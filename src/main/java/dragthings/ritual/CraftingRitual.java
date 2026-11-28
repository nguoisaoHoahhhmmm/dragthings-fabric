package dragthings.ritual;

import dragthings.Dragthings;
import dragthings.config.GameplayConfig;
import dragthings.config.RitualConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quick crafting: drag a Crafting Table next to the ingredients of a
 * SHAPELESS recipe and it crafts by itself, dropping the result at the table.
 *
 * Only shapeless recipes work, since there is no grid to give a shape.
 * Ingredients may come from one stack (two planks from a stack of planks are
 * fine) or from different item entities.
 */
public final class CraftingRitual {

    // rituals.json, section "crafting": enabled, radius, cooldownTicks, minIngredients
    private static RitualConfig.Crafting cfg() { return GameplayConfig.rituals().crafting; }
    /** Look for a recipe this often (ticks); scanning every recipe each tick is wasteful. */
    private static final int CHECK_INTERVAL = 5;

    // table entity id -> the player dragging it. Keeping the player lets a
    // disconnect clean the entry up instead of leaking it.
    private static final Map<Integer, ServerPlayer> activeTables = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> cooldown = new ConcurrentHashMap<>();

    private CraftingRitual() {}

    // ── Public API (same signatures the drag handlers already call) ───────

    public static void onStartDrag(int entityId, ServerLevel level, ServerPlayer player) {
        if (!cfg().enabled) return;
        boolean isNew = activeTables.put(entityId, player) == null;
        if (isNew && level.getEntity(entityId) instanceof ItemEntity table) {
            level.playSound(null, table.blockPosition(), SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.4f, 0.7f);
        }
    }

    public static void onRelease(int entityId) {
        activeTables.remove(entityId);
        cooldown.remove(entityId);
    }

    public static void tick() {
        cooldown.replaceAll((id, ticks) -> ticks - 1);
        cooldown.values().removeIf(ticks -> ticks <= 0);
        if (activeTables.isEmpty()) return;

        Iterator<Map.Entry<Integer, ServerPlayer>> it = activeTables.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ServerPlayer> entry = it.next();
            int tableId = entry.getKey();
            ServerPlayer player = entry.getValue();

            if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) {
                it.remove();
                cooldown.remove(tableId);
                continue;
            }
            if (!(level.getEntity(tableId) instanceof ItemEntity table) || !table.isAlive()) {
                it.remove();
                cooldown.remove(tableId);
                continue;
            }

            if (cooldown.containsKey(tableId)) continue;
            if ((level.getGameTime() + tableId) % CHECK_INTERVAL != 0) continue;
            if (tryCraft(level, table)) cooldown.put(tableId, cfg().cooldownTicks);
        }
    }

    // ── Crafting ──────────────────────────────────────────────────────────

    private static boolean tryCraft(ServerLevel level, ItemEntity table) {
        AABB box = new AABB(table.position(), table.position()).inflate(cfg().radius);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != table && i.isAlive() && !i.getItem().isEmpty() && !i.hasPickUpDelay());
        if (nearby.isEmpty()) return false;

        int totalItems = 0;
        for (ItemEntity e : nearby) totalItems += e.getItem().getCount();
        if (totalItems < cfg().minIngredients) return false;

        // Prefer the recipe that uses the most ingredients, so a specific
        // recipe wins over a smaller one that happens to fit inside it.
        List<ShapelessRecipe> candidates = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (holder.value() instanceof ShapelessRecipe r
                    && r.getIngredients().size() >= cfg().minIngredients
                    && r.getIngredients().size() <= totalItems) {
                candidates.add(r);
            }
        }
        candidates.sort(Comparator.comparingInt((ShapelessRecipe r) -> r.getIngredients().size()).reversed());

        for (ShapelessRecipe recipe : candidates) {
            int[] taken = new int[nearby.size()]; // how many were claimed from each entity
            if (!claim(recipe.getIngredients(), nearby, taken)) continue;

            // Let the recipe confirm the combination (tags, custom conditions)
            List<ItemStack> inputs = new ArrayList<>();
            for (int i = 0; i < nearby.size(); i++) {
                if (taken[i] > 0) inputs.add(nearby.get(i).getItem().copyWithCount(taken[i]));
            }
            List<ItemStack> flat = new ArrayList<>();
            for (ItemStack s : inputs) for (int n = 0; n < s.getCount(); n++) flat.add(s.copyWithCount(1));
            CraftingInput input = CraftingInput.of(flat.size(), 1, flat);
            if (!recipe.matches(input, level)) continue;

            ItemStack result = recipe.assemble(input, level.registryAccess());
            if (result.isEmpty()) continue;

            for (int i = 0; i < nearby.size(); i++) {
                if (taken[i] > 0) RitualUtil.shrinkOrDiscard(nearby.get(i), taken[i]);
            }
            RitualUtil.spawnResult(level, table, result);
            effects(level, table);
            Dragthings.LOGGER.debug("Crafting ritual: {} (table #{})", result.getHoverName().getString(), table.getId());
            return true;
        }
        return false;
    }

    /**
     * Gives every ingredient one item from the nearby stacks. One stack can
     * supply several ingredients. Fills {@code taken} with how many items were
     * claimed from each entity; nothing is consumed here.
     */
    private static boolean claim(List<Ingredient> ingredients, List<ItemEntity> nearby, int[] taken) {
        for (Ingredient ing : ingredients) {
            boolean found = false;
            for (int i = 0; i < nearby.size(); i++) {
                ItemStack stack = nearby.get(i).getItem();
                if (taken[i] < stack.getCount() && ing.test(stack)) {
                    taken[i]++;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static void effects(ServerLevel level, ItemEntity table) {
        level.playSound(null, table.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.9f, 1.1f);
        level.playSound(null, table.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.4f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, table.getX(), table.getY() + 0.4, table.getZ(),
                12, 0.3, 0.2, 0.3, 0.0);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, table.getX(), table.getY() + 0.3, table.getZ(),
                8, 0.25, 0.2, 0.25, 0.02);
    }
}