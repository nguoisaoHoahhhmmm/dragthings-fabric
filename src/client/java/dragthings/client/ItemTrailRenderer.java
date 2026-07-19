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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Renders a smooth quad-billboard trail behind dragged items.
 *
 * Each segment is a quad (2 tris) whose edges are offset perpendicular to
 * both the segment direction AND the camera forward vector, keeping the
 * ribbon face-on at any angle (billboard).
 *
 * Gap fix: instead of computing a right vector per-segment, we pre-compute
 * a miter offset at each POINT by averaging the right vectors of the
 * adjacent segments. Adjacent quads then share exactly the same edge
 * vertices → no gap or overlap at joints.
 */
public final class ItemTrailRenderer {

    private static final double MIN_STEP       = 0.03;
    private static final int    MAX_POINTS_CAP = 48;

    private static final class TrailEntry {
        int               entityId;
        final Deque<Vec3> points  = new ArrayDeque<>(MAX_POINTS_CAP + 1);
        Vec3              lastPos = null;
        float             alpha   = 0f;

        TrailEntry(int id) { entityId = id; }
    }

    private static final List<TrailEntry> entries = new ArrayList<>();

    private ItemTrailRenderer() {}

    // ── Public API ────────────────────────────────────────────────────────

    public static void init() {
        WorldRenderEvents.LAST.register(ItemTrailRenderer::onRender);
    }

    public static void tickTrail(ItemEntity leader, List<ItemEntity> followers) {
        int needed = 1 + followers.size();
        while (entries.size() > needed) entries.remove(entries.size() - 1);
        while (entries.size() < needed) entries.add(new TrailEntry(-1));

        pushEntry(entries.get(0), leader);
        for (int i = 0; i < followers.size(); i++)
            pushEntry(entries.get(i + 1), followers.get(i));
    }

    public static void stopTrail() {
        for (TrailEntry e : entries) {
            e.lastPos = null;
            e.points.clear(); // prevent ghost trail on next drag start
        }
    }

    public static void clearAll() {
        entries.clear();
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private static void pushEntry(TrailEntry entry, ItemEntity item) {
        entry.entityId = item.getId();
        Vec3 pos = item.position();

        DragThingsConfig.TrailConfig cfg = DragThingsConfig.get().trail;
        int   maxPoints = Math.min(cfg.trailLength, MAX_POINTS_CAP);
        float fadeSpeed = cfg.getFadeSpeed();

        if (entry.lastPos == null || pos.distanceTo(entry.lastPos) >= MIN_STEP) {
            entry.points.addLast(pos);
            if (entry.points.size() > maxPoints) entry.points.pollFirst();
            entry.lastPos = pos;
        }
        entry.alpha = Math.min(1f, entry.alpha + fadeSpeed);
    }

    /**
     * Compute a stable billboard "right" vector at a single point.
     * seg is the direction going through or away from this point.
     */
    private static Vec3 billboardRight(Vec3 camForward, Vec3 seg) {
        Vec3 r = camForward.cross(seg).normalize();
        if (r.lengthSqr() < 1e-10) r = new Vec3(0, 1, 0).cross(seg).normalize();
        if (r.lengthSqr() < 1e-10) r = new Vec3(1, 0, 0).cross(seg).normalize();
        if (r.lengthSqr() < 1e-10) r = new Vec3(1, 0, 0); // last-resort unit vector
        return r;
    }

    // ── Render ────────────────────────────────────────────────────────────

    private static void onRender(WorldRenderContext ctx) {
        if (entries.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        DragThingsConfig.TrailConfig cfg = DragThingsConfig.get().trail;

        if (!cfg.enableTrail) {
            entries.forEach(e -> e.lastPos = null);
        }

        float fadeSpeed = cfg.getFadeSpeed();
        entries.removeIf(e -> {
            if (e.lastPos == null) {
                e.alpha -= fadeSpeed;
                return e.alpha <= 0f;
            }
            return false;
        });
        if (entries.isEmpty()) return;

        float partial    = ctx.tickCounter().getGameTimeDeltaPartialTick(true);
        Vec3  cam        = mc.gameRenderer.getMainCamera().getPosition();
        Vec3  camForward = Vec3.directionFromRotation(
                mc.gameRenderer.getMainCamera().getXRot(),
                mc.gameRenderer.getMainCamera().getYRot());

        float halfW     = cfg.getHalfWidth();
        float headAlpha = cfg.getHeadAlpha();
        float CR = cfg.getColorR();
        float CG = cfg.getColorG();
        float CB = cfg.getColorB();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        PoseStack pose = ctx.matrixStack();
        pose.pushPose();
        Matrix4f mat = pose.last().pose();

        Tesselator    tess         = Tesselator.getInstance();
        BufferBuilder buf          = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean       hasVertices  = false;

        for (TrailEntry entry : entries) {
            if (entry.points.size() < 2) continue;

            // Append live interpolated head so the trail connects to the item exactly
            List<Vec3> pts = new ArrayList<>(entry.points);
            var ent = mc.level.getEntity(entry.entityId);
            if (ent instanceof ItemEntity item) {
                pts.add(new Vec3(
                        item.xo + (item.getX() - item.xo) * partial,
                        item.yo + (item.getY() - item.yo) * partial,
                        item.zo + (item.getZ() - item.zo) * partial));
            }

            int n = pts.size();
            if (n < 2) continue;

            float baseAlpha = entry.alpha * headAlpha;

            // ── Pre-compute miter right vectors for every point ───────────
            // Each interior point averages the directions of its two adjacent
            // segments. Adjacent quads then share identical edge positions,
            // eliminating the gap/overlap that occurs when each segment uses
            // its own independent right vector.
            Vec3[] rights = new Vec3[n];
            for (int i = 0; i < n; i++) {
                Vec3 segBefore = (i > 0)     ? pts.get(i).subtract(pts.get(i - 1)).normalize() : null;
                Vec3 segAfter  = (i < n - 1) ? pts.get(i + 1).subtract(pts.get(i)).normalize() : null;

                Vec3 avgSeg;
                if (segBefore != null && segAfter != null) {
                    Vec3 sum = segBefore.add(segAfter);
                    avgSeg = sum.lengthSqr() > 1e-10 ? sum.normalize() : segAfter;
                } else {
                    avgSeg = segAfter != null ? segAfter : segBefore;
                }

                rights[i] = billboardRight(camForward, avgSeg);
            }

            // ── Emit one quad per segment using the shared miter offsets ──
            for (int i = 0; i < n - 1; i++) {
                Vec3 a = pts.get(i);
                Vec3 b = pts.get(i + 1);

                // t = 0 at tail, 1 at head
                float tA = (float) i       / (n - 1);
                float tB = (float)(i + 1)  / (n - 1);

                // Quadratic alpha: transparent at tail, opaque at head
                float aA = tA * tA * baseAlpha;
                float aB = tB * tB * baseAlpha;

                // Colour warms slightly toward head
                float rA = CR + (1f - CR) * tA * 0.3f;
                float gA = CG - (CG - 0.5f) * (1f - tA) * 0.2f;
                float rB = CR + (1f - CR) * tB * 0.3f;
                float gB = CG - (CG - 0.5f) * (1f - tB) * 0.2f;

                // Width tapers: 0.5× at tail → 1.0× at head
                float hwA = halfW * (tA * 0.5f + 0.5f);
                float hwB = halfW * (tB * 0.5f + 0.5f);

                Vec3 offsetA = rights[i].scale(hwA);
                Vec3 offsetB = rights[i + 1].scale(hwB);

                // 4 corners of the quad, relative to camera
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
}
