package dragthings.client.particle;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One JSON file under assets/dragthings/particle_profiles/. A profile
 * matches an item or entity if ANY of its "match" conditions match (OR),
 * and defines what to spawn for each of the three phases it cares about —
 * any phase can be omitted (e.g. a profile with only "grab" defined simply
 * spawns nothing extra on release/drag).
 *
 * "priority" (default 0, higher wins) only matters when two non-default
 * profiles would otherwise both match the same item/entity — lets you ship
 * an override without worrying about file load order. Default profiles
 * (item_default / entity_default) are always resolved last regardless of
 * priority, since they're deliberately the catch-all.
 */
public class ParticleProfile {

    public final ResourceLocation id;
    public final List<MatchCondition> match;
    public final int priority;
    public final PhaseParticles grab;
    public final PhaseParticles release;
    public final PhaseParticles drag;

    private ParticleProfile(ResourceLocation id, List<MatchCondition> match, int priority,
                            PhaseParticles grab, PhaseParticles release, PhaseParticles drag) {
        this.id = id;
        this.match = match;
        this.priority = priority;
        this.grab = grab;
        this.release = release;
        this.drag = drag;
    }

    public static ParticleProfile fromJson(ResourceLocation id, JsonObject root) {
        List<MatchCondition> conditions = new ArrayList<>();
        root.getAsJsonArray("match").forEach(el -> conditions.add(MatchCondition.fromJson(el.getAsJsonObject())));

        int priority = root.has("priority") ? root.get("priority").getAsInt() : 0;

        PhaseParticles grab    = root.has("grab")    ? PhaseParticles.fromJson(root.getAsJsonObject("grab"))    : null;
        PhaseParticles release = root.has("release") ? PhaseParticles.fromJson(root.getAsJsonObject("release")) : null;
        PhaseParticles drag    = root.has("drag")    ? PhaseParticles.fromJson(root.getAsJsonObject("drag"))    : null;

        return new ParticleProfile(id, conditions, priority, grab, release, drag);
    }

    public boolean isDefault() {
        return match.stream().anyMatch(MatchCondition::isDefault);
    }

    public boolean matchesItem(ItemStack stack) {
        return match.stream().filter(MatchCondition::isItemKind).anyMatch(c -> c.matchesItem(stack));
    }

    public boolean matchesEntity(Entity entity) {
        return match.stream().filter(MatchCondition::isEntityKind).anyMatch(c -> c.matchesEntity(entity));
    }
}