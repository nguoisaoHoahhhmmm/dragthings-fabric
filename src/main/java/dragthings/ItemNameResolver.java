package dragthings;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Optional;

/**
 * Resolves free-typed text (from the crafting-target GUI) into an actual
 * vanilla {@link Item}. Supports three input styles, tried in order:
 *
 *   1. Resource ID, with or without namespace: "minecraft:iron_pickaxe" or
 *      "iron_pickaxe" (namespace defaults to "minecraft" if omitted).
 *   2. Exact display name, case-insensitive: "Iron Pickaxe" / "iron pickaxe".
 *   3. Partial display name match (first hit wins): "pickaxe" → whichever
 *      pickaxe item comes first in the registry — a forgiving fallback for
 *      typos/partial input, not meant to be precise.
 *
 * Common code (not client- or server-only) since both the C2S packet
 * handler (server-side) and any future client-side autocomplete/preview
 * could reasonably want the same resolution logic.
 */
public final class ItemNameResolver {

    private ItemNameResolver() {}

    public static Optional<Item> resolve(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String trimmed = input.trim();

        // 1. Direct resource ID
        String candidateId = trimmed.toLowerCase().replace(' ', '_');
        ResourceLocation rl = candidateId.contains(":")
                ? ResourceLocation.tryParse(candidateId)
                : ResourceLocation.tryParse("minecraft:" + candidateId);
        if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
            return Optional.of(BuiltInRegistries.ITEM.get(rl));
        }

        // 2. Exact display name match, case-insensitive
        String needle = trimmed.toLowerCase();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.getDescription().getString().equalsIgnoreCase(needle)) {
                return Optional.of(item);
            }
        }

        // 3. Partial display name match — forgiving fallback, first hit wins
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.getDescription().getString().toLowerCase().contains(needle)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }
}