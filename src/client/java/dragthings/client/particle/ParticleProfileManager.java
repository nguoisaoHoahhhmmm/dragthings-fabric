package dragthings.client.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Loads every JSON under assets/dragthings/particle_profiles/ as one or
 * more ParticleProfiles, on initial resource load AND on every /reload or
 * F3+T — so you (or anyone using this mod) can drop in a new profile file,
 * hit F3+T, and see it live without restarting the game.
 *
 * Each file can contain either a single profile object:
 *   { "match": [...], "grab": {...} }
 * or an array of several profiles at once, so a batch of related
 * materials doesn't need one file each:
 *   [ { "match": [...], "grab": {...} }, { "match": [...], ... } ]
 *
 * This is what makes the system genuinely extensible without touching Java:
 * a new mob or item just needs a new profile (in a new file, or appended
 * into an existing array) matched by id/tag, dropped into this same folder
 * (in this mod's own resources, or in any resource pack / other mod that
 * wants to add its own drag particles).
 */
public class ParticleProfileManager implements SimpleSynchronousResourceReloadListener {

    public static final ParticleProfileManager INSTANCE = new ParticleProfileManager();
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("dragthings", "particle_profile_manager");
    private static final String FOLDER = "particle_profiles";

    private List<ParticleProfile> profiles = List.of();

    public static void init() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(INSTANCE);
    }

    @Override
    public ResourceLocation getFabricId() { return ID; }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        List<ParticleProfile> loaded = new ArrayList<>();

        Map<ResourceLocation, Resource> found = manager.listResources(FOLDER,
                path -> path.getPath().endsWith(".json"));

        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open())) {
                JsonElement root = JsonParser.parseReader(reader);

                if (root.isJsonArray()) {
                    // Multiple profiles in one file — index appended to the
                    // id purely so each one has a distinct name for logging
                    // (e.g. "dragthings:particle_profiles/materials.json#0").
                    // Purely cosmetic: match/priority/etc. all come from
                    // that element's own JSON, independent of the others.
                    // FIX: "#" is not a legal ResourceLocation path character
                    // (must match [a-z0-9/._-]) — using it here made the
                    // WHOLE file fail to parse for every array-based
                    // profile file, not just log a warning for this one
                    // entry. "_" is legal and just as readable for the
                    // debug-only id.
                    JsonArray array = root.getAsJsonArray();
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject obj = array.get(i).getAsJsonObject();
                        ResourceLocation subId = entry.getKey().withSuffix("_" + i);
                        loaded.add(ParticleProfile.fromJson(subId, obj));
                    }
                } else {
                    loaded.add(ParticleProfile.fromJson(entry.getKey(), root.getAsJsonObject()));
                }
            } catch (Exception e) {
                System.err.println("[dragthings] Failed to load particle profile " + entry.getKey() + ": " + e);
            }
        }

        // Highest priority first, so matchItem/matchEntity's first-match-wins
        // scan respects the "priority" field instead of arbitrary file order.
        loaded.sort(Comparator.comparingInt((ParticleProfile p) -> p.priority).reversed());
        this.profiles = loaded;

        System.out.println("[dragthings] Loaded " + loaded.size() + " particle profile(s)");
    }

    /** Specific match first, falls back to whichever item_default profile sorts first. */
    public ParticleProfile findItemProfile(ItemStack stack) {
        ParticleProfile fallback = null;
        for (ParticleProfile p : profiles) {
            if (p.isDefault()) {
                if (fallback == null && p.match.stream().anyMatch(c -> c.kind == MatchCondition.Kind.ITEM_DEFAULT)) {
                    fallback = p;
                }
                continue;
            }
            if (p.matchesItem(stack)) return p;
        }
        return fallback;
    }

    /** Specific match first, falls back to whichever entity_default profile sorts first. */
    public ParticleProfile findEntityProfile(Entity entity) {
        ParticleProfile fallback = null;
        for (ParticleProfile p : profiles) {
            if (p.isDefault()) {
                if (fallback == null && p.match.stream().anyMatch(c -> c.kind == MatchCondition.Kind.ENTITY_DEFAULT)) {
                    fallback = p;
                }
                continue;
            }
            if (p.matchesEntity(entity)) return p;
        }
        return fallback;
    }
}