package dragthings.ritual;

import dragthings.Dragthings;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grindstone ritual: drag a grindstone item over enchanted items to strip
 * their enchantments, returning a clean copy and spawning XP orbs proportional
 * to the enchantment value — matching vanilla grindstone behaviour.
 */
public final class GrindstoneRitual {

    // FIX: was Map<Integer, ServerLevel> — no player reference meant no way
    // to detect the dragging player disconnecting, so this entry leaked
    // forever once a player left mid-drag. Made worse now that dragged
    // items never despawn (setUnlimitedLifetime), so the old 5-minute
    // despawn was quietly acting as a failsafe cleanup — gone now, needs
    // its own real cleanup. Level is derived from player.level().
    private static final Map<Integer, ServerPlayer> activeTables = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer>      cooldown     = new ConcurrentHashMap<>();

    private static final double RADIUS         = 1;
    private static final int    COOLDOWN_TICKS = 20;

    private GrindstoneRitual() {}

    public static void onStartDrag(int entityId, ServerLevel level, ServerPlayer player) {
        boolean isNew = !activeTables.containsKey(entityId);
        activeTables.put(entityId, player);
        // Play start sound only on the first registration, not on every sync packet.
        if (isNew) {
            var e = level.getEntity(entityId);
            if (e instanceof ItemEntity stone) {
                level.playSound(null, stone.blockPosition(),
                        SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.35f, 0.7f);
            }
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
            int          stoneId = entry.getKey();
            ServerPlayer player  = entry.getValue();

            // FIX: player disconnected mid-drag — stop tracking.
            if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) {
                it.remove();
                cooldown.remove(stoneId);
                continue;
            }

            var e = level.getEntity(stoneId);
            if (!(e instanceof ItemEntity stone) || !stone.isAlive()) {
                it.remove();
                cooldown.remove(stoneId);
                continue;
            }

            if (!cooldown.containsKey(stoneId) && tryGrind(level, stone)) {
                cooldown.put(stoneId, COOLDOWN_TICKS);
            }
        }
    }

    private static boolean tryGrind(ServerLevel level, ItemEntity stone) {
        AABB box = new AABB(stone.position(), stone.position()).inflate(RADIUS);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != stone && i.isAlive() && !i.getItem().isEmpty());

        for (ItemEntity target : nearby) {
            ItemStack stack = target.getItem();

            // Check regular enchantments or stored enchantments (books)
            ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
            ItemEnchantments stored   = stack.get(DataComponents.STORED_ENCHANTMENTS);
            boolean hasEnchants = (enchants != null && !enchants.isEmpty())
                    || (stored  != null && !stored.isEmpty());
            if (!hasEnchants) continue;

            // Calculate XP reward before stripping
            int xpReward = computeXpReward(enchants, stored);

            // Strip enchantments via DataComponents (1.21.1 API)
            ItemStack stripped = stack.copyWithCount(stack.getCount());
            if (enchants != null && !enchants.isEmpty())
                stripped.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            if (stored != null && !stored.isEmpty())
                stripped.set(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);

            target.setItem(stripped);
            target.setPickUpDelay(20);

            // Spawn XP orbs near the grindstone
            if (xpReward > 0) {
                ExperienceOrb.award(level,
                        stone.position().add(0, 0.3, 0),
                        xpReward);
            }

            level.playSound(null, stone.blockPosition(),
                    SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1f, 1f);
            // Enchantment-stripped "whoosh" — the magic peeling away
            level.playSound(null, stone.blockPosition(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.6f, 0.5f);
            level.playSound(null, stone.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 0.9f);
            level.sendParticles(ParticleTypes.CRIT,
                    stone.getX(), stone.getY() + 0.3, stone.getZ(),
                    16, 0.3, 0.2, 0.3, 0.04);
            level.sendParticles(ParticleTypes.ENCHANT,
                    stone.getX(), stone.getY() + 0.5, stone.getZ(),
                    20, 0.3, 0.3, 0.3, 0.06);

            Dragthings.LOGGER.info("Grindstone ritual: stripped {} (xp={}, stone #{})",
                    stack.getHoverName().getString(), xpReward, stone.getId());
            return true;
        }
        return false;
    }

    /**
     * Mirrors vanilla grindstone XP calculation:
     * sum each enchantment's min-XP cost at its level, then clamp to [3, 8] per enchant.
     */
    private static int computeXpReward(ItemEnchantments enchants, ItemEnchantments stored) {
        int total = 0;
        if (enchants != null) {
            for (var entry : enchants.entrySet()) {
                int lvl  = entry.getIntValue();
                int base = entry.getKey().value().getMinCost(lvl);
                total += Math.max(3, Math.min(8, base / 2));
            }
        }
        if (stored != null) {
            for (var entry : stored.entrySet()) {
                int lvl  = entry.getIntValue();
                int base = entry.getKey().value().getMinCost(lvl);
                total += Math.max(3, Math.min(8, base / 2));
            }
        }
        return total;
    }
}