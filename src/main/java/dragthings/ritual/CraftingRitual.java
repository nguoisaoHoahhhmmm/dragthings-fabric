package dragthings.ritual;

import dragthings.Dragthings;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crafting ritual: kéo crafting table gần đúng nguyên liệu của một
 * shapeless recipe → tự động craft, spawn kết quả tại vị trí table.
 *
 * Chỉ hỗ trợ ShapelessRecipe vì ta không có grid để xác định
 * layout của ShapedRecipe. Shaped support có thể thêm sau nếu cần.
 *
 * Matching algorithm:
 *   - Duyệt từng ShapelessRecipe trong RecipeManager
 *   - Với mỗi recipe: thử tìm một tập con items nearby đủ để
 *     satisfy tất cả ingredients (greedy, 1 ItemEntity per ingredient slot)
 *   - Nếu match: shrink từng nguyên liệu đúng 1, spawn result
 */
public final class CraftingRitual {

    // FIX: was Map<Integer, ServerLevel> — no player reference meant no way
    // to detect the dragging player disconnecting, so this entry (and the
    // ritual's tick logic) leaked forever once a player left mid-drag. Made
    // worse now that dragged items never despawn (setUnlimitedLifetime), so
    // the old 5-minute despawn was quietly acting as a failsafe cleanup —
    // that failsafe is gone, so this needs its own real cleanup now.
    // Level is derived from player.level() instead of storing it separately.
    private static final Map<Integer, ServerPlayer> activeTables = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer>      cooldown     = new ConcurrentHashMap<>();

    // GUI-set target item for "drag the table" mode — set via
    // SetCraftingTargetScreen (isBlockTarget=false), read by tryCraft()
    // as a fallback when the off-hand is empty. Cleared on release so a
    // stale target doesn't silently carry over to a completely different
    // table picked up later (tables aren't otherwise individually
    // identified beyond their transient entity ID).
    private static final Map<Integer, net.minecraft.world.item.Item> guiTargets = new ConcurrentHashMap<>();

    private static final double RADIUS         = 1;
    private static final int    COOLDOWN_TICKS = 20;

    private CraftingRitual() {}

    /** Called by the SetCraftingTargetPayload network handler. */
    public static void setGuiTarget(int entityId, net.minecraft.world.item.Item item) {
        guiTargets.put(entityId, item);
    }

    // ── Public API ────────────────────────────────────────────────────────

    public static void onStartDrag(int entityId, ServerLevel level, ServerPlayer player) {
        boolean isNew = !activeTables.containsKey(entityId);
        activeTables.put(entityId, player);
        if (isNew) {
            var e = level.getEntity(entityId);
            if (e instanceof ItemEntity table) {
                // Gentle wood knock — crafting table being picked up
                level.playSound(null, table.blockPosition(),
                        SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.4f, 0.7f);
            }
        }
    }

    public static void onRelease(int entityId) {
        activeTables.remove(entityId);
        cooldown.remove(entityId);
        guiTargets.remove(entityId);
    }

    public static void tick() {
        cooldown.replaceAll((id, ticks) -> ticks - 1);
        cooldown.values().removeIf(ticks -> ticks <= 0);

        if (activeTables.isEmpty()) return;

        Iterator<Map.Entry<Integer, ServerPlayer>> it = activeTables.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ServerPlayer> entry = it.next();
            int          tableId = entry.getKey();
            ServerPlayer player  = entry.getValue();

            // FIX: player disconnected mid-drag — stop tracking, nothing to
            // craft for. Without this check the entry (and its cooldown)
            // lived forever.
            if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) {
                it.remove();
                cooldown.remove(tableId);
                continue;
            }

            var e = level.getEntity(tableId);
            if (!(e instanceof ItemEntity table) || !table.isAlive()) {
                it.remove();
                cooldown.remove(tableId);
                continue;
            }

            if (!cooldown.containsKey(tableId) && tryCraft(level, table, player)) {
                cooldown.put(tableId, COOLDOWN_TICKS);
            }
        }
    }

    // ── Core logic ────────────────────────────────────────────────────────

    /**
     * If the player is holding an item in their OFF-HAND, that item is
     * treated as a "template" — instead of scanning every ShapelessRecipe
     * for whatever nearby items happen to satisfy, we look up the specific
     * recipe(s) that produce that exact item and try to gather ITS
     * ingredients from nearby entities. This works for ShapedRecipe too
     * (something the radius-based search below can't do), since we already
     * know exactly which recipe we're building — no grid/position matching
     * needed at all, just "do we have one of everything this recipe wants
     * nearby". Off-hand empty → falls back to the original behavior.
     */
    private static boolean tryCraft(ServerLevel level, ItemEntity table, ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            return tryCraftTemplate(level, table, offhand.getItem());
        }
        net.minecraft.world.item.Item guiTarget = guiTargets.get(table.getId());
        if (guiTarget != null) {
            return tryCraftTemplate(level, table, guiTarget);
        }
        return tryCraftAnyShapeless(level, table);
    }

    /**
     * Template mode: craft the SPECIFIC recipe that produces {@code targetItem},
     * regardless of Shaped/Shapeless — see tryCraft() doc above for why this
     * sidesteps the whole shape-matching problem.
     *
     * NOTE: uses recipe.getResultItem() directly rather than building a real
     * positional CraftingInput and calling assemble(). This is correct for
     * the vast majority of recipes (tools, weapons, armor, blocks — a fixed
     * result stack), but recipes whose assemble() logic depends on the
     * actual input stacks (repairing, dyeing leather armor, banner patterns,
     * firework star mixing) won't get that extra behavior here — an
     * accepted simplification for this ritual mechanic.
     */
    private static boolean tryCraftTemplate(ServerLevel level, ItemEntity table,
                                            net.minecraft.world.item.Item targetItem) {
        AABB box = new AABB(table.position(), table.position()).inflate(RADIUS);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != table && i.isAlive() && !i.getItem().isEmpty()
                        && !i.hasPickUpDelay());
        if (nearby.isEmpty()) return false;

        for (RecipeHolder<CraftingRecipe> holder :
                level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {

            CraftingRecipe recipe = holder.value();
            ItemStack recipeResult = recipe.getResultItem(level.registryAccess());
            if (!recipeResult.is(targetItem)) continue;

            // getIngredients() is a flat list for BOTH ShapedRecipe and
            // ShapelessRecipe — shape only matters for real grid placement,
            // which we're deliberately not doing here.
            List<net.minecraft.world.item.crafting.Ingredient> ingredients = recipe.getIngredients();
            if (ingredients.isEmpty() || ingredients.size() > nearby.size()) continue;

            int[] claimed = new int[ingredients.size()];
            if (!matchIngredients(ingredients, nearby, claimed)) continue;

            ItemStack result = recipeResult.copy();
            if (result.isEmpty()) continue;

            for (int ci : claimed) {
                RitualUtil.shrinkOrDiscard(nearby.get(ci), 1);
            }
            RitualUtil.spawnResult(level, table, result);

            level.playSound(null, table.blockPosition(),
                    SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.9f, 1.1f);
            level.playSound(null, table.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.4f);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    table.getX(), table.getY() + 0.4, table.getZ(),
                    12, 0.3, 0.2, 0.3, 0.0);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    table.getX(), table.getY() + 0.3, table.getZ(),
                    8, 0.25, 0.2, 0.25, 0.02);

            Dragthings.LOGGER.info("Crafting ritual (template): {} (table #{})",
                    result.getHoverName().getString(), table.getId());
            return true;
        }
        return false;
    }

    private static boolean tryCraftAnyShapeless(ServerLevel level, ItemEntity table) {
        AABB box = new AABB(table.position(), table.position()).inflate(RADIUS);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != table && i.isAlive() && !i.getItem().isEmpty()
                        && !i.hasPickUpDelay()); // skip freshly-spawned results

        if (nearby.isEmpty()) return false;

        // Filter to only ShapelessRecipe using instanceof — avoids the unchecked
        // cast that comes from trying to parameterize the recipe holder directly.
        for (RecipeHolder<CraftingRecipe> holder :
                level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {

            if (!(holder.value() instanceof ShapelessRecipe recipe)) continue;

            List<net.minecraft.world.item.crafting.Ingredient> ingredients =
                    recipe.getIngredients();

            if (ingredients.isEmpty() || ingredients.size() > nearby.size()) continue;

            // Try to match each ingredient slot to a distinct ItemEntity nearby.
            int[] claimed = new int[ingredients.size()];
            if (!matchIngredients(ingredients, nearby, claimed)) continue;

            // Build a CraftingInput to let the recipe verify the combination
            // (handles tag-based ingredients, custom conditions, etc.)
            List<ItemStack> inputStacks = new ArrayList<>();
            for (int ci : claimed) inputStacks.add(nearby.get(ci).getItem().copyWithCount(1));
            CraftingInput input = CraftingInput.of(inputStacks.size(), 1, inputStacks);
            if (!recipe.matches(input, level)) continue;

            ItemStack result = recipe.assemble(input, level.registryAccess());
            if (result.isEmpty()) continue;

            // Preserve recipe result count
            result.setCount(recipe.getResultItem(level.registryAccess()).getCount());

            // Consume exactly 1 from each claimed entity
            for (int ci : claimed) {
                RitualUtil.shrinkOrDiscard(nearby.get(ci), 1);
            }

            // Spawn the crafted item at the table position
            RitualUtil.spawnResult(level, table, result);

            // ── Effects ───────────────────────────────────────────────
            level.playSound(null, table.blockPosition(),
                    SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.9f, 1.1f);
            level.playSound(null, table.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.4f);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    table.getX(), table.getY() + 0.4, table.getZ(),
                    12, 0.3, 0.2, 0.3, 0.0);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    table.getX(), table.getY() + 0.3, table.getZ(),
                    8, 0.25, 0.2, 0.25, 0.02);

            Dragthings.LOGGER.info("Crafting ritual: {} (table #{})",
                    result.getHoverName().getString(), table.getId());
            return true;
        }

        return false;
    }

    /**
     * Greedy ingredient→entity matching.
     *
     * For each ingredient (in order), scan nearby items for an unclaimed
     * entity that satisfies it. Because shapeless recipes don't impose
     * ordering, this greedy pass is sufficient for all vanilla cases.
     *
     * Returns true if every ingredient was matched; fills {@code claimed}
     * with the index into {@code nearby} for each ingredient slot.
     */
    private static boolean matchIngredients(
            List<net.minecraft.world.item.crafting.Ingredient> ingredients,
            List<ItemEntity> nearby,
            int[] claimed) {

        boolean[] used = new boolean[nearby.size()];

        for (int slot = 0; slot < ingredients.size(); slot++) {
            net.minecraft.world.item.crafting.Ingredient ing = ingredients.get(slot);
            boolean found = false;
            for (int ni = 0; ni < nearby.size(); ni++) {
                if (used[ni]) continue;
                if (ing.test(nearby.get(ni).getItem())) {
                    claimed[slot] = ni;
                    used[ni] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}