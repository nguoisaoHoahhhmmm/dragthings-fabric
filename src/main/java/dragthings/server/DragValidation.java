package dragthings.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server-side sanity checks for positions that came from a client packet. */
public final class DragValidation {

    /**
     * Maximum movement between two accepted sync packets (blocks).
     * The client syncs every 3 ticks and its spring can move ~1 block/tick,
     * so 4 leaves room for lag spikes while still blocking 20+ block jumps.
     */
    private static final double MAX_POS_DELTA_SQ = 4.0 * 4.0;

    private DragValidation() {}

    public static boolean finite(Vec3 pos) {
        return pos != null && Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z);
    }

    /**
     * Checks reach and per-packet movement. The first packet after a grab is
     * allowed to jump from the item's spawn position to the player's hand.
     */
    public static boolean validMove(ServerPlayer player, Vec3 requested, DragSession session) {
        if (!finite(requested)) return false;
        if (player.distanceToSqr(requested) > DragConstants.MAX_DRAG_DISTANCE_SQ) return false;

        if (session.consumeGraceJump()) return true;
        Vec3 last = session.lastServerPos();
        return !finite(last) || requested.distanceToSqr(last) <= MAX_POS_DELTA_SQ;
    }

    public static Vec3 clampThrow(Vec3 raw) {
        if (!finite(raw)) return Vec3.ZERO;
        if (raw.length() > DragConstants.MAX_THROW_SPEED) {
            return raw.normalize().scale(DragConstants.MAX_THROW_SPEED);
        }
        return raw;
    }
}
