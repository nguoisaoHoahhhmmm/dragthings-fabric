package dragthings.ritual;

import dragthings.Dragthings;
import dragthings.config.GameplayConfig;
import dragthings.config.RitualConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grindstone ritual: drag a grindstone over enchanted items to strip their
 * enchantments, like the vanilla grindstone:
 *   - curses stay on the item
 *   - an enchanted book becomes a plain book
 *   - XP comes only from the enchantments that were removed
 */
public final class GrindstoneRitual {

    private static final Map<Integer, ServerPlayer> activeTables = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer>      cooldown     = new ConcurrentHashMap<>();

    // rituals.json, section "grindstone"
    private static RitualConfig.Grindstone cfg() { return GameplayConfig.rituals().grindstone; }

    private GrindstoneRitual() {}

    public static void onStartDrag(int entityId, ServerLevel level, ServerPlayer player) {
        if (!cfg().enabled) return;
        boolean isNew = !activeTables.containsKey(entityId);
        activeTables.put(entityId, player);
        if (isNew && level.getEntity(entityId) instanceof ItemEntity stone) {
            level.playSound(null, stone.blockPosition(),
                    SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.35f, 0.7f);
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
            int stoneId = entry.getKey();
            ServerPlayer player = entry.getValue();

            if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) {
                it.remove();
                cooldown.remove(stoneId);
                continue;
            }
            if (!(level.getEntity(stoneId) instanceof ItemEntity stone) || !stone.isAlive()) {
                it.remove();
                cooldown.remove(stoneId);
                continue;
            }

            if (!cooldown.containsKey(stoneId) && tryGrind(level, stone)) {
                cooldown.put(stoneId, cfg().cooldownTicks);
            }
        }
    }

    private static boolean tryGrind(ServerLevel level, ItemEntity stone) {
        AABB box = new AABB(stone.position(), stone.position()).inflate(cfg().radius);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != stone && i.isAlive() && !i.getItem().isEmpty() && !i.hasPickUpDelay());

        for (ItemEntity target : nearby) {
            ItemStack stack = target.getItem();

            // Enchantments for either kind of item (stored ones for books).
            ItemEnchantments enchants = EnchantmentHelper.getEnchantmentsForCrafting(stack);
            int xpBase = 0;
            for (var entry : enchants.entrySet()) {
                if (entry.getKey().is(EnchantmentTags.CURSE)) continue;
                xpBase += entry.getKey().value().getMinCost(entry.getIntValue());
            }
            // Nothing removable (no enchants, or only curses): skip. Without
            // this a cursed item would be "ground" again and again.
            if (xpBase <= 0 && !hasNonCurse(enchants)) continue;

            ItemStack stripped = stack.copy();
            EnchantmentHelper.updateEnchantments(stripped,
                    mutable -> mutable.removeIf(holder -> !holder.is(EnchantmentTags.CURSE)));
            if (stripped.is(Items.ENCHANTED_BOOK) && EnchantmentHelper.getEnchantmentsForCrafting(stripped).isEmpty()) {
                stripped = new ItemStack(Items.BOOK, stack.getCount());
            }

            // Vanilla XP: half the cost, rounded up, plus a random bonus up to the same amount.
            int half = (int) Math.ceil(xpBase / 2.0);
            int xpReward = half > 0 ? (half + level.random.nextInt(half)) * stack.getCount() : 0;

            target.setItem(stripped);
            target.setPickUpDelay(20);

            if (xpReward > 0) {
                ExperienceOrb.award(level, stone.position().add(0, 0.3, 0), xpReward);
            }

            level.playSound(null, stone.blockPosition(),
                    SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1f, 1f);
            level.playSound(null, stone.blockPosition(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.6f, 0.5f);
            level.playSound(null, stone.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 0.9f);
            level.sendParticles(ParticleTypes.CRIT,
                    stone.getX(), stone.getY() + 0.3, stone.getZ(), 16, 0.3, 0.2, 0.3, 0.04);
            level.sendParticles(ParticleTypes.ENCHANT,
                    stone.getX(), stone.getY() + 0.5, stone.getZ(), 20, 0.3, 0.3, 0.3, 0.06);

            Dragthings.LOGGER.info("Grindstone ritual: stripped {} (xp={}, stone #{})",
                    stack.getHoverName().getString(), xpReward, stone.getId());
            return true;
        }
        return false;
    }

    private static boolean hasNonCurse(ItemEnchantments enchants) {
        for (var entry : enchants.entrySet()) {
            if (!entry.getKey().is(EnchantmentTags.CURSE)) return true;
        }
        return false;
    }
}