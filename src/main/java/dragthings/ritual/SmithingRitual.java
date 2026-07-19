package dragthings.ritual;

import dragthings.Dragthings;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SmithingRitual {

    private static final Map<Integer, ServerLevel> activeTables = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer>     cooldown     = new ConcurrentHashMap<>();
    private static final double RADIUS        = 1;
    private static final int    COOLDOWN_TICKS = 20;

    private SmithingRitual() {}

    public static void onStartDrag(int entityId, ServerLevel level) {
        boolean isNew = !activeTables.containsKey(entityId);
        activeTables.put(entityId, level);
        // Play start sound only on the first registration, not on every sync packet.
        if (isNew) {
            var e = level.getEntity(entityId);
            if (e instanceof ItemEntity table) {
                level.playSound(null, table.blockPosition(),
                        SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4f, 0.6f);
            }
        }
    }
    public static void onRelease(int entityId) { activeTables.remove(entityId); cooldown.remove(entityId); }

    public static void tick() {
        cooldown.replaceAll((id, ticks) -> ticks - 1);
        cooldown.values().removeIf(ticks -> ticks <= 0);

        if (activeTables.isEmpty()) return;
        Iterator<Map.Entry<Integer, ServerLevel>> it = activeTables.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ServerLevel> entry = it.next();
            int tableId = entry.getKey();
            ServerLevel level = entry.getValue();

            var e = level.getEntity(tableId);
            if (!(e instanceof ItemEntity table) || !table.isAlive()) { it.remove(); cooldown.remove(tableId); continue; }

            if (!cooldown.containsKey(tableId) && trySmith(level, table))
                cooldown.put(tableId, COOLDOWN_TICKS);
        }
    }

    private static boolean trySmith(ServerLevel level, ItemEntity table) {
        AABB box = new AABB(table.position(), table.position()).inflate(RADIUS);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != table && i.isAlive() && !i.getItem().isEmpty());
        if (nearby.size() < 3) return false;

        for (RecipeHolder<SmithingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING)) {
            SmithingRecipe recipe = holder.value();
            if (recipe.isIncomplete()) continue;
            for (ItemEntity tE : nearby) {
                if (!recipe.isTemplateIngredient(tE.getItem())) continue;
                for (ItemEntity bE : nearby) {
                    if (bE == tE || !recipe.isBaseIngredient(bE.getItem())) continue;
                    for (ItemEntity aE : nearby) {
                        if (aE == tE || aE == bE || !recipe.isAdditionIngredient(aE.getItem())) continue;
                        SmithingRecipeInput input = new SmithingRecipeInput(
                                tE.getItem().copyWithCount(1), bE.getItem().copyWithCount(1), aE.getItem().copyWithCount(1));
                        if (!recipe.matches(input, level)) continue;
                        ItemStack result = recipe.assemble(input, level.registryAccess());
                        if (result.isEmpty()) continue;

                        RitualUtil.shrinkOrDiscard(tE, 1); RitualUtil.shrinkOrDiscard(aE, 1);
                        if (bE.getItem().getCount() <= 1) { bE.setItem(result.copy()); bE.setPickUpDelay(20); }
                        else { RitualUtil.shrinkOrDiscard(bE, 1); RitualUtil.spawnResult(level, table, result); }

                        level.playSound(null, table.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1f, 1f);
                        // Metallic "ding" to signal completion, pitched up for a satisfying finish
                        level.playSound(null, table.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.25f, 2.0f);
                        level.playSound(null, table.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.6f);
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, table.getX(), table.getY()+0.3, table.getZ(), 24, 0.35, 0.25, 0.35, 0.02);
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, table.getX(), table.getY()+0.5, table.getZ(), 8, 0.25, 0.25, 0.25, 0.0);
                        Dragthings.LOGGER.info("Smithing ritual: {} (table #{})", result.getHoverName().getString(), table.getId());
                        return true;
                    }
                }
            }
        }
        return false;
    }
}