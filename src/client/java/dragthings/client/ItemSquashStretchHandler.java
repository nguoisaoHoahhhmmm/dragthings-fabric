package dragthings.client;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Classic animation-principle "squash & stretch" for dragged items.
 *
 * - Stretch: the faster an item moves, the more it elongates along its
 *   current direction of travel (and thins on the perpendicular axes, to
 *   fake volume preservation).
 * - Squash: on a hard collision (item slammed into a block by
 *   resolveBlockCollision), it briefly flattens along that same axis and
 *   springs back — a cheap "impact" pop that reads as weight.
 *
 * This class only tracks state and math. The actual PoseStack transform is
 * applied in ItemSquashStretchMixin, which reads getStretchDir/Factor/Pulse
 * while rendering.
 */
public class ItemSquashStretchHandler {

    private static class State {
        Vec3  stretchDir     = Vec3.ZERO; // last known world-space travel direction
        float stretchFactor  = 1f;        // eased elongation along stretchDir (>= 1)
        float squashPulse    = 0f;        // impact spring displacement (can go negative = flattened)
        float squashVelocity = 0f;        // spring velocity backing squashPulse
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    // Stretch: how much elongation per unit of speed (blocks/tick), and how
    // quickly the displayed stretch eases toward that target each tick.
    private static final double STRETCH_PER_SPEED = 2.5;
    private static final float  MAX_STRETCH        = 1.35f;
    private static final float  STRETCH_EASE       = 0.25f;

    // Squash: a lightly-damped spring so an impact pops and settles instead
    // of just snapping back to normal.
    private static final float SQUASH_STIFFNESS   = 0.32f;
    private static final float SQUASH_DAMPING     = 0.30f;
    private static final float MAX_SQUASH_IMPULSE = 0.5f;

    /**
     * Called once per drag tick for every dragged item (leader + followers).
     *
     * @param velocity    this item's current velocity vector (blocks/tick)
     * @param collided    whether resolveBlockCollision cancelled part of this
     *                    tick's intended movement
     * @param impactSpeed how much movement got cancelled by the collision
     *                    (0 if none)
     */
    public static void tick(ItemEntity item, Vec3 velocity, boolean collided, double impactSpeed) {
        State s = STATES.computeIfAbsent(item.getId(), id -> new State());

        float intensity = DragThingsConfig.get().feel.getSquashStretchIntensity();
        if (intensity <= 0f) {
            // Fully disabled — relax any in-flight state back to neutral instead
            // of freezing it mid-effect.
            s.stretchFactor  += (1f - s.stretchFactor) * STRETCH_EASE;
            s.squashPulse    = 0f;
            s.squashVelocity = 0f;
            return;
        }

        double speed = velocity.length();
        if (speed > 0.02) {
            s.stretchDir = velocity.normalize();
        }

        float maxStretch = 1f + (MAX_STRETCH - 1f) * intensity;
        float targetStretch = (float) Math.min(maxStretch, 1.0 + speed * STRETCH_PER_SPEED * intensity);
        s.stretchFactor += (targetStretch - s.stretchFactor) * STRETCH_EASE;

        if (collided && impactSpeed > 0.03) {
            float maxImpulse = MAX_SQUASH_IMPULSE * intensity;
            float impulse = (float) Math.min(maxImpulse, impactSpeed * 3.5 * intensity);
            s.squashVelocity -= impulse;
        }

        // Damped harmonic spring pulling squashPulse back to 0. Slight
        // underdamping gives a small rebound overshoot before it settles.
        float accel = -SQUASH_STIFFNESS * s.squashPulse - SQUASH_DAMPING * s.squashVelocity;
        s.squashVelocity += accel;
        s.squashPulse    += s.squashVelocity;
    }

    /** World-space axis the item should stretch along, or Vec3.ZERO if unknown. */
    public static Vec3 getStretchDir(ItemEntity item) {
        State s = STATES.get(item.getId());
        return s == null ? Vec3.ZERO : s.stretchDir;
    }

    /** Current elongation factor along the stretch axis (>= 1, eased). */
    public static float getStretchFactor(ItemEntity item) {
        State s = STATES.get(item.getId());
        return s == null ? 1f : s.stretchFactor;
    }

    /** Current impact spring displacement (negative = flattened right now). */
    public static float getSquashPulse(ItemEntity item) {
        State s = STATES.get(item.getId());
        return s == null ? 0f : s.squashPulse;
    }

    public static void clear(ItemEntity item) {
        STATES.remove(item.getId());
    }

    public static void clearAll() {
        STATES.clear();
    }
}