package dragthings.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * A tiny, purely-visual FOV "kick" triggered when a dragged item slams into
 * something.
 *
 * Deliberately does NOT touch camera rotation (yaw/pitch): nudging the
 * player's actual look direction would throw off their aim without them
 * doing anything, which is bad feel and arguably unsafe mid-combat/mining.
 * FOV is a safe way to add "impact" — it's a zoom pulse only, it never
 * changes look direction or hit detection.
 */
public class ItemCameraShakeHandler {

    // Current impulse magnitude (0..MAX_SHAKE), decays every client tick.
    private static float shake     = 0f;
    private static float lastShake = 0f; // previous tick's value, for partial-tick interpolation

    private static final float DECAY            = 0.78f; // multiplicative decay per tick
    private static final float MAX_SHAKE        = 1.0f;  // caps how big a single impact can feel
    private static final float FOV_KICK_DEGREES = 4.5f;  // FOV swing at max shake

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            lastShake = shake;
            shake *= DECAY;
            if (shake < 0.01f) shake = 0f;
        });
    }

    /** Called when a dragged item's movement was blocked by a collision this tick. */
    public static void onImpact(double impactMagnitude) {
        float intensity = DragThingsConfig.get().feel.getCameraShakeIntensity();
        if (intensity <= 0f) return;

        float impulse = (float) Math.min(MAX_SHAKE, impactMagnitude * 1.8 * intensity);
        shake = Math.min(MAX_SHAKE, shake + impulse);
    }

    /**
     * FOV delta (degrees) to add this frame, smoothly interpolated between
     * ticks so the punch-in doesn't look stepped at high framerates.
     */
    public static float getFovKick(float partialTick) {
        float intensity = DragThingsConfig.get().feel.getCameraShakeIntensity();
        if (intensity <= 0f) return 0f;

        float interpolated = lastShake + (shake - lastShake) * partialTick;
        // A quick zoom-IN reads as "impact" better than zooming out, and it
        // naturally settles back to 0 as the shake value decays.
        return -interpolated * FOV_KICK_DEGREES * intensity;
    }
}