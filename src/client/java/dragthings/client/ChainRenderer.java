package dragthings.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a rope/chain visual connecting:
 *   player hand → dragged leader → follower[0] → follower[1] → ...
 *
 * Each segment is a quad-billboard ribbon (same technique as ItemTrailRenderer)
 * with a catenary sag applied — the midpoint of each segment droops downward
 * proportional to the segment length, giving a natural rope-like appearance.
 *
 * The ribbon tapers slightly and fades toward the far end, and has a subtle
 * brightness "twist" running along its length so it reads as a woven rope
 * rather than a flat ribbon.
 *
 * Anchor positions (player, leader, followers) are recomputed EVERY render
 * frame using partial-tick interpolation, not cached once per game tick —
 * previously the chain only moved 20 times/sec regardless of framerate,
 * which looked stepped/laggy compared to the smoothly-interpolated items
 * themselves.
 */
public final class ChainRenderer {

    // ── Segment resolution ────────────────────────────────────────────────
    /** Number of subdivisions per chain segment. More = smoother sag curve. */
    private static final int SUBDIVISIONS = 12;

    // ── Hand anchor offset ───────────────────────────────────────────────
    // The rope now anchors near where a held item would actually be (down
    // and to the side, slightly forward) instead of shooting straight out
    // from the player's eyes.
    private static final double HAND_FORWARD = 0.30;
    private static final double HAND_RIGHT   = 0.30;
    private static final double HAND_DOWN    = 0.55;

    // Rope "twist" look: brightness ripples along the length of the chain.
    private static final float TWIST_FREQUENCY = 5.5f;
    private static final float TWIST_STRENGTH  = 0.12f;

    // ── State ─────────────────────────────────────────────────────────────
    // We keep entity REFERENCES (not snapshot positions) so onRender() can
    // pull fresh, interpolated positions every single frame.
    private static Entity            leaderRef;
    private static final List<Entity> followerRefs = new ArrayList<>();
    private static float                 fadeAlpha = 0f;
    private static boolean               active    = false;
    private static float                 twistTime = 0f;

    // Reused scratch buffers — one segment's worth of subdivided points and
    // their billboard "right" vectors. Previously these were reallocated
    // per-segment, per-frame; now they're overwritten in place.
    private static final Vec3[] scratchPts    = new Vec3[SUBDIVISIONS + 1];
    private static final Vec3[] scratchRights = new Vec3[SUBDIVISIONS + 1];

    private ChainRenderer() {}

    // ── Public API ────────────────────────────────────────────────────────

    public static void init() {
        WorldRenderEvents.LAST.register(ChainRenderer::onRender);
    }

    /**
     * Call every tick while dragging. Only stores entity references and
     * bumps the fade-in — actual positions are computed fresh each render
     * frame in onRender() for smooth partial-tick motion.
     */
    public static void update(Entity leader, List<? extends Entity> followers) {
        active    = true;
        leaderRef = leader;
        followerRefs.clear();
        followerRefs.addAll(followers);

        DragThingsConfig.ChainConfig cfg = DragThingsConfig.get().chain;
        float speed = cfg.getFadeSpeed();
        fadeAlpha = Math.min(1f, fadeAlpha + speed);
    }

    /** Call on release — chain fades out over subsequent frames. */
    public static void stop() {
        active = false;
    }

    /** Call on disconnect / world change — instant clear. */
    public static void clearAll() {
        active = false;
        leaderRef = null;
        followerRefs.clear();
        fadeAlpha = 0f;
    }

    // ── Render ────────────────────────────────────────────────────────────

    private static void onRender(WorldRenderContext ctx) {
        DragThingsConfig.ChainConfig cfg = DragThingsConfig.get().chain;
        if (!cfg.enableChain) {
            if (fadeAlpha > 0f) fadeAlpha = 0f;
            leaderRef = null;
            followerRefs.clear();
            return;
        }

        // Fade out when not active
        if (!active) {
            fadeAlpha -= cfg.getFadeSpeed();
            if (fadeAlpha <= 0f) {
                fadeAlpha = 0f;
                leaderRef = null;
                followerRefs.clear();
                return;
            }
        }
        if (leaderRef == null || !leaderRef.isAlive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(true);
        twistTime += 0.02f;

        // ── Build fresh, interpolated anchor list for THIS frame ─────────
        List<Vec3> anchors = buildInterpolatedAnchors(mc.player, partial);
        if (anchors.size() < 2) return;

        Vec3  cam    = mc.gameRenderer.getMainCamera().getPosition();
        Vec3  camFwd = Vec3.directionFromRotation(
                mc.gameRenderer.getMainCamera().getXRot(),
                mc.gameRenderer.getMainCamera().getYRot());

        float halfW     = cfg.getHalfWidth();
        float headAlpha = cfg.getAlpha() * fadeAlpha;
        float CR = cfg.getColorR();
        float CG = cfg.getColorG();
        float CB = cfg.getColorB();
        float sag = cfg.getSag();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        PoseStack pose = ctx.matrixStack();
        pose.pushPose();
        Matrix4f mat = pose.last().pose();

        Tesselator    tess        = Tesselator.getInstance();
        BufferBuilder buf         = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean       hasVertices = false;

        int segCount = anchors.size() - 1;

        for (int seg = 0; seg < segCount; seg++) {
            Vec3 pA = anchors.get(seg);
            Vec3 pB = anchors.get(seg + 1);

            // Global t along the whole chain: 0 = player hand, 1 = last follower
            float globalTA = (float) seg       / segCount;
            float globalTB = (float)(seg + 1)  / segCount;

            // Catenary sag: midpoint droops by sag * segment_length
            double segLen   = pA.distanceTo(pB);
            double sagDepth = sag * segLen;

            // Fill the reused scratch buffer with this segment's subdivided,
            // sagged points (no per-frame array allocation).
            for (int i = 0; i <= SUBDIVISIONS; i++) {
                float u = (float) i / SUBDIVISIONS;
                Vec3 base = BillboardMath.lerp(pA, pB, u);
                double drop = sagDepth * 4.0 * u * (1.0 - u);
                scratchPts[i] = base.add(0, -drop, 0);
            }

            // Pre-compute miter right vectors (same gap-free technique as ItemTrailRenderer)
            for (int i = 0; i <= SUBDIVISIONS; i++) {
                Vec3 segBefore = (i > 0)             ? scratchPts[i].subtract(scratchPts[i - 1]).normalize() : null;
                Vec3 segAfter  = (i < SUBDIVISIONS)  ? scratchPts[i + 1].subtract(scratchPts[i]).normalize() : null;
                Vec3 avgSeg;
                if (segBefore != null && segAfter != null) {
                    Vec3 sum = segBefore.add(segAfter);
                    avgSeg = sum.lengthSqr() > 1e-10 ? sum.normalize() : segAfter;
                } else {
                    avgSeg = segAfter != null ? segAfter : segBefore;
                }
                scratchRights[i] = BillboardMath.billboardRight(camFwd, avgSeg);
            }

            // Emit quads
            for (int i = 0; i < SUBDIVISIONS; i++) {
                Vec3 a = scratchPts[i];
                Vec3 b = scratchPts[i + 1];

                // Local t within this sub-segment, mapped to global chain t
                float tA = globalTA + (globalTB - globalTA) * ((float) i       / SUBDIVISIONS);
                float tB = globalTA + (globalTB - globalTA) * ((float)(i + 1)  / SUBDIVISIONS);

                // Alpha: full at player end (t=0), fades slightly toward last follower
                float aA = (1f - tA * 0.35f) * headAlpha;
                float aB = (1f - tB * 0.35f) * headAlpha;

                // Width tapers toward the follower end
                float hwA = halfW * (1f - tA * 0.4f);
                float hwB = halfW * (1f - tB * 0.4f);

                // Woven-rope look: brightness ripples along the chain length.
                float twistA = 1f + TWIST_STRENGTH * (float) Math.sin(tA * TWIST_FREQUENCY * (float) Math.PI + twistTime);
                float twistB = 1f + TWIST_STRENGTH * (float) Math.sin(tB * TWIST_FREQUENCY * (float) Math.PI + twistTime);

                // Color: slightly darker/warmer toward follower end, modulated by twist
                float rA = clamp01((CR + (1f - CR) * tA * 0.15f) * twistA);
                float gA = clamp01((CG * (1f - tA * 0.1f)) * twistA);
                float rB = clamp01((CR + (1f - CR) * tB * 0.15f) * twistB);
                float gB = clamp01((CG * (1f - tB * 0.1f)) * twistB);
                float bA = clamp01(CB * twistA);
                float bB = clamp01(CB * twistB);

                Vec3 offsetA = scratchRights[i].scale(hwA);
                Vec3 offsetB = scratchRights[i + 1].scale(hwB);

                float ax0 = (float)(a.x - cam.x - offsetA.x);
                float ay0 = (float)(a.y - cam.y - offsetA.y);
                float az0 = (float)(a.z - cam.z - offsetA.z);
                float ax1 = (float)(a.x - cam.x + offsetA.x);
                float ay1 = (float)(a.y - cam.y + offsetA.y);
                float az1 = (float)(a.z - cam.z + offsetA.z);
                float bx0 = (float)(b.x - cam.x - offsetB.x);
                float by0 = (float)(b.y - cam.y - offsetB.y);
                float bz0 = (float)(b.z - cam.z - offsetB.z);
                float bx1 = (float)(b.x - cam.x + offsetB.x);
                float by1 = (float)(b.y - cam.y + offsetB.y);
                float bz1 = (float)(b.z - cam.z + offsetB.z);

                buf.addVertex(mat, ax0, ay0, az0).setColor(rA, gA, bA, aA);
                buf.addVertex(mat, ax1, ay1, az1).setColor(rA, gA, bA, aA);
                buf.addVertex(mat, bx1, by1, bz1).setColor(rB, gB, bB, aB);
                buf.addVertex(mat, bx0, by0, bz0).setColor(rB, gB, bB, aB);
                hasVertices = true;
            }
        }

        if (hasVertices) {
            BufferUploader.drawWithShader(buf.buildOrThrow());
        }

        pose.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Builds this frame's anchor list: [0]=hand, [1]=leader, [2..n]=followers,
     * with every position interpolated for the current partial tick.
     */
    private static List<Vec3> buildInterpolatedAnchors(LocalPlayer player, float partial) {
        List<Vec3> out = new ArrayList<>(2 + followerRefs.size());
        out.add(handAnchor(player, partial));
        out.add(interpolatedItemPos(leaderRef, partial).add(0, 0.15, 0));
        for (Entity f : followerRefs) {
            if (f == null || !f.isAlive()) continue;
            out.add(interpolatedItemPos(f, partial).add(0, 0.15, 0));
        }
        return out;
    }

    /** Interpolated item position using the same xo/yo/zo pattern as ItemTrailRenderer. */
    private static Vec3 interpolatedItemPos(Entity item, float partial) {
        return new Vec3(
                item.xo + (item.getX() - item.xo) * partial,
                item.yo + (item.getY() - item.yo) * partial,
                item.zo + (item.getZ() - item.zo) * partial);
    }

    /**
     * Anchor near where a held item would actually sit — down and to the
     * side of the eyes, slightly forward — instead of straight out from the
     * player's face.
     */
    private static Vec3 handAnchor(LocalPlayer player, float partial) {
        Vec3  eye   = player.getEyePosition(partial);
        float pitch = player.getViewXRot(partial);
        float yaw   = player.getViewYRot(partial);

        Vec3 look  = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = Vec3.directionFromRotation(0f, yaw - 90f);

        return eye
                .add(look.scale(HAND_FORWARD))
                .add(right.scale(HAND_RIGHT))
                .subtract(0, HAND_DOWN, 0);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}