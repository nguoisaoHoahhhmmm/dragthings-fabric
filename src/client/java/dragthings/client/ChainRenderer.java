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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.item.ItemEntity;
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
 * The ribbon tapers slightly and fades toward the far end so the chain looks
 * anchored at the player hand and free at the last follower.
 */
public final class ChainRenderer {

    // ── Segment resolution ────────────────────────────────────────────────
    /** Number of subdivisions per chain segment. More = smoother sag curve. */
    private static final int SUBDIVISIONS = 12;

    // ── State ─────────────────────────────────────────────────────────────
    /** Ordered anchor points: [0]=player hand, [1]=leader, [2…n]=followers */
    private static final List<Vec3> anchors = new ArrayList<>();
    private static float            fadeAlpha = 0f;
    private static boolean          active    = false;

    private ChainRenderer() {}

    // ── Public API ────────────────────────────────────────────────────────

    public static void init() {
        WorldRenderEvents.LAST.register(ChainRenderer::onRender);
    }

    /**
     * Call every tick while dragging to update anchor positions.
     * @param playerHand  approximate hand/eye position of the player
     * @param leader      the primary dragged item
     * @param followers   ordered chain followers
     */
    public static void update(Vec3 playerHand, ItemEntity leader, List<ItemEntity> followers) {
        active = true;
        anchors.clear();
        anchors.add(playerHand);
        anchors.add(leader.position().add(0, 0.15, 0)); // slightly above item center
        for (ItemEntity f : followers)
            anchors.add(f.position().add(0, 0.15, 0));

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
        anchors.clear();
        fadeAlpha = 0f;
    }

    // ── Render ────────────────────────────────────────────────────────────

    private static void onRender(WorldRenderContext ctx) {
        DragThingsConfig.ChainConfig cfg = DragThingsConfig.get().chain;
        if (!cfg.enableChain) {
            if (fadeAlpha > 0f) fadeAlpha = 0f;
            anchors.clear(); // prevent stale anchors rendering on re-enable
            return;
        }

        // Fade out when not active
        if (!active) {
            fadeAlpha -= cfg.getFadeSpeed();
            if (fadeAlpha <= 0f) { fadeAlpha = 0f; anchors.clear(); return; }
        }
        if (anchors.size() < 2) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(true);
        Vec3  cam     = mc.gameRenderer.getMainCamera().getPosition();
        Vec3  camFwd  = Vec3.directionFromRotation(
                mc.gameRenderer.getMainCamera().getXRot(),
                mc.gameRenderer.getMainCamera().getYRot());

        float halfW    = cfg.getHalfWidth();
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
            double segLen = pA.distanceTo(pB);
            double sagDepth = sag * segLen;

            // Build subdivided polyline with sag applied
            Vec3[] pts = new Vec3[SUBDIVISIONS + 1];
            for (int i = 0; i <= SUBDIVISIONS; i++) {
                float u = (float) i / SUBDIVISIONS;
                // Lerp base position
                Vec3 base = lerp(pA, pB, u);
                // Catenary-style sag: parabolic drop, max at u=0.5
                double drop = sagDepth * 4.0 * u * (1.0 - u);
                pts[i] = base.add(0, -drop, 0);
            }

            // Pre-compute miter right vectors (same gap-free technique as ItemTrailRenderer)
            Vec3[] rights = new Vec3[pts.length];
            for (int i = 0; i < pts.length; i++) {
                Vec3 segBefore = (i > 0)               ? pts[i].subtract(pts[i - 1]).normalize() : null;
                Vec3 segAfter  = (i < pts.length - 1)  ? pts[i + 1].subtract(pts[i]).normalize() : null;
                Vec3 avgSeg;
                if (segBefore != null && segAfter != null) {
                    Vec3 sum = segBefore.add(segAfter);
                    avgSeg = sum.lengthSqr() > 1e-10 ? sum.normalize() : segAfter;
                } else {
                    avgSeg = segAfter != null ? segAfter : segBefore;
                }
                rights[i] = billboardRight(camFwd, avgSeg);
            }

            // Emit quads
            for (int i = 0; i < SUBDIVISIONS; i++) {
                Vec3 a = pts[i];
                Vec3 b = pts[i + 1];

                // Local t within this sub-segment, mapped to global chain t
                float tA = globalTA + (globalTB - globalTA) * ((float) i       / SUBDIVISIONS);
                float tB = globalTA + (globalTB - globalTA) * ((float)(i + 1)  / SUBDIVISIONS);

                // Alpha: full at player end (t=0), fades slightly toward last follower
                float aA = (1f - tA * 0.35f) * headAlpha;
                float aB = (1f - tB * 0.35f) * headAlpha;

                // Width tapers toward the follower end
                float hwA = halfW * (1f - tA * 0.4f);
                float hwB = halfW * (1f - tB * 0.4f);

                // Color: slightly darker/warmer toward follower end
                float rA = CR + (1f - CR) * tA * 0.15f;
                float gA = CG * (1f - tA * 0.1f);
                float rB = CR + (1f - CR) * tB * 0.15f;
                float gB = CG * (1f - tB * 0.1f);

                Vec3 offsetA = rights[i].scale(hwA);
                Vec3 offsetB = rights[i + 1].scale(hwB);

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

                buf.addVertex(mat, ax0, ay0, az0).setColor(rA, gA, CB, aA);
                buf.addVertex(mat, ax1, ay1, az1).setColor(rA, gA, CB, aA);
                buf.addVertex(mat, bx1, by1, bz1).setColor(rB, gB, CB, aB);
                buf.addVertex(mat, bx0, by0, bz0).setColor(rB, gB, CB, aB);
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

    private static Vec3 lerp(Vec3 a, Vec3 b, float t) {
        return new Vec3(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t);
    }

    private static Vec3 billboardRight(Vec3 camFwd, Vec3 seg) {
        Vec3 r = camFwd.cross(seg).normalize();
        if (r.lengthSqr() < 1e-10) r = new Vec3(0, 1, 0).cross(seg).normalize();
        if (r.lengthSqr() < 1e-10) r = new Vec3(1, 0, 0).cross(seg).normalize();
        if (r.lengthSqr() < 1e-10) r = new Vec3(1, 0, 0); // last-resort unit vector
        return r;
    }
}
