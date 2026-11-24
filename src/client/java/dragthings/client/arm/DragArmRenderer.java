package dragthings.client.arm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dragthings.network.ArmGrabPayload;
import dragthings.network.ArmStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Draws a stretched right arm from a player's shoulder to the item they are
 * dragging, like the grab arm in "A Dusty Trip".
 *
 * The arm is drawn in WORLD space after entities, using the player's own
 * skin and the real PlayerModel arm cuboid, scaled along its length. Because
 * it is world-space, one code path handles first person, third person, and
 * other players seeing you.
 *
 * Wiring:
 *  - call {@link #init()} once from the client initializer
 *  - ItemDragHandler calls {@link #onLocalGrab} / {@link #onLocalRelease}
 *  - ItemInHandRendererMixin / PlayerRendererMixin hide the vanilla arm
 */
public final class DragArmRenderer {

    private DragArmRenderer() {}

    // ── Tuning ────────────────────────────────────────────────────────────
    /** Seconds for the arm to fully extend or retract. */
    private static final float EXTEND_SECONDS = 0.18f;
    /** Vanilla arm cuboid: 12 px tall, pivot 2 px below its top edge. */
    private static final float ARM_LENGTH = 12f / 16f;
    private static final float ARM_TOP_OFFSET = 2f / 16f;
    /** Never squash the arm shorter than this fraction of its natural length. */
    private static final float MIN_SCALE = 0.4f;
    /** Do not draw when shoulder and grab point are basically the same spot. */
    private static final double MIN_DISTANCE = 0.05;
    /** The item model sits a bit above the entity origin. */
    private static final double GRAB_Y_OFFSET = 0.15;

    // First-person shoulder offset from the camera (blocks).
    private static final float FP_FORWARD = 0.35f;
    private static final float FP_RIGHT = 0.30f;
    private static final float FP_DOWN = 0.42f;

    // Third-person / other players: right shoulder relative to the feet.
    private static final double SHOULDER_HEIGHT = 1.376;
    private static final double SHOULDER_HEIGHT_SNEAK = 1.15;
    private static final double SHOULDER_SIDE = 0.31;

    private static final float ARM_THICKNESS = 0.8f;

    // ── State ─────────────────────────────────────────────────────────────
    private static final class Anim {
        int itemId = -1;
        boolean grabbing;
        float extend;          // 0 = arm at rest, 1 = fully reaching
        boolean hasGrab;
        Vec3 lastGrab = Vec3.ZERO; // last known grab point, used while retracting
    }

    /** player entity id -> animation state. */
    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    private static long lastNanos = 0L;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ArmStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> apply(payload.playerId(), payload.itemId())));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ANIMS.clear();
            lastNanos = 0L;
        });

        WorldRenderEvents.AFTER_ENTITIES.register(DragArmRenderer::render);
    }

    // ── Hooks called by ItemDragHandler ───────────────────────────────────
    public static void onLocalGrab(ItemEntity item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        apply(mc.player.getId(), item.getId());
        if (ClientPlayNetworking.canSend(ArmGrabPayload.TYPE)) {
            ClientPlayNetworking.send(new ArmGrabPayload(item.getId()));
        }
    }

    public static void onLocalRelease() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        apply(mc.player.getId(), -1);
        if (ClientPlayNetworking.canSend(ArmGrabPayload.TYPE)) {
            ClientPlayNetworking.send(new ArmGrabPayload(-1));
        }
    }

    private static void apply(int playerId, int itemId) {
        Anim a = ANIMS.computeIfAbsent(playerId, k -> new Anim());
        if (itemId >= 0) {
            a.itemId = itemId;
            a.grabbing = true;
            a.hasGrab = false; // drop any stale point from a previous grab
        } else {
            a.grabbing = false;
        }
    }

    // ── Queries used by the mixins ────────────────────────────────────────
    /** True while this player's vanilla right arm should be hidden. */
    public static boolean shouldReplaceArm(AbstractClientPlayer player) {
        Anim a = ANIMS.get(player.getId());
        return a != null && a.extend > 0.02f && poseAllowsArm(player);
    }

    /** True while the local first-person hand layer should be skipped. */
    public static boolean shouldHideFirstPersonHands() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer.getMainCamera().isDetached()) return false;
        return shouldReplaceArm(mc.player);
    }

    private static boolean poseAllowsArm(AbstractClientPlayer p) {
        Pose pose = p.getPose();
        return pose == Pose.STANDING || pose == Pose.CROUCHING;
    }

    // ── Rendering ─────────────────────────────────────────────────────────
    private static void render(WorldRenderContext ctx) {
        if (ANIMS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = ctx.world();
        PoseStack poseStack = ctx.matrixStack();
        MultiBufferSource consumers = ctx.consumers();
        if (level == null || poseStack == null || consumers == null) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min((now - lastNanos) / 1_000_000_000f, 0.1f);
        lastNanos = now;

        float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        Camera camera = ctx.camera();
        Vec3 camPos = camera.getPosition();

        Iterator<Map.Entry<Integer, Anim>> it = ANIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Anim> entry = it.next();
            Anim a = entry.getValue();

            Entity pe = level.getEntity(entry.getKey());
            if (!(pe instanceof AbstractClientPlayer player)) {
                it.remove(); // player left, died, or changed dimension
                continue;
            }

            ItemEntity item = null;
            if (a.grabbing) {
                Entity ie = level.getEntity(a.itemId);
                if (ie instanceof ItemEntity found && found.isAlive()) item = found;
                else a.grabbing = false; // item was picked up, placed, or unloaded
            }

            a.extend = Mth.clamp(a.extend + (a.grabbing ? dt : -dt) / EXTEND_SECONDS, 0f, 1f);

            if (item != null) {
                a.lastGrab = new Vec3(
                        Mth.lerp(pt, item.xo, item.getX()),
                        Mth.lerp(pt, item.yo, item.getY()) + GRAB_Y_OFFSET,
                        Mth.lerp(pt, item.zo, item.getZ()));
                a.hasGrab = true;
            }

            if (!a.grabbing && a.extend <= 0f) {
                it.remove();
                continue;
            }
            if (!a.hasGrab || !poseAllowsArm(player)) continue;

            boolean firstPerson = player == mc.player && !camera.isDetached();
            drawArm(poseStack, consumers, mc, player, camera, camPos, pt, firstPerson, a);
        }
    }

    private static void drawArm(PoseStack poseStack, MultiBufferSource consumers, Minecraft mc,
                                AbstractClientPlayer player, Camera camera, Vec3 camPos,
                                float pt, boolean firstPerson, Anim a) {
        EntityRenderer<? super AbstractClientPlayer> er = mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(er instanceof PlayerRenderer renderer)) return;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();

        float bodyYawRad = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 shoulder = shoulderPos(player, camera, pt, firstPerson, bodyYawRad);

        Vec3 toGrab = a.lastGrab.subtract(shoulder);
        double fullDist = toGrab.length();
        if (fullDist < MIN_DISTANCE) return;

        float e = a.extend * a.extend * (3f - 2f * a.extend); // smoothstep
        double length = fullDist * e;
        if (length < MIN_DISTANCE) return;

        Vec3 dir = toGrab.scale(1.0 / fullDist);
        Vec3 bodyForward = new Vec3(-Math.sin(bodyYawRad), 0, Math.cos(bodyYawRad));
        Quaternionf rotation = basis(dir, bodyForward);
        float scaleY = Math.max((float) (length / ARM_LENGTH), MIN_SCALE);

        ResourceLocation texture = player.getSkin().texture();
        VertexConsumer vc = consumers.getBuffer(RenderType.entityCutoutNoCull(texture));
        int light = renderer.getPackedLightCoords(player, pt);

        poseStack.pushPose();
        poseStack.translate(shoulder.x - camPos.x, shoulder.y - camPos.y, shoulder.z - camPos.z);
        poseStack.mulPose(rotation);
        poseStack.scale(ARM_THICKNESS, scaleY, ARM_THICKNESS);
        // Shift the cuboid so its top edge sits exactly on the shoulder and
        // the hand end lands on the grab point after scaling.
        poseStack.translate(0f, ARM_TOP_OFFSET, 0f);

        renderPart(model.rightArm, poseStack, vc, light);
        if (player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE)) {
            renderPart(model.rightSleeve, poseStack, vc, light);
        }
        poseStack.popPose();
    }

    /**
     * Renders one model part with its pose zeroed (so only OUR transform
     * applies) and forced visible (the PlayerRendererMixin hides the vanilla
     * arm on the shared model). Both are restored afterwards.
     */
    private static void renderPart(ModelPart part, PoseStack poseStack, VertexConsumer vc, int light) {
        PartPose saved = part.storePose();
        boolean wasVisible = part.visible;
        try {
            part.loadPose(PartPose.ZERO);
            part.visible = true;
            part.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY);
        } finally {
            part.loadPose(saved);
            part.visible = wasVisible;
        }
    }

    private static Vec3 shoulderPos(AbstractClientPlayer p, Camera camera, float pt,
                                    boolean firstPerson, float bodyYawRad) {
        if (firstPerson) {
            Vec3 c = camera.getPosition();
            Vector3f look = camera.getLookVector();
            Vector3f left = camera.getLeftVector();
            Vector3f up = camera.getUpVector();
            return new Vec3(
                    c.x + look.x * FP_FORWARD - left.x * FP_RIGHT - up.x * FP_DOWN,
                    c.y + look.y * FP_FORWARD - left.y * FP_RIGHT - up.y * FP_DOWN,
                    c.z + look.z * FP_FORWARD - left.z * FP_RIGHT - up.z * FP_DOWN);
        }
        double x = Mth.lerp(pt, p.xo, p.getX());
        double y = Mth.lerp(pt, p.yo, p.getY());
        double z = Mth.lerp(pt, p.zo, p.getZ());
        double rightX = -Math.cos(bodyYawRad);
        double rightZ = -Math.sin(bodyYawRad);
        double h = p.isCrouching() ? SHOULDER_HEIGHT_SNEAK : SHOULDER_HEIGHT;
        return new Vec3(x + rightX * SHOULDER_SIDE, y + h, z + rightZ * SHOULDER_SIDE);
    }

    /**
     * Rotation that sends the model's +Y axis (down the arm) to {@code dir}.
     * The twist is chosen so the arm's front face turns upward, which is
     * what a natural forward reach looks like. Falls back to the body's
     * forward direction as the reference when the arm points straight up or
     * down, where "up" would be degenerate.
     */
    private static Quaternionf basis(Vec3 dir, Vec3 bodyForward) {
        Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        Vector3f ref = new Vector3f(0f, 1f, 0f);

        Vector3f z = new Vector3f(d).mul(d.dot(ref)).sub(ref); // d*(d.ref) - ref
        if (z.lengthSquared() < 1e-4f) {
            ref.set((float) bodyForward.x, (float) bodyForward.y, (float) bodyForward.z);
            z.set(d).mul(d.dot(ref)).sub(ref);
        }
        z.normalize();
        Vector3f x = new Vector3f(d).cross(z); // d x z, already unit length

        return new Quaternionf().setFromNormalized(new Matrix3f(x, d, z));
    }
}