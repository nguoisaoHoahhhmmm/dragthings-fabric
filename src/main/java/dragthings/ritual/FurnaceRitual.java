package dragthings.ritual;

import dragthings.Dragthings;
import dragthings.config.GameplayConfig;
import dragthings.config.RitualConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Furnace ritual: drag a furnace / blast furnace / smoker, and it cooks the
 * items lying around it, burning fuel from nearby items.
 *
 * Like a real furnace, fuel is only used while there is something to cook, so
 * a furnace dragged past nothing does not waste its coal.
 */
public final class FurnaceRitual {

    /** Tracks the burning state of an active furnace. */
    private static final class BurningState {
        final ServerLevel  level;
        final ServerPlayer player;
        final FurnaceType type;
        int burnTimeLeft;  // ticks of fuel remaining
        int particleTicker = 0;

        BurningState(ServerLevel l, ServerPlayer p, FurnaceType t, int burnTicks) {
            level = l; player = p; type = t; burnTimeLeft = burnTicks;
        }
    }

    /** Cooking progress of one item entity. */
    private static final class CookingProgress {
        int cookTime = 0;
        int cookTimeTotal;
        long lastTouched; // serverTick when it last cooked, so abandoned entries can be dropped
        CookingProgress(int total, long now) { cookTimeTotal = total; lastTouched = now; }
    }

    /** One ingredient that this furnace can cook right now. */
    private record Job(ItemEntity ingredient, RecipeHolder<AbstractCookingRecipe> holder) {}

    /** Maps vanilla furnace items to the correct recipe type. */
    public enum FurnaceType {
        FURNACE   (RecipeType.SMELTING,  SoundEvents.FURNACE_FIRE_CRACKLE),
        BLASTING  (RecipeType.BLASTING,  SoundEvents.BLASTFURNACE_FIRE_CRACKLE),
        SMOKING   (RecipeType.SMOKING,   SoundEvents.SMOKER_SMOKE);

        final RecipeType<? extends AbstractCookingRecipe> recipeType;
        final net.minecraft.sounds.SoundEvent sound;

        FurnaceType(RecipeType<? extends AbstractCookingRecipe> r, net.minecraft.sounds.SoundEvent s) {
            recipeType = r; sound = s;
        }
    }

    private static final Map<Integer, BurningState>    activeFurnaces     = new ConcurrentHashMap<>();
    private static final Map<Integer, CookingProgress> cookingItems       = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer>         fuelSearchCooldown = new ConcurrentHashMap<>();

    /**
     * Marker stored in the furnace's CUSTOM_MODEL_DATA while it burns. The item
     * models in assets/minecraft/models/item/{furnace,blast_furnace,smoker}.json
     * switch to the "lit" block model when they see this value.
     */
    public static final int LIT_MODEL_DATA = 7301;

    private static final int PARTICLE_INTERVAL = 3;    // spawn particles every N ticks
    private static final int PRUNE_INTERVAL    = 200;  // how often unused progress entries are dropped
    private static final int PROGRESS_KEEP     = 600;  // ...once untouched this long
    private static long serverTick = 0;

    // rituals.json, section "furnace": enabled, radius, fuelSearchCooldownTicks
    private static RitualConfig.Furnace cfg() { return GameplayConfig.rituals().furnace; }

    private FurnaceRitual() {}

    /** Called on every drag sync packet, so everything here must be safe to repeat. */
    public static void onStartDrag(int entityId, ServerLevel level, ServerPlayer player) {
        if (!cfg().enabled) return;
        if (activeFurnaces.containsKey(entityId)) return; // already registered: no second pickup sound

        if (!(level.getEntity(entityId) instanceof ItemEntity furnace)) return;
        FurnaceType type = detectType(furnace.getItem());
        if (type == null) return;

        activeFurnaces.put(entityId, new BurningState(level, player, type, 0));
        setLit(furnace, false); // clear a marker left over from an earlier crash
        level.playSound(null, furnace.blockPosition(), type.sound, SoundSource.BLOCKS, 0.35f, 0.55f);
    }

    public static void onRelease(int entityId) {
        BurningState state = activeFurnaces.remove(entityId);
        fuelSearchCooldown.remove(entityId);
        if (state != null) unlight(state, entityId); // a released furnace goes back to normal
        // Cooking progress is kept: an item stays mid-cook in the world.
    }

    public static void tick() {
        serverTick++;
        if (serverTick % PRUNE_INTERVAL == 0) {
            cookingItems.values().removeIf(p -> serverTick - p.lastTouched > PROGRESS_KEEP);
        }

        fuelSearchCooldown.replaceAll((id, t) -> t - 1);
        fuelSearchCooldown.values().removeIf(t -> t <= 0);

        if (activeFurnaces.isEmpty()) return;

        Iterator<Map.Entry<Integer, BurningState>> it = activeFurnaces.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, BurningState> entry = it.next();
            int furnaceId = entry.getKey();
            BurningState state = entry.getValue();

            // The player left mid-drag: stop, so an abandoned furnace does not keep working forever.
            if (state.player.isRemoved()) {
                unlight(state, furnaceId);
                it.remove();
                fuelSearchCooldown.remove(furnaceId);
                continue;
            }
            ServerLevel level = state.level;

            if (!(level.getEntity(furnaceId) instanceof ItemEntity furnace) || !furnace.isAlive()) {
                it.remove();
                fuelSearchCooldown.remove(furnaceId);
                continue;
            }

            FurnaceType type = detectType(furnace.getItem());
            if (type == null) { unlight(state, furnaceId); it.remove(); fuelSearchCooldown.remove(furnaceId); continue; }

            // ── What can be cooked here? Nothing: do nothing and keep the fuel. ──
            AABB box = new AABB(furnace.position(), furnace.position()).inflate(cfg().radius);
            List<Job> jobs = new ArrayList<>();
            for (ItemEntity candidate : level.getEntitiesOfClass(ItemEntity.class, box,
                    i -> i != furnace && i.isAlive() && !i.getItem().isEmpty())) {
                findRecipe(level, type, candidate.getItem())
                        .ifPresent(holder -> jobs.add(new Job(candidate, holder)));
            }
            if (jobs.isEmpty()) { setLit(furnace, false); continue; }

            // ── Fuel phase ──────────────────────────────────────────────
            if (state.burnTimeLeft <= 0) {
                if (fuelSearchCooldown.containsKey(furnaceId)) { setLit(furnace, false); continue; } // waiting before the next fuel check
                if (!tryConsumeFuel(level, furnace, state, box)) {
                    setLit(furnace, false);
                    // A cold puff of smoke says "I need fuel"
                    if (++state.particleTicker % 20 == 0) {
                        level.sendParticles(ParticleTypes.SMOKE,
                                furnace.getX(), furnace.getY() + 0.3, furnace.getZ(),
                                3, 0.1, 0.1, 0.1, 0.005);
                    }
                    fuelSearchCooldown.put(furnaceId, cfg().fuelSearchCooldownTicks);
                    continue;
                }
            }

            // ── Burning phase ───────────────────────────────────────────
            state.burnTimeLeft--;
            setLit(furnace, true);
            if (++state.particleTicker % PARTICLE_INTERVAL == 0) {
                level.sendParticles(ParticleTypes.FLAME,
                        furnace.getX(), furnace.getY() + 0.25, furnace.getZ(),
                        2, 0.08, 0.08, 0.08, 0.005);
                level.sendParticles(ParticleTypes.SMOKE,
                        furnace.getX(), furnace.getY() + 0.35, furnace.getZ(),
                        1, 0.05, 0.05, 0.05, 0.003);
            }

            // ── Cooking phase ───────────────────────────────────────────
            for (Job job : jobs) tickCooking(level, furnace, job, type);
        }
    }

    // ── Cooking logic ────────────────────────────────────────────────────

    private static void tickCooking(ServerLevel level, ItemEntity furnace, Job job, FurnaceType type) {
        ItemEntity ingredient = job.ingredient();
        AbstractCookingRecipe recipe = job.holder().value();
        ItemStack input = ingredient.getItem();
        int cookTotal = recipe.getCookingTime();

        CookingProgress progress = cookingItems.computeIfAbsent(
                ingredient.getId(), id -> new CookingProgress(cookTotal, serverTick));
        progress.lastTouched = serverTick;

        // The item changed into something with another cooking time: start over,
        // and remember the new total (otherwise this would reset every tick).
        if (progress.cookTimeTotal != cookTotal) {
            progress.cookTimeTotal = cookTotal;
            progress.cookTime = 0;
        }

        progress.cookTime++;

        if (progress.cookTime % 8 == 0) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    ingredient.getX(), ingredient.getY() + 0.15, ingredient.getZ(),
                    2, 0.06, 0.06, 0.06, 0.01);
        }
        if (progress.cookTime < progress.cookTimeTotal) return;

        // ── Done ──
        cookingItems.remove(ingredient.getId());
        SingleRecipeInput recipeInput = new SingleRecipeInput(input.copyWithCount(1));
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
        level.playSound(null, furnace.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.55f, 1.5f);
        level.sendParticles(ParticleTypes.FLAME,
                ingredient.getX(), ingredient.getY() + 0.3, ingredient.getZ(), 10, 0.2, 0.15, 0.2, 0.04);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                ingredient.getX(), ingredient.getY() + 0.5, ingredient.getZ(), 6, 0.2, 0.2, 0.2, 0.0);

        Dragthings.LOGGER.info("Furnace ritual [{}]: {} -> {} (furnace #{})",
                type.name(), input.getHoverName().getString(),
                result.getHoverName().getString(), furnace.getId());
    }

    @SuppressWarnings("unchecked")
    private static Optional<RecipeHolder<AbstractCookingRecipe>> findRecipe(
            ServerLevel level, FurnaceType type, ItemStack stack) {
        RecipeType<AbstractCookingRecipe> recipeType = (RecipeType<AbstractCookingRecipe>) type.recipeType;
        return level.getRecipeManager().getRecipeFor(recipeType, new SingleRecipeInput(stack.copyWithCount(1)), level);
    }

    /** Burns one nearby fuel item. A lava bucket leaves its empty bucket, like a real furnace. */
    private static boolean tryConsumeFuel(ServerLevel level, ItemEntity furnace, BurningState state, AABB box) {
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != furnace && i.isAlive() && isFuel(i.getItem()));
        if (nearby.isEmpty()) return false;

        ItemEntity fuelEntity = nearby.get(0);
        ItemStack fuelStack = fuelEntity.getItem();
        int burnTime = getFuelBurnTime(fuelStack);
        net.minecraft.world.item.Item remainder = fuelStack.getItem().getCraftingRemainingItem(); // e.g. bucket
        ItemStack leftover = remainder == null ? ItemStack.EMPTY : new ItemStack(remainder);

        RitualUtil.shrinkOrDiscard(fuelEntity, 1);
        if (!leftover.isEmpty()) RitualUtil.spawnResult(level, furnace, leftover);
        state.burnTimeLeft += burnTime;

        net.minecraft.sounds.SoundEvent igniteSound = switch (state.type) {
            case FURNACE  -> SoundEvents.FLINTANDSTEEL_USE;
            case BLASTING -> SoundEvents.BLASTFURNACE_FIRE_CRACKLE;
            case SMOKING  -> SoundEvents.SMOKER_SMOKE;
        };
        level.playSound(null, furnace.blockPosition(),
                igniteSound, SoundSource.BLOCKS, 0.5f, state.type == FurnaceType.BLASTING ? 0.8f : 1f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                furnace.getX(), furnace.getY() + 0.3, furnace.getZ(), 5, 0.15, 0.1, 0.15, 0.01);

        Dragthings.LOGGER.debug("Furnace ritual: burned {} ({} ticks, furnace #{})",
                fuelStack.getHoverName().getString(), burnTime, furnace.getId());
        return true;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Switches the dragged furnace between its normal and "lit" look. It only
     * touches the stack when the state really changes, and always sets a COPY
     * so the change is synced to clients.
     */
    private static void setLit(ItemEntity furnace, boolean lit) {
        ItemStack stack = furnace.getItem();
        CustomModelData current = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        boolean isLit = current != null && current.value() == LIT_MODEL_DATA;
        if (isLit == lit) return;

        ItemStack copy = stack.copy();
        if (lit) copy.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(LIT_MODEL_DATA));
        else copy.remove(DataComponents.CUSTOM_MODEL_DATA);
        furnace.setItem(copy);
    }

    private static void unlight(BurningState state, int furnaceId) {
        if (state.level.getEntity(furnaceId) instanceof ItemEntity furnace && furnace.isAlive()) {
            setLit(furnace, false);
        }
    }

    private static FurnaceType detectType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(Items.FURNACE))       return FurnaceType.FURNACE;
        if (stack.is(Items.BLAST_FURNACE)) return FurnaceType.BLASTING;
        if (stack.is(Items.SMOKER))        return FurnaceType.SMOKING;
        return null;
    }

    private static boolean isFuel(ItemStack stack) {
        return AbstractFurnaceBlockEntity.getFuel().containsKey(stack.getItem());
    }

    private static int getFuelBurnTime(ItemStack stack) {
        Integer t = AbstractFurnaceBlockEntity.getFuel().get(stack.getItem());
        return t != null ? t : 0;
    }
}