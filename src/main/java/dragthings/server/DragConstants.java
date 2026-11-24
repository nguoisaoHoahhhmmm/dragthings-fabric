package dragthings.server;

/** Limits shared by the server-side drag handlers. */
public final class DragConstants {

    private DragConstants() {}

    /**
     * Max squared distance between player and dragged thing.
     *
     * The client lets the drag distance scroll out to 12 blocks, and every
     * follower in a chain trails 0.45 blocks further behind the one ahead.
     * 16 blocks was still rejecting legitimate far-chain drags and caused
     * snap-back jitter, so 24 gives headroom for leader (12) + a long chain
     * while still blocking absurd values (the old (0,0,0) teleport bug was
     * 60+ blocks).
     */
    public static final double MAX_DRAG_DISTANCE_SQ = 24.0 * 24.0;

    public static final double MAX_THROW_SPEED = 2.5;
}