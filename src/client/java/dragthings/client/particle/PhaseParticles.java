package dragthings.client.particle;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The particle list for a single phase (grab / release / drag) of a
 * profile. minIntervalTicks/maxIntervalTicks only apply to the "drag" phase
 * (continuous ticking while actively dragging) — leave them unset (-1) to
 * fall back to ParticleEngine's default speed-based interval formula, the
 * same one DragParticleEffects used to hard-code for every category.
 */
public class PhaseParticles {

    public final List<ParticleSpec> particles;
    public final int minIntervalTicks;
    public final int maxIntervalTicks;

    private PhaseParticles(List<ParticleSpec> particles, int minIntervalTicks, int maxIntervalTicks) {
        this.particles = particles;
        this.minIntervalTicks = minIntervalTicks;
        this.maxIntervalTicks = maxIntervalTicks;
    }

    public static PhaseParticles fromJson(JsonObject obj) {
        List<ParticleSpec> specs = new ArrayList<>();
        if (obj.has("particles")) {
            obj.getAsJsonArray("particles").forEach(el -> specs.add(ParticleSpec.fromJson(el.getAsJsonObject())));
        }
        int minTicks = obj.has("minIntervalTicks") ? obj.get("minIntervalTicks").getAsInt() : -1;
        int maxTicks = obj.has("maxIntervalTicks") ? obj.get("maxIntervalTicks").getAsInt() : -1;
        return new PhaseParticles(specs, minTicks, maxTicks);
    }

    public boolean hasCustomInterval() { return minIntervalTicks > 0 && maxIntervalTicks > 0; }
}