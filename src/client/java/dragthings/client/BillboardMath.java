package dragthings.client;

import net.minecraft.world.phys.Vec3;

/**
 * Small shared math helpers for the ribbon/billboard renderers
 * (ChainRenderer, ItemTrailRenderer). Pulled out of both files since they
 * had identical copies of billboardRight().
 */
final class BillboardMath {

    private BillboardMath() {}

    /**
     * Stable "right" vector for a billboarded ribbon segment: perpendicular
     * to both the camera's forward direction and the segment direction.
     * Falls back through a couple of alternate axes if the segment happens
     * to be parallel to the primary fallback (degenerate cross product).
     */
    static Vec3 billboardRight(Vec3 camForward, Vec3 seg) {
        Vec3 r = camForward.cross(seg);
        if (r.lengthSqr() < 1e-10) r = new Vec3(0, 1, 0).cross(seg);
        if (r.lengthSqr() < 1e-10) r = new Vec3(1, 0, 0).cross(seg);
        if (r.lengthSqr() < 1e-10) return new Vec3(1, 0, 0); // last-resort unit vector
        return r.normalize();
    }

    static Vec3 lerp(Vec3 a, Vec3 b, float t) {
        return new Vec3(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t);
    }
}