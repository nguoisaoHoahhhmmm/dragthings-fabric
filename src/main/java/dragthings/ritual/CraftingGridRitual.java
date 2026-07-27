package dragthings.ritual;

import dragthings.Dragthings;
import dragthings.network.CraftingProgressPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Placed-crafting-table ritual — two modes, depending on whether a target
 * has been set for that table via SetCraftingTargetScreen:
 *
 *   NO target set -> "precise grid" mode: drag + sneak snaps the item into
 *   one of 9 virtual slots above the table; a real CraftingInput.of(3,3,..)
 *   is built from the actual slot layout, so RecipeManager#getRecipeFor
 *   matches BOTH ShapedRecipe and ShapelessRecipe -- same lookup a real
 *   crafting table GUI uses. Precise but fiddly to aim.
 *
 *   Target SET -> "touch and gather" mode: drag + sneak + get the item
 *   close enough to the table and it's immediately consumed into a running
 *   per-table tally (Map&lt;Item,Integer&gt;), no precise positioning needed at
 *   all. Once the tally can satisfy the target recipe's ingredients, it
 *   crafts automatically -- repeatedly, as ingredients keep arriving. Simple
 *   at the cost of not validating ShapedRecipe layout (irrelevant here
 *   anyway, since we already know exactly which recipe we're building).
 *
 * Keyed by GlobalPos (dimension + BlockPos) rather than plain BlockPos --
 * two different dimensions can have a table at the same block coordinates,
 * and a bare BlockPos key would incorrectly conflate them.
 */
public final class CraftingGridRitual {

    private static final double CAPTURE_RADIUS = 1.2;
    private static final double TOUCH_RADIUS   = 0.9;  // "touch and gather" mode consumption distance
    private static final double SLOT_SPACING   = 0.3;
    private static final double SLOT_SNAP_DIST = 0.28; // how close counts as "aiming for this slot"

    private static final class GridEntry {
        final ServerLevel level;
        final int[] slots = emptyGrid(); // entity ID per slot, -1 = empty; index = row*3+col
        GridEntry(ServerLevel l) { level = l; }
    }

    private static final Map<GlobalPos, GridEntry> grids      = new ConcurrentHashMap<>();
    private static final Map<Integer, GlobalPos>   capturedBy = new ConcurrentHashMap<>();

    // "Touch and gather" mode state, only used once a target is set for a table.
    private static final Map<GlobalPos, Item>              blockTargets = new ConcurrentHashMap<>();
    private static final Map<GlobalPos, Map<Item, Integer>> gathered     = new ConcurrentHashMap<>();

    private CraftingGridRitual() {}

    /**
     * Called by the SetCraftingTargetPayload network handler. Resets any
     * prior gathering progress for this table (a changed target shouldn't
     * silently keep old tallies around) and immediately pushes a 0-progress
     * update so the player sees requirements right away.
     */
    public static void setGuiTarget(ServerLevel level, ServerPlayer player, GlobalPos tablePos, Item item) {
        blockTargets.put(tablePos, item);
        gathered.remove(tablePos);

        findRecipeFor(level, item).ifPresent(holder ->
                sendProgress(level, player, tablePos.pos(), item, holder.value().getIngredients(), new HashMap<>()));
    }

    public static boolean isCaptured(int entityId) {
        return capturedBy.containsKey(entityId);
    }

    /**
     * Single entry point called every dragging tick for whichever item is
     * being dragged (leader or follower), BEFORE the normal position-sync
     * in Dragthings.java runs. Returns true if this ritual handled the item
     * this tick (caller should skip its own item.setPos() -- either the grid
     * owns its position, or the item was just consumed/discarded).
     */
    public static boolean tryHandleDrag(ServerLevel level, ServerPlayer player, ItemEntity item,
                                        Vec3 targetPos, boolean sneaking) {
        int entityId = item.getId();

        if (!sneaking) {
            release(entityId);
            return false;
        }

        if (capturedBy.containsKey(entityId)) {
            return true; // precise-grid mode already owns this item's position
        }

        BlockPos tablePos = findNearbyTable(level, targetPos);
        if (tablePos == null) return false;

        GlobalPos key = GlobalPos.of(level.dimension(), tablePos);
        Item target = blockTargets.get(key);

        if (target != null) {
            return tryTouchConsume(level, player, item, targetPos, tablePos, key, target);
        }
        return tryCaptureGrid(level, item, targetPos, tablePos, key);
    }

    /**
     * Shows a lightweight particle marker at every EMPTY slot of the
     * precise-grid layout -- only meaningful when no target is set (touch
     * mode doesn't have fixed slots to mark). Sent only to the given
     * player, throttled to avoid flooding the connection.
     */
    public static void showGridPreview(ServerLevel level, ServerPlayer player, Vec3 aimPos, boolean sneaking) {
        if (!sneaking) return;
        if (level.getGameTime() % 5 != 0) return;

        BlockPos tablePos = findNearbyTable(level, aimPos);
        if (tablePos == null) return;

        GlobalPos key = GlobalPos.of(level.dimension(), tablePos);
        if (blockTargets.containsKey(key)) return; // touch mode -- no slots to preview

        GridEntry grid = grids.get(key);

        for (int i = 0; i < 9; i++) {
            boolean occupied = grid != null && grid.slots[i] != -1;
            if (occupied) continue;

            Vec3 slotPos = slotWorldPos(tablePos, i);
            level.sendParticles(player, ParticleTypes.END_ROD, false,
                    slotPos.x, slotPos.y, slotPos.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // -- Precise-grid mode ----------------------------------------------

    private static boolean tryCaptureGrid(ServerLevel level, ItemEntity item, Vec3 targetPos,
                                          BlockPos tablePos, GlobalPos key) {
        GridEntry grid = grids.computeIfAbsent(key, k -> new GridEntry(level));

        int bestSlot = -1;
        double bestDist = SLOT_SNAP_DIST;
        for (int i = 0; i < 9; i++) {
            if (grid.slots[i] != -1) continue;
            Vec3 slotPos = slotWorldPos(tablePos, i);
            double dist = slotPos.distanceTo(targetPos);
            if (dist < bestDist) { bestDist = dist; bestSlot = i; }
        }
        if (bestSlot == -1) return false;

        int entityId = item.getId();
        grid.slots[bestSlot] = entityId;
        capturedBy.put(entityId, key);

        item.setNoGravity(true);
        item.noPhysics = true;
        item.setDeltaMovement(Vec3.ZERO);

        Vec3 slotPos = slotWorldPos(tablePos, bestSlot);
        level.playSound(null, tablePos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.5f, 1.3f);
        level.sendParticles(ParticleTypes.CRIT, slotPos.x, slotPos.y, slotPos.z, 6, 0.08, 0.08, 0.08, 0.02);

        checkGridRecipe(key, grid);
        return true;
    }

    private static void release(int entityId) {
        GlobalPos key = capturedBy.remove(entityId);
        if (key == null) return;
        GridEntry grid = grids.get(key);
        if (grid != null) {
            for (int i = 0; i < 9; i++) if (grid.slots[i] == entityId) grid.slots[i] = -1;
        }
    }

    /** Called once per server tick -- holds every grid-captured item in its locked slot, and cleans up. */
    public static void tick() {
        if (grids.isEmpty()) return;

        Iterator<Map.Entry<GlobalPos, GridEntry>> it = grids.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<GlobalPos, GridEntry> entry = it.next();
            GlobalPos tableKey  = entry.getKey();
            GridEntry grid      = entry.getValue();
            ServerLevel level   = grid.level;
            BlockPos   tablePos = tableKey.pos();

            if (!level.getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) {
                for (int slotId : grid.slots) {
                    if (slotId == -1) continue;
                    capturedBy.remove(slotId);
                    var e = level.getEntity(slotId);
                    if (e instanceof ItemEntity ie) {
                        ie.setNoGravity(false);
                        ie.noPhysics = false;
                    }
                }
                it.remove();
                continue;
            }

            boolean anyOccupied = false;
            for (int i = 0; i < 9; i++) {
                int entityId = grid.slots[i];
                if (entityId == -1) continue;

                var e = level.getEntity(entityId);
                if (!(e instanceof ItemEntity ie) || !ie.isAlive()) {
                    grid.slots[i] = -1;
                    capturedBy.remove(entityId);
                    continue;
                }

                anyOccupied = true;
                Vec3 pos = slotWorldPos(tablePos, i);
                ie.setPos(pos.x, pos.y, pos.z);
                ie.setDeltaMovement(Vec3.ZERO);
                ie.setNoGravity(true);
                ie.noPhysics = true;
            }

            if (!anyOccupied) {
                it.remove();
            }
        }
    }

    private static void checkGridRecipe(GlobalPos key, GridEntry grid) {
        ServerLevel level = grid.level;
        BlockPos tablePos = key.pos();

        List<ItemStack> gridStacks = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            int entityId = grid.slots[i];
            var e = entityId == -1 ? null : level.getEntity(entityId);
            gridStacks.add(e instanceof ItemEntity ie ? ie.getItem().copyWithCount(1) : ItemStack.EMPTY);
        }

        CraftingInput input = CraftingInput.of(3, 3, gridStacks);
        Optional<RecipeHolder<CraftingRecipe>> match =
                level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (match.isEmpty()) return;

        CraftingRecipe recipe = match.get().value();
        ItemStack result = recipe.assemble(input, level.registryAccess());
        if (result.isEmpty()) return;
        result.setCount(recipe.getResultItem(level.registryAccess()).getCount());

        for (int i = 0; i < 9; i++) {
            int entityId = grid.slots[i];
            if (entityId == -1) continue;
            var e = level.getEntity(entityId);
            if (e instanceof ItemEntity ie) {
                RitualUtil.shrinkOrDiscard(ie, 1);
            }
            capturedBy.remove(entityId);
            grid.slots[i] = -1;
        }

        spawnResultAt(level, tablePos, result);
        Dragthings.LOGGER.info("Crafting grid ritual (precise mode): {} at {}",
                result.getHoverName().getString(), tablePos);
    }

    // -- Touch-and-gather mode --------------------------------------------

    private static boolean tryTouchConsume(ServerLevel level, ServerPlayer player, ItemEntity item,
                                           Vec3 targetPos, BlockPos tablePos, GlobalPos key, Item target) {
        Vec3 tableCenter = new Vec3(tablePos.getX() + 0.5, tablePos.getY() + 1.0, tablePos.getZ() + 0.5);
        if (targetPos.distanceTo(tableCenter) > TOUCH_RADIUS) return false;

        Optional<RecipeHolder<CraftingRecipe>> recipeOpt = findRecipeFor(level, target);
        if (recipeOpt.isEmpty()) return false;
        List<Ingredient> ingredients = recipeOpt.get().value().getIngredients();

        ItemStack stack = item.getItem();
        // Only consume if this item is actually useful for the recipe -- an
        // unrelated item wandering close to the table shouldn't just vanish.
        boolean useful = ingredients.stream().anyMatch(ing -> ing.test(stack));
        if (!useful) return false;

        Map<Item, Integer> gatheredMap = gathered.computeIfAbsent(key, k -> new HashMap<>());
        gatheredMap.merge(stack.getItem(), stack.getCount(), Integer::sum);
        item.discard();

        level.playSound(null, tablePos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.3f, 1.2f);
        level.sendParticles(ParticleTypes.CRIT, tableCenter.x, tableCenter.y, tableCenter.z,
                4, 0.15, 0.1, 0.15, 0.02);

        tryCraftFromGathered(level, tablePos, key, target, ingredients);
        sendProgress(level, player, tablePos, target, ingredients, gathered.getOrDefault(key, new HashMap<>()));
        return true;
    }

    /**
     * Greedy check-and-consume: for each ingredient predicate, find a
     * gathered item type with remaining count that satisfies it. If every
     * ingredient can be satisfied, actually subtracts the consumed amounts
     * from gatheredMap and returns true; otherwise leaves it untouched and
     * returns false. Any excess gathered beyond what one craft needs simply
     * stays in the tally for the next attempt.
     */
    private static boolean tryConsumeForRecipe(List<Ingredient> ingredients, Map<Item, Integer> gatheredMap) {
        Map<Item, Integer> remaining = new HashMap<>(gatheredMap);
        Map<Item, Integer> toRemove  = new HashMap<>();

        for (Ingredient ing : ingredients) {
            Item found = null;
            for (Map.Entry<Item, Integer> e : remaining.entrySet()) {
                if (e.getValue() > 0 && ing.test(new ItemStack(e.getKey()))) {
                    found = e.getKey();
                    break;
                }
            }
            if (found == null) return false;
            remaining.merge(found, -1, Integer::sum);
            toRemove.merge(found, 1, Integer::sum);
        }

        for (Map.Entry<Item, Integer> e : toRemove.entrySet()) {
            gatheredMap.merge(e.getKey(), -e.getValue(), Integer::sum);
        }
        gatheredMap.values().removeIf(v -> v <= 0);
        return true;
    }

    private static void tryCraftFromGathered(ServerLevel level, BlockPos tablePos, GlobalPos key,
                                             Item target, List<Ingredient> ingredients) {
        Map<Item, Integer> gatheredMap = gathered.get(key);
        if (gatheredMap == null) return;
        if (!tryConsumeForRecipe(ingredients, gatheredMap)) return;

        Optional<RecipeHolder<CraftingRecipe>> recipeOpt = findRecipeFor(level, target);
        if (recipeOpt.isEmpty()) return;

        // NOTE: uses getResultItem() directly rather than a real positional
        // CraftingInput + assemble() -- same simplification (and same
        // caveat for component-dependent recipes) as CraftingRitual's
        // off-hand template mode. See that class's doc comment for details.
        ItemStack result = recipeOpt.get().value().getResultItem(level.registryAccess()).copy();
        if (result.isEmpty()) return;

        spawnResultAt(level, tablePos, result);
        Dragthings.LOGGER.info("Crafting grid ritual (touch mode): {} at {}",
                result.getHoverName().getString(), tablePos);
    }

    private static void sendProgress(ServerLevel level, ServerPlayer player, BlockPos tablePos,
                                     Item target, List<Ingredient> ingredients, Map<Item, Integer> gatheredMap) {
        if (!ServerPlayNetworking.canSend(player, CraftingProgressPayload.TYPE)) return;

        // Best-effort display grouping: for each ingredient slot, attribute
        // it to whichever already-gathered item type satisfies it (so
        // progress reads in terms of what the player is actually holding),
        // falling back to the ingredient's own first valid item if nothing
        // gathered yet matches. This is purely cosmetic -- tryConsumeForRecipe
        // above is what actually decides craftability.
        Map<Item, Integer> neededMap = new HashMap<>();
        for (Ingredient ing : ingredients) {
            Item matched = null;
            for (Item gatheredItem : gatheredMap.keySet()) {
                if (ing.test(new ItemStack(gatheredItem))) { matched = gatheredItem; break; }
            }
            if (matched == null) {
                ItemStack[] options = ing.getItems();
                if (options.length > 0) matched = options[0].getItem();
            }
            if (matched != null) neededMap.merge(matched, 1, Integer::sum);
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : neededMap.entrySet()) {
            int have = gatheredMap.getOrDefault(e.getKey(), 0);
            int need = e.getValue();
            lines.add(e.getKey().getDescription().getString() + ": " + Math.min(have, need) + "/" + need);
        }

        String joined = String.join("\n", lines);
        ServerPlayNetworking.send(player, new CraftingProgressPayload(
                tablePos, target.getDescription().getString(), joined));
    }

    // -- Shared helpers ---------------------------------------------------

    private static Optional<RecipeHolder<CraftingRecipe>> findRecipeFor(ServerLevel level, Item target) {
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (holder.value().getResultItem(level.registryAccess()).is(target)) {
                return Optional.of(holder);
            }
        }
        return Optional.empty();
    }

    private static void spawnResultAt(ServerLevel level, BlockPos tablePos, ItemStack result) {
        Vec3 spawnAt = new Vec3(tablePos.getX() + 0.5, tablePos.getY() + 1.2, tablePos.getZ() + 0.5);
        ItemEntity resultEntity = new ItemEntity(level, spawnAt.x, spawnAt.y, spawnAt.z, result);
        resultEntity.setDeltaMovement(0, 0.15, 0);
        level.addFreshEntity(resultEntity);

        level.playSound(null, tablePos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.9f, 1.1f);
        level.playSound(null, tablePos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.4f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                spawnAt.x, spawnAt.y - 0.1, spawnAt.z, 12, 0.3, 0.2, 0.3, 0.0);
    }

    private static int[] emptyGrid() {
        int[] g = new int[9];
        Arrays.fill(g, -1);
        return g;
    }

    /** World-space center of the given 0-8 grid slot, floating just above the table's top face. */
    private static Vec3 slotWorldPos(BlockPos tablePos, int slot) {
        int row = slot / 3;
        int col = slot % 3;
        double offsetX = (col - 1) * SLOT_SPACING;
        double offsetZ = (row - 1) * SLOT_SPACING;
        return new Vec3(
                tablePos.getX() + 0.5 + offsetX,
                tablePos.getY() + 1.05,
                tablePos.getZ() + 0.5 + offsetZ);
    }

    private static BlockPos findNearbyTable(ServerLevel level, Vec3 pos) {
        BlockPos center = BlockPos.containing(pos);
        BlockPos best = null;
        double bestDist = CAPTURE_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            if (!level.getBlockState(p).is(Blocks.CRAFTING_TABLE)) continue;
            double dist = pos.distanceTo(new Vec3(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5));
            if (dist < bestDist) { bestDist = dist; best = p.immutable(); }
        }
        return best;
    }
}