package dragthings.ritual;

import dragthings.Dragthings;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Furnace ritual: drag a furnace/blast furnace/smoker item, consume fuel to enter
 * "burning" state, then gradually cook nearby ingredients with progress tracking.
 */
public final class FurnaceRitual {

    /** Tracks the burning state of an active furnace. */
    private static final class BurningState {
        final ServerLevel level;
        final FurnaceType type;
        int burnTimeLeft;  // ticks of fuel remaining
        int particleTicker = 0;

        BurningState(ServerLevel l, FurnaceType t, int burnTicks) {
            level = l; type = t; burnTimeLeft = burnTicks;
        }
    }

    /** Tracks cooking progress for a specific item entity. */
    private static final class CookingProgress {
        int cookTime = 0;
        int cookTimeTotal;
        CookingProgress(int total) { cookTimeTotal = total; }
    }

    /** Maps vanilla furnace items to the correct recipe type and cook speed. */
    public enum FurnaceType {
        FURNACE   (RecipeType.SMELTING,  200, SoundEvents.FURNACE_FIRE_CRACKLE),
        BLASTING  (RecipeType.BLASTING,  100, SoundEvents.BLASTFURNACE_FIRE_CRACKLE),
        SMOKING   (RecipeType.SMOKING,   100, SoundEvents.SMOKER_SMOKE);

        final RecipeType<? extends AbstractCookingRecipe> recipeType;
        final int defaultCookTime; // ticks to cook (vanilla: 200 for furnace, 100 for blast/smoker)
        final net.minecraft.sounds.SoundEvent sound;

        FurnaceType(RecipeType<? extends AbstractCookingRecipe> r, int cook, net.minecraft.sounds.SoundEvent s) {
            recipeType = r; defaultCookTime = cook; sound = s;
        }
    }

    private static final Map<Integer, BurningState>   activeFurnaces  = new ConcurrentHashMap<>();
    private static final Map<Integer, CookingProgress> cookingItems   = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer>        fuelSearchCooldown = new ConcurrentHashMap<>();

    private static final double RADIUS                = 2.0;
    private static final int    FUEL_SEARCH_COOLDOWN  = 10;  // ticks between fuel checks
    private static final int    PARTICLE_INTERVAL     = 3;   // spawn particles every N ticks

    private FurnaceRitual() {}

    public static void onStartDrag(int entityId, ServerLevel level) {
        // Only register if not already burning; preserve existing burn state on re-drag
        if (!activeFurnaces.containsKey(entityId)) {
            FurnaceType type = detectType(getStack(level, entityId));
            if (type != null) activeFurnaces.put(entityId, new BurningState(level, type, 0));
        }
        // Play a distinct "picked up" sound for each furnace variant
        var e = level.getEntity(entityId);
        if (e instanceof ItemEntity furnace) {
            FurnaceType type = detectType(furnace.getItem());
            if (type != null) {
                net.minecraft.sounds.SoundEvent startSound = switch (type) {
                    case FURNACE  -> SoundEvents.FURNACE_FIRE_CRACKLE;
                    case BLASTING -> SoundEvents.BLASTFURNACE_FIRE_CRACKLE;
                    case SMOKING  -> SoundEvents.SMOKER_SMOKE;
                };
                level.playSound(null, furnace.blockPosition(),
                        startSound, SoundSource.BLOCKS, 0.35f, 0.55f);
            }
        }
    }

    public static void onRelease(int entityId) {
        activeFurnaces.remove(entityId);
        fuelSearchCooldown.remove(entityId);
        // Cooking progress is intentionally preserved — item remains mid-cook in the world
    }

    public static void tick() {
        fuelSearchCooldown.replaceAll((id, t) -> t - 1);
        fuelSearchCooldown.values().removeIf(t -> t <= 0);

        if (activeFurnaces.isEmpty()) return;

        Iterator<Map.Entry<Integer, BurningState>> it = activeFurnaces.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, BurningState> entry = it.next();
            int furnaceId = entry.getKey();
            BurningState state = entry.getValue();
            ServerLevel level = state.level;

            var ent = level.getEntity(furnaceId);
            if (!(ent instanceof ItemEntity furnace) || !furnace.isAlive()) {
                it.remove();
                fuelSearchCooldown.remove(furnaceId);
                continue;
            }

            FurnaceType type = detectType(furnace.getItem());
            if (type == null) { it.remove(); fuelSearchCooldown.remove(furnaceId); continue; }

            // ── Fuel phase ──────────────────────────────────────────────
            if (state.burnTimeLeft <= 0) {
                // Not burning — try to consume fuel if cooldown elapsed
                if (!fuelSearchCooldown.containsKey(furnaceId)) {
                    boolean consumed = tryConsumeFuel(level, furnace, state);
                    if (!consumed) {
                        // Show "cold" puff of smoke to signal waiting for fuel
                        if (++state.particleTicker % 20 == 0) {
                            level.sendParticles(ParticleTypes.SMOKE,
                                    furnace.getX(), furnace.getY() + 0.3, furnace.getZ(),
                                    3, 0.1, 0.1, 0.1, 0.005);
                        }
                        fuelSearchCooldown.put(furnaceId, FUEL_SEARCH_COOLDOWN);
                        continue; // no fuel, skip cooking this tick
                    }
                } else {
                    continue; // waiting before next fuel check
                }
            }

            // ── Burning phase ────────────────────────────────────────────
            state.burnTimeLeft--;

            // Emit flame + smoke particles while burning
            if (++state.particleTicker % PARTICLE_INTERVAL == 0) {
                level.sendParticles(ParticleTypes.FLAME,
                        furnace.getX(), furnace.getY() + 0.25, furnace.getZ(),
                        2, 0.08, 0.08, 0.08, 0.005);
                level.sendParticles(ParticleTypes.SMOKE,
                        furnace.getX(), furnace.getY() + 0.35, furnace.getZ(),
                        1, 0.05, 0.05, 0.05, 0.003);
            }

            // ── Cooking phase ────────────────────────────────────────────
            AABB box = new AABB(furnace.position(), furnace.position()).inflate(RADIUS);
            List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                    i -> i != furnace && i.isAlive() && !i.getItem().isEmpty());

            for (ItemEntity ingredient : nearby) {
                tickCooking(level, furnace, ingredient, type);
            }
        }
    }

    // ── Cooking logic ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void tickCooking(ServerLevel level, ItemEntity furnace,
                                     ItemEntity ingredient, FurnaceType type) {
        ItemStack input = ingredient.getItem();
        RecipeType<AbstractCookingRecipe> recipeType = (RecipeType<AbstractCookingRecipe>) type.recipeType;

        SingleRecipeInput recipeInput = new SingleRecipeInput(input.copyWithCount(1));
        Optional<RecipeHolder<AbstractCookingRecipe>> match =
                level.getRecipeManager().getRecipeFor(recipeType, recipeInput, level);
        if (match.isEmpty()) return;

        AbstractCookingRecipe recipe = match.get().value();
        int cookTotal = recipe.getCookingTime();

        // Get or create progress tracker for this ingredient entity
        CookingProgress progress = cookingItems.computeIfAbsent(
                ingredient.getId(), id -> new CookingProgress(cookTotal));

        // If recipe changed (different item), reset
        if (progress.cookTimeTotal != cookTotal) progress.cookTime = 0;

        progress.cookTime++;

        // Sparkle particle toward the furnace as cooking progresses
        if (progress.cookTime % 8 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    ingredient.getX(), ingredient.getY() + 0.15, ingredient.getZ(),
                    2, 0.06, 0.06, 0.06, 0.01);
        }

        if (progress.cookTime >= progress.cookTimeTotal) {
            // Cooking complete!
            cookingItems.remove(ingredient.getId());
            ItemStack result = recipe.assemble(recipeInput, level.registryAccess());
            if (result.isEmpty()) return;

            if (input.getCount() <= 1) {
                ingredient.setItem(result.copy());
                ingredient.setPickUpDelay(20);
            } else {
                RitualUtil.shrinkOrDiscard(ingredient, 1);
                RitualUtil.spawnResult(level, furnace, result);
            }

            level.playSound(null, furnace.blockPosition(), type.sound, SoundSource.BLOCKS, 0.8f, 1.2f);
            // Soft ding to signal the item is done — distinct from the ambient crackle
            level.playSound(null, furnace.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.55f, 1.5f);
            level.sendParticles(ParticleTypes.FLAME,
                    ingredient.getX(), ingredient.getY() + 0.3, ingredient.getZ(),
                    10, 0.2, 0.15, 0.2, 0.04);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    ingredient.getX(), ingredient.getY() + 0.5, ingredient.getZ(),
                    6, 0.2, 0.2, 0.2, 0.0);

            Dragthings.LOGGER.info("Furnace ritual [{}]: {} → {} (furnace #{})",
                    type.name(), input.getHoverName().getString(),
                    result.getHoverName().getString(), furnace.getId());
        }
    }

    /** Consume one unit of fuel from a nearby fuel item. Returns true if successful. */
    private static boolean tryConsumeFuel(ServerLevel level, ItemEntity furnace, BurningState state) {
        AABB box = new AABB(furnace.position(), furnace.position()).inflate(RADIUS);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != furnace && i.isAlive() && isFuel(i.getItem()));

        if (nearby.isEmpty()) return false;

        ItemEntity fuelEntity = nearby.get(0);
        ItemStack fuelStack = fuelEntity.getItem();
        int burnTime = getFuelBurnTime(fuelStack);

        RitualUtil.shrinkOrDiscard(fuelEntity, 1);
        state.burnTimeLeft += burnTime;

        // Ignition sound differs per furnace type:
        //   furnace  → flint-and-steel (classic fire-starting)
        //   blasting → louder blastfurnace crackle (industrial)
        //   smoker   → soft smoker hiss (gentle ignition)
        net.minecraft.sounds.SoundEvent igniteSound = switch (state.type) {
            case FURNACE  -> SoundEvents.FLINTANDSTEEL_USE;
            case BLASTING -> SoundEvents.BLASTFURNACE_FIRE_CRACKLE;
            case SMOKING  -> SoundEvents.SMOKER_SMOKE;
        };
        level.playSound(null, furnace.blockPosition(),
                igniteSound, SoundSource.BLOCKS, 0.5f, state.type == FurnaceType.BLASTING ? 0.8f : 1f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                furnace.getX(), furnace.getY() + 0.3, furnace.getZ(),
                5, 0.15, 0.1, 0.15, 0.01);

        Dragthings.LOGGER.debug("Furnace ritual: consumed {} (burn={}t, furnace #{})",
                fuelStack.getHoverName().getString(), burnTime, furnace.getId());
        return true;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static FurnaceType detectType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(Items.FURNACE))       return FurnaceType.FURNACE;
        if (stack.is(Items.BLAST_FURNACE)) return FurnaceType.BLASTING;
        if (stack.is(Items.SMOKER))        return FurnaceType.SMOKING;
        return null;
    }

    private static boolean isFuel(ItemStack stack) {
        return net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
                .getFuel().containsKey(stack.getItem());
    }

    private static int getFuelBurnTime(ItemStack stack) {
        Integer t = net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
                .getFuel().get(stack.getItem());
        return t != null ? t : 0;
    }

    private static ItemStack getStack(ServerLevel level, int entityId) {
        var e = level.getEntity(entityId);
        return (e instanceof ItemEntity ie) ? ie.getItem() : ItemStack.EMPTY;
    }
}
