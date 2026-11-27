package dragthings.client.arm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dragthings.client.DragThingsConfig;
import dragthings.mobdrag.MobDragHandler;
import dragthings.network.ArmGrabPayload;
import dragthings.network.ArmStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Core of the stretched-arm feature: networking, per-player animation state,
 * where the shoulders and hands are, and dispatch to an {@link ArmStyle}.
 * It does not know how an arm is actually drawn; that is the style's job:
 *
 *  - {@link RigidArmStyle}   one straight, stiff arm (the original look)
 *  - {@link BezierArmStyle}  a curved, multi-segment arm with a spring
 *
 * Pick one with {@link #setMode}. One arm (right) is used for items, two arms
 * for mobs. Everything is drawn in WORLD space after entities, so one code
 * path covers first person, third person, and other players seeing you.
 *
 * Wiring:
 *  - call {@link #init()} once from the client initializer
 *  - ItemDragHandler / MobDragHandler call {@link #onLocalGrab} / {@link #onLocalRelease}
 *  - ItemInHandRendererMixin / PlayerRendererMixin hide the vanilla arms
 */
public final class DragArmRenderer {

    private DragArmRenderer() {}

    // ── Style selection ───────────────────────────────────────────────────
    public enum ArmMode { RIGID, BEZIER }

    // TODO: drive this from the client config file once that exists.
    public static ArmMode getMode() {
        ArmMode m = DragThingsConfig.get().arm.mode;   // đọc từ Cloth Config
        return m == null ? ArmMode.BEZIER : m;
    }
    public static void setMode(ArmMode m) {
        DragThingsConfig.get().arm.mode = (m == null) ? ArmMode.BEZIER : m;
    }

    /** Implemented by each way of drawing an arm. */
    interface ArmStyle {
        void draw(DrawCtx ctx);
    }

    /**
     * Everything a style needs to draw one arm. {@code shoulder} and
     * {@code tip} are world coordinates; {@code tip} is already blended
     * between the rest pose and the grab point by the extend animation, and
     * is at least MIN_DISTANCE away from the shoulder.
     */
    record DrawCtx(PoseStack poseStack, VertexConsumer vc, Minecraft mc, ClientLevel level,
                   AbstractClientPlayer player, Vec3 camPos, boolean firstPerson,
                   Limb limb, boolean leftArm, boolean slim,
                   Vec3 shoulder, Vec3 tip, Vec3 bodyForward, Vec3 bodyLeft,
                   float dt, int fallbackLight) {}

    // ── Shared tuning ─────────────────────────────────────────────────────
    /** Natural arm length (12 px). Styles use it for stretching and bending. */
    static final float ARM_LEN = 12f / 16f;
    static final double MIN_DISTANCE = 0.05;

    /** Seconds for the arm to fully extend or retract. */
    private static final float EXTEND_SECONDS = 0.20f;
    private static final double MAX_RENDER_DIST_SQ = 64.0 * 64.0;
    /** Item models sit a bit above the entity origin. */
    private static final double ITEM_GRAB_Y = 0.15;
    /** Where on a mob the hands grab, as a fraction of its height. */
    private static final double MOB_GRAB_HEIGHT = 0.55;
    /** Hand separation on a mob: fraction of its width, clamped. */
    private static final double MOB_SEP_FRACTION = 0.40;
    private static final double MOB_SEP_MIN = 0.12;
    private static final double MOB_SEP_MAX = 0.45;
    /** Per-hand smoothing rates (1/s). Different, so the hands move independently. */
    private static final float HAND_RATE_RIGHT = 26f;
    private static final float HAND_RATE_LEFT = 20f;
    /** Keep drawing for this long if the target entity is not known yet. */
    private static final double TARGET_GRACE_SECONDS = 0.5;

    // First-person shoulder offset from the camera (blocks).
    private static final float FP_FORWARD = 0.35f;
    private static final float FP_SIDE = 0.30f;
    private static final float FP_DOWN = 0.45f;

    // Third-person / other players: shoulder relative to the feet.
    private static final double SHOULDER_HEIGHT = 1.376;
    private static final double SHOULDER_HEIGHT_SNEAK = 1.15;
    private static final double SHOULDER_SIDE = 0.31;

    // ── State ─────────────────────────────────────────────────────────────
    /** Per-arm state. The spring fields are owned by BezierArmStyle. */
    static final class Limb {
        Vec3 grab = Vec3.ZERO;
        boolean hasGrab;

        Vec3 ctrl = Vec3.ZERO;
        Vec3 vel = Vec3.ZERO;
        boolean springInit;

        void reset() {
            hasGrab = false;
            springInit = false;
            vel = Vec3.ZERO;
        }
    }

    private static final class Anim {
        int targetId = -1;
        boolean grabbing;
        boolean twoHanded;
        float extend;           // 0 = arms at rest, 1 = fully reaching
        double missing;         // seconds the target entity has been unknown
        final Limb right = new Limb();
        final Limb left = new Limb();
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

    // ── Hooks called by the drag handlers ─────────────────────────────────
    /** Start reaching for {@code target} (an item entity, or a mob). */
    public static void onLocalGrab(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        apply(mc.player.getId(), target.getId());
        if (ClientPlayNetworking.canSend(ArmGrabPayload.TYPE)) {
            ClientPlayNetworking.send(new ArmGrabPayload(target.getId()));
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

    private static boolean isTwoHanded(Entity target) {
        return !(target instanceof ItemEntity);
    }

    private static void apply(int playerId, int targetId) {
        Anim a = ANIMS.computeIfAbsent(playerId, k -> new Anim());
        if (targetId >= 0) {
            a.targetId = targetId;
            a.grabbing = true;
            a.missing = 0;
            a.right.reset();
            a.left.reset();
            Minecraft mc = Minecraft.getInstance();
            Entity t = mc.level != null ? mc.level.getEntity(targetId) : null;
            if (t != null) a.twoHanded = isTwoHanded(t);
        } else {
            a.grabbing = false;
        }
    }

    // ── Queries used by the mixins ────────────────────────────────────────
    /** True while this player's vanilla RIGHT arm should be hidden. */
    public static boolean shouldReplaceArm(AbstractClientPlayer player) {
        Anim a = ANIMS.get(player.getId());
        return a != null && a.extend > 0.02f && poseAllowsArm(player);
    }

    /** True while this player's vanilla LEFT arm should be hidden. */
    public static boolean shouldReplaceLeftArm(AbstractClientPlayer player) {
        Anim a = ANIMS.get(player.getId());
        return a != null && a.twoHanded && a.extend > 0.02f && poseAllowsArm(player);
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
        float dt = lastNanos == 0L ? 0f : Math.min((now - lastNanos) / 1_000_000_000f, 0.05f);
        lastNanos = now;

        float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        Camera camera = ctx.camera();
        Vec3 camPos = camera.getPosition();
        Frustum frustum = ctx.frustum();

        Iterator<Map.Entry<Integer, Anim>> it = ANIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Anim> entry = it.next();
            Anim a = entry.getValue();

            Entity pe = level.getEntity(entry.getKey());
            if (!(pe instanceof AbstractClientPlayer player)) {
                it.remove(); // player left, died, or changed dimension
                continue;
            }

            Entity target = null;
            if (a.grabbing) {
                Entity found = level.getEntity(a.targetId);
                if (found != null && found.isAlive() && !found.isRemoved()) {
                    target = found;
                    a.missing = 0;
                    a.twoHanded = isTwoHanded(found);
                } else {
                    a.missing += dt;
                    if (a.missing > TARGET_GRACE_SECONDS) a.grabbing = false;
                }
            }

            a.extend = Mth.clamp(a.extend + (a.grabbing ? dt : -dt) / EXTEND_SECONDS, 0f, 1f);

            if (target != null) updateAnchors(a, player, target, pt, dt);

            if (!a.grabbing && a.extend <= 0f) {
                it.remove();
                continue;
            }
            if (!a.right.hasGrab || !poseAllowsArm(player)) continue;

            boolean firstPerson = player == mc.player && !camera.isDetached();
            if (!firstPerson && player.distanceToSqr(camPos) > MAX_RENDER_DIST_SQ) continue;

            drawPlayerArms(poseStack, consumers, mc, level, player, camera, camPos,
                    frustum, pt, dt, firstPerson, a);
        }
    }

    /** Computes where each hand should be, with per-hand smoothing. */
    private static void updateAnchors(Anim a, AbstractClientPlayer player, Entity target, float pt, float dt) {
        double tx = Mth.lerp(pt, target.xo, target.getX());
        double ty = Mth.lerp(pt, target.yo, target.getY());
        double tz = Mth.lerp(pt, target.zo, target.getZ());

        Vec3 rightT;
        Vec3 leftT;
        boolean direct;

        if (target instanceof ItemEntity) {
            rightT = new Vec3(tx, ty + ITEM_GRAB_Y, tz);
            leftT = rightT;
            direct = true;
        } else {
            float scale = mobScale(target);
            double h = target.getBbHeight() * scale;
            double w = target.getBbWidth() * scale;
            double sep = Mth.clamp(w * MOB_SEP_FRACTION, MOB_SEP_MIN, MOB_SEP_MAX);
            Vec3 center = new Vec3(tx, ty + h * MOB_GRAB_HEIGHT, tz);

            // Lateral axis from the dragger's view direction, not the mob's
            // orientation, so the hands stay on their own sides and never cross.
            float yaw = player.getViewYRot(pt) * Mth.DEG_TO_RAD;
            Vec3 lateralLeft = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            rightT = center.subtract(lateralLeft.scale(sep));
            leftT = center.add(lateralLeft.scale(sep));
            direct = false;
        }

        followAnchor(a.right, rightT, direct, HAND_RATE_RIGHT, dt);
        followAnchor(a.left, leftT, direct, HAND_RATE_LEFT, dt);
    }

    private static void followAnchor(Limb l, Vec3 target, boolean direct, float rate, float dt) {
        if (!l.hasGrab || direct) {
            l.grab = target;
        } else {
            l.grab = l.grab.lerp(target, 1.0 - Math.exp(-rate * dt));
        }
        l.hasGrab = true;
    }

    /** Same formula as MobDragScaleMixin, so the hands follow the mob's visual size. */
    private static float mobScale(Entity target) {
        if (target instanceof LivingEntity le) {
            float progress = MobDragHandler.getShrinkProgress(le);
            if (progress <= 0.001f) return 1f;
            float t = progress * progress * (3f - 2f * progress);
            float minScale = DragThingsConfig.get().mobDrag.getMinScale();
            return 1f + (minScale - 1f) * t;
        }
        return 1f;
    }

    private static void drawPlayerArms(PoseStack poseStack, MultiBufferSource consumers,
                                       Minecraft mc, ClientLevel level, AbstractClientPlayer player,
                                       Camera camera, Vec3 camPos, Frustum frustum, float pt, float dt,
                                       boolean firstPerson, Anim a) {
        float bodyYaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 bodyForward = new Vec3(-Math.sin(bodyYaw), 0, Math.cos(bodyYaw));
        Vec3 bodyLeft = new Vec3(Math.cos(bodyYaw), 0, Math.sin(bodyYaw));

        float e = a.grabbing ? easeOutBack(a.extend) : smooth(a.extend);

        ResourceLocation texture = player.getSkin().texture();
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        VertexConsumer vc = consumers.getBuffer(RenderType.entityCutoutNoCull(texture));
        int fallbackLight = mc.getEntityRenderDispatcher().getPackedLightCoords(player, pt);

        ArmStyle style = (getMode() == ArmMode.RIGID) ? RigidArmStyle.INSTANCE : BezierArmStyle.INSTANCE;
        
        dispatch(style, poseStack, vc, mc, level, player, camera, camPos, frustum, firstPerson,
                a.right, false, slim, bodyForward, bodyLeft, e, pt, dt, fallbackLight);
        if (a.twoHanded && a.left.hasGrab) {
            dispatch(style, poseStack, vc, mc, level, player, camera, camPos, frustum, firstPerson,
                    a.left, true, slim, bodyForward, bodyLeft, e, pt, dt, fallbackLight);
        }
    }

    private static void dispatch(ArmStyle style, PoseStack poseStack, VertexConsumer vc, Minecraft mc,
                                 ClientLevel level, AbstractClientPlayer player, Camera camera, Vec3 camPos,
                                 Frustum frustum, boolean firstPerson, Limb limb, boolean leftArm,
                                 boolean slim, Vec3 bodyForward, Vec3 bodyLeft, float e, float pt, float dt,
                                 int fallbackLight) {
        Vec3 shoulder = shoulderPos(player, camera, pt, firstPerson, bodyLeft, leftArm);

        // Rest pose: arm hanging down (third person) or resting forward-down (first person).
        Vec3 restDir = firstPerson ? lookDown(camera) : new Vec3(0, -1, 0);
        Vec3 rest = shoulder.add(restDir.scale(ARM_LEN));

        // Blend from rest to the grab point, so there is no pop when the
        // vanilla arm is swapped in and out.
        Vec3 tip = rest.lerp(limb.grab, e);
        if (tip.subtract(shoulder).length() < MIN_DISTANCE) return;

        if (!firstPerson && frustum != null) {
            AABB box = new AABB(shoulder, tip).minmax(new AABB(shoulder, limb.grab)).inflate(0.6);
            if (!frustum.isVisible(box)) return;
        }

        style.draw(new DrawCtx(poseStack, vc, mc, level, player, camPos, firstPerson, limb,
                leftArm, slim, shoulder, tip, bodyForward, bodyLeft, dt, fallbackLight));
    }

    private static Vec3 lookDown(Camera camera) {
        Vector3f look = camera.getLookVector();
        return new Vec3(look.x * 0.6, look.y * 0.6 - 0.8, look.z * 0.6).normalize();
    }

    /** Light at a point, falling back to the player's own light inside solid blocks. */
    static int lightAt(ClientLevel level, Vec3 p, int fallback) {
        int l = LevelRenderer.getLightColor(level, BlockPos.containing(p));
        return (LightTexture.block(l) == 0 && LightTexture.sky(l) == 0) ? fallback : l;
    }

    private static Vec3 shoulderPos(AbstractClientPlayer p, Camera camera, float pt,
                                    boolean firstPerson, Vec3 bodyLeft, boolean leftArm) {
        if (firstPerson) {
            Vec3 c = camera.getPosition();
            Vector3f look = camera.getLookVector();
            Vector3f left = camera.getLeftVector();
            Vector3f up = camera.getUpVector();
            float side = leftArm ? FP_SIDE : -FP_SIDE; // camera "left" vector points left
            return new Vec3(
                    c.x + look.x * FP_FORWARD + left.x * side - up.x * FP_DOWN,
                    c.y + look.y * FP_FORWARD + left.y * side - up.y * FP_DOWN,
                    c.z + look.z * FP_FORWARD + left.z * side - up.z * FP_DOWN);
        }
        double x = Mth.lerp(pt, p.xo, p.getX());
        double y = Mth.lerp(pt, p.yo, p.getY());
        double z = Mth.lerp(pt, p.zo, p.getZ());
        double h = p.isCrouching() ? SHOULDER_HEIGHT_SNEAK : SHOULDER_HEIGHT;
        double s = leftArm ? SHOULDER_SIDE : -SHOULDER_SIDE;
        return new Vec3(x + bodyLeft.x * s, y + h, z + bodyLeft.z * s);
    }

    // ── Easing ────────────────────────────────────────────────────────────
    private static float smooth(float x) {
        return x * x * (3f - 2f * x);
    }

    /** Overshoots to ~1.07 before settling at 1. */
    private static float easeOutBack(float x) {
        float c1 = 1.2f;
        float c3 = c1 + 1f;
        float t = x - 1f;
        return 1f + c3 * t * t * t + c1 * t * t;
    }
}