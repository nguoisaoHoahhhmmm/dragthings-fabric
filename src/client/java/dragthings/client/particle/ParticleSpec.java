package dragthings.client.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * One particle definition read straight from a profile JSON's "particles"
 * array. Deliberately a plain mutable-free data holder — all the actual
 * emission logic (registry lookup, color resolution, jitter/fan-out) lives
 * in ParticleEngine, so this class stays a dumb JSON mirror that's easy to
 * extend with new fields without touching emission code every time.
 *
 * Recognized "type" values:
 *   - "dust"           -> DustParticleOptions, needs color (+ optional scale)
 *   - "item_icon"      -> the dragged item's own icon (ItemParticleOption) —
 *                         only meaningful in an item context, silently
 *                         skipped for mobs
 *   - "colored_effect" -> ParticleTypes.ENTITY_EFFECT tinted via
 *                         ColorParticleOption, needs color
 *   - anything else     -> looked up directly in the particle type registry
 *                         (must be a SimpleParticleType — covers the vast
 *                         majority of vanilla particles: poof, cloud, crit,
 *                         smoke, splash, bubble, enchant, note, etc.)
 */
public class ParticleSpec {

    public final String type;
    public final int count;
    public final float speedMultiplier;
    public final int[] color;       // {r,g,b} 0-255, or null
    public final String colorSource; // "dye" | "potion_contents" | "flower" | "biome_leaf" | null
    public final float scale;        // dust scale, ignored otherwise
    public final int everyN;         // emit only on every Nth loop index (1 = every time)

    private ParticleSpec(String type, int count, float speedMultiplier, int[] color,
                         String colorSource, float scale, int everyN) {
        this.type = type;
        this.count = count;
        this.speedMultiplier = speedMultiplier;
        this.color = color;
        this.colorSource = colorSource;
        this.scale = scale;
        this.everyN = everyN;
    }

    public static ParticleSpec fromJson(JsonObject obj) {
        String type = obj.get("type").getAsString();
        int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
        float speedMult = obj.has("speedMultiplier") ? obj.get("speedMultiplier").getAsFloat() : 1.0f;
        float scale = obj.has("scale") ? obj.get("scale").getAsFloat() : 1.0f;
        int everyN = obj.has("everyN") ? obj.get("everyN").getAsInt() : 1;
        String colorSource = obj.has("colorSource") ? obj.get("colorSource").getAsString() : null;

        int[] color = null;
        if (obj.has("color")) {
            JsonArray arr = obj.getAsJsonArray("color");
            color = new int[]{ arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt() };
        }

        return new ParticleSpec(type, count, speedMult, color, colorSource, scale, Math.max(1, everyN));
    }
}