package dragthings.client.arm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A curved, multi-segment arm. Built by hand from the player's skin:
 *  - the arm follows a quadratic Bezier whose control point is on a spring,
 *    so it lags and wobbles when the target moves;
 *  - the shoulder and hand keep their proportions, and only the middle of
 *    the arm is stretched (nine-slice along its length);
 *  - when the target is closer than a natural arm length, the arm bends
 *    instead of being squashed.
 */
final class BezierArmStyle implements DragArmRenderer.ArmStyle {

    static final BezierArmStyle INSTANCE = new BezierArmStyle();

    private BezierArmStyle() {}

    // ── Tuning (visual only) ──────────────────────────────────────────────
    private static final float ARM_PX = 12f;
    /** Shoulder and hand caps keep their size; only the middle stretches. */
    private static final float CAP_LEN = 3f / 16f;
    private static final float CAP_PX = 3f;
    /** Sleeve layer is the arm inflated by this many pixels. */
    private static final float SLEEVE_INFLATE_PX = 0.25f;
    /** Extra rings in the stretched middle, so the curve looks smooth. */
    private static final int MIDDLE_RINGS = 6;
    private static final int SPINE_SAMPLES = 24;

    /** Curve control point spring. Damping ratio ~0.6: slight wobble. */
    private static final float SPRING_K = 180f;
    private static final float SPRING_D = 16f;
    /** Gravity droop of an extended arm, as a fraction of its length. */
    private static final float DROOP = 0.03f;
    private static final float MAX_SAG = 0.45f;

    @Override
    public void draw(DragArmRenderer.DrawCtx c) {
        Vec3 shoulder = c.shoulder();
        Vec3 tip = c.tip();
        Vec3 chord = tip.subtract(shoulder);
        double dist = chord.length();
        if (dist < DragArmRenderer.MIN_DISTANCE) return;
        Vec3 dir = chord.scale(1.0 / dist);

        // Sag: bend when closer than a natural arm, plus a little droop.
        double bend = dist < DragArmRenderer.ARM_LEN
                ? Math.sqrt((DragArmRenderer.ARM_LEN - dist) * 3.0 * dist / 8.0)
                : 0.0;
        double sag = Math.min(MAX_SAG, bend + DROOP * dist);

        Vec3 outward = c.leftArm() ? c.bodyLeft() : c.bodyLeft().scale(-1);
        Vec3 ref = new Vec3(0, -1, 0).add(outward.scale(0.6));
        Vec3 bendDir = ref.subtract(dir.scale(ref.dot(dir)));
        if (bendDir.lengthSqr() < 1e-4) bendDir = outward;
        bendDir = bendDir.normalize();

        // Control point offset is 2x the apex sag of a quadratic Bezier.
        Vec3 ctrlTarget = shoulder.add(tip).scale(0.5).add(bendDir.scale(2.0 * sag));
        stepSpring(c.limb(), ctrlTarget, c.dt());

        Spine spine = new Spine(shoulder, c.limb().ctrl, tip);
        List<Ring> rings = buildRings(spine, c.bodyForward());

        int light = DragArmRenderer.lightAt(c.level(), spine.at(spine.length * 0.5), c.fallbackLight());

        boolean left = c.leftArm();
        int u0 = left ? 32 : 40;
        int v0 = left ? 48 : 16;
        int su0 = left ? 48 : 40;
        int sv0 = left ? 48 : 32;
        float wPx = c.slim() ? 3f : 4f;

        PoseStack.Pose pose = c.poseStack().last();
        drawLayer(c.vc(), pose, c.camPos(), rings, u0, v0, wPx, 0f, light);

        PlayerModelPart sleevePart = left ? PlayerModelPart.LEFT_SLEEVE : PlayerModelPart.RIGHT_SLEEVE;
        if (c.player().isModelPartShown(sleevePart)) {
            drawLayer(c.vc(), pose, c.camPos(), rings, su0, sv0, wPx, SLEEVE_INFLATE_PX, light);
        }
    }

    // ── Spring ────────────────────────────────────────────────────────────
    private static void stepSpring(DragArmRenderer.Limb l, Vec3 target, float dt) {
        if (!l.springInit) {
            l.ctrl = target;
            l.vel = Vec3.ZERO;
            l.springInit = true;
            return;
        }
        int sub = Math.max(1, (int) Math.ceil(dt / 0.008f));
        float h = dt / sub;
        for (int i = 0; i < sub; i++) {
            Vec3 acc = target.subtract(l.ctrl).scale(SPRING_K).subtract(l.vel.scale(SPRING_D));
            l.vel = l.vel.add(acc.scale(h));
            l.ctrl = l.ctrl.add(l.vel.scale(h));
        }
    }

    // ── Geometry ──────────────────────────────────────────────────────────
    /** A quadratic Bezier with an arc-length table. */
    private static final class Spine {
        final Vec3[] pts = new Vec3[SPINE_SAMPLES + 1];
        final double[] cum = new double[SPINE_SAMPLES + 1];
        final double length;

        Spine(Vec3 p0, Vec3 p1, Vec3 p2) {
            for (int i = 0; i <= SPINE_SAMPLES; i++) {
                double t = (double) i / SPINE_SAMPLES;
                double u = 1.0 - t;
                pts[i] = p0.scale(u * u).add(p1.scale(2 * u * t)).add(p2.scale(t * t));
                cum[i] = i == 0 ? 0 : cum[i - 1] + pts[i].distanceTo(pts[i - 1]);
            }
            length = cum[SPINE_SAMPLES];
        }

        Vec3 at(double s) {
            if (s <= 0) return pts[0];
            if (s >= length) return pts[SPINE_SAMPLES];
            int lo = 0;
            int hi = SPINE_SAMPLES;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (cum[mid] <= s) lo = mid;
                else hi = mid;
            }
            double seg = cum[hi] - cum[lo];
            double f = seg > 1e-9 ? (s - cum[lo]) / seg : 0.0;
            return pts[lo].lerp(pts[hi], f);
        }

        Vec3 tangent(double s) {
            Vec3 d = at(Math.min(length, s + 0.03)).subtract(at(Math.max(0, s - 0.03)));
            if (d.lengthSqr() < 1e-8) d = pts[SPINE_SAMPLES].subtract(pts[0]);
            return d.normalize();
        }
    }

    /** A cross-section of the arm. ey points down the arm toward the hand. */
    private record Ring(Vec3 c, Vec3 ex, Vec3 ey, Vec3 ez, float yPx) {}

    /**
     * Rings are placed so that the shoulder cap and hand cap each get a ring
     * exactly at their boundary; the texture only stretches between them.
     */
    private static List<Ring> buildRings(Spine sp, Vec3 bodyForward) {
        double len = sp.length;
        boolean general = len > 2.0 * CAP_LEN + 1e-4;

        List<Double> ss = new ArrayList<>();
        ss.add(0.0);
        if (general) {
            double m0 = CAP_LEN;
            double m1 = len - CAP_LEN;
            ss.add(m0);
            for (int i = 1; i < MIDDLE_RINGS; i++) ss.add(m0 + (m1 - m0) * i / MIDDLE_RINGS);
            ss.add(m1);
        } else {
            ss.add(len * 0.5);
        }
        ss.add(len);

        List<Ring> rings = new ArrayList<>(ss.size());
        Vec3 ex = null;
        Vec3 ez;
        for (double s : ss) {
            Vec3 ey = sp.tangent(s);
            if (ex == null) {
                // Front face (-Z) turns upward, like a natural forward reach.
                Vec3 up = new Vec3(0, 1, 0);
                Vec3 f = up.subtract(ey.scale(up.dot(ey)));
                if (f.lengthSqr() < 1e-4) f = bodyForward.subtract(ey.scale(bodyForward.dot(ey)));
                f = f.normalize();
                ez = f.scale(-1);
                ex = ey.cross(ez);
            } else {
                // Carry the frame along the curve without twisting it.
                ex = ex.subtract(ey.scale(ex.dot(ey))).normalize();
                ez = ex.cross(ey);
            }

            float y;
            if (!general) {
                y = (float) (s / len * ARM_PX);
            } else if (s <= CAP_LEN) {
                y = (float) (s / CAP_LEN * CAP_PX);
            } else if (s >= len - CAP_LEN) {
                y = (float) (ARM_PX - CAP_PX + (s - (len - CAP_LEN)) / CAP_LEN * CAP_PX);
            } else {
                y = (float) (CAP_PX + (s - CAP_LEN) / (len - 2.0 * CAP_LEN) * (ARM_PX - 2.0 * CAP_PX));
            }
            rings.add(new Ring(sp.at(s), ex, ey, ez, y));
        }
        return rings;
    }

    /**
     * Emits one skin layer (arm or sleeve) as a box that follows the rings.
     * Skin layout of a w x 12 x 4 limb at (u0, v0), as in vanilla:
     * [right d][front w][left d][back w] below a row holding top and bottom.
     */
    private static void drawLayer(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, List<Ring> rings,
                                  float u0, float v0, float wPx, float inflatePx, int light) {
        final float dPx = 4f;
        float hw = (wPx * 0.5f + inflatePx) / 16f;
        float hd = (dPx * 0.5f + inflatePx) / 16f;
        float push = inflatePx / 16f;

        float uR = u0;
        float uF = u0 + dPx;
        float uL = u0 + dPx + wPx;
        float uB = u0 + 2 * dPx + wPx;
        float uBot = u0 + dPx + wPx;
        float vBody = v0 + dPx;

        // The sleeve's end caps are pushed out so they don't z-fight the arm's.
        int n = rings.size();
        Vec3[] centers = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Ring r = rings.get(i);
            Vec3 c = r.c();
            if (i == 0) c = c.subtract(r.ey().scale(push));
            else if (i == n - 1) c = c.add(r.ey().scale(push));
            centers[i] = c;
        }

        for (int i = 0; i < n - 1; i++) {
            Ring a = rings.get(i);
            Ring b = rings.get(i + 1);
            float va = vBody + a.yPx();
            float vb = vBody + b.yPx();

            // front (-Z)
            quad(vc, pose, cam, light, a.ez().scale(-1),
                    corner(centers[i], a, -1, -1, hw, hd), uF, va,
                    corner(centers[i], a, +1, -1, hw, hd), uF + wPx, va,
                    corner(centers[i + 1], b, +1, -1, hw, hd), uF + wPx, vb,
                    corner(centers[i + 1], b, -1, -1, hw, hd), uF, vb);
            // back (+Z)
            quad(vc, pose, cam, light, a.ez(),
                    corner(centers[i], a, +1, +1, hw, hd), uB, va,
                    corner(centers[i], a, -1, +1, hw, hd), uB + wPx, va,
                    corner(centers[i + 1], b, -1, +1, hw, hd), uB + wPx, vb,
                    corner(centers[i + 1], b, +1, +1, hw, hd), uB, vb);
            // -X side
            quad(vc, pose, cam, light, a.ex().scale(-1),
                    corner(centers[i], a, -1, +1, hw, hd), uR, va,
                    corner(centers[i], a, -1, -1, hw, hd), uR + dPx, va,
                    corner(centers[i + 1], b, -1, -1, hw, hd), uR + dPx, vb,
                    corner(centers[i + 1], b, -1, +1, hw, hd), uR, vb);
            // +X side
            quad(vc, pose, cam, light, a.ex(),
                    corner(centers[i], a, +1, -1, hw, hd), uL, va,
                    corner(centers[i], a, +1, +1, hw, hd), uL + dPx, va,
                    corner(centers[i + 1], b, +1, +1, hw, hd), uL + dPx, vb,
                    corner(centers[i + 1], b, +1, -1, hw, hd), uL, vb);
        }

        // Shoulder cap (top face)
        Ring first = rings.get(0);
        quad(vc, pose, cam, light, first.ey().scale(-1),
                corner(centers[0], first, -1, -1, hw, hd), uF, v0 + dPx,
                corner(centers[0], first, +1, -1, hw, hd), uF + wPx, v0 + dPx,
                corner(centers[0], first, +1, +1, hw, hd), uF + wPx, v0,
                corner(centers[0], first, -1, +1, hw, hd), uF, v0);

        // Hand end (bottom face)
        Ring last = rings.get(n - 1);
        quad(vc, pose, cam, light, last.ey(),
                corner(centers[n - 1], last, -1, -1, hw, hd), uBot, v0,
                corner(centers[n - 1], last, +1, -1, hw, hd), uBot + wPx, v0,
                corner(centers[n - 1], last, +1, +1, hw, hd), uBot + wPx, v0 + dPx,
                corner(centers[n - 1], last, -1, +1, hw, hd), uBot, v0 + dPx);
    }

    private static Vec3 corner(Vec3 c, Ring r, int sx, int sz, float hw, float hd) {
        return c.add(r.ex().scale(sx * hw)).add(r.ez().scale(sz * hd));
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, int light, Vec3 normal,
                             Vec3 p0, float u0, float v0,
                             Vec3 p1, float u1, float v1,
                             Vec3 p2, float u2, float v2,
                             Vec3 p3, float u3, float v3) {
        float nx = (float) normal.x;
        float ny = (float) normal.y;
        float nz = (float) normal.z;
        vertex(vc, pose, cam, light, p0, u0, v0, nx, ny, nz);
        vertex(vc, pose, cam, light, p1, u1, v1, nx, ny, nz);
        vertex(vc, pose, cam, light, p2, u2, v2, nx, ny, nz);
        vertex(vc, pose, cam, light, p3, u3, v3, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, int light, Vec3 p,
                               float u, float v, float nx, float ny, float nz) {
        vc.addVertex(pose, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                .setColor(255, 255, 255, 255)
                .setUv(u / 64f, v / 64f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}