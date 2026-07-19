package dragthings.ritual;

import dragthings.Dragthings;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anvil ritual: kéo anvil gần 2 items để repair/combine/enchant.
 * - Base item + same item type  → repair durability
 * - Base item + enchanted book  → apply enchantments
 * - Base item + same item (enchanted) → combine enchantments
 * XP được consume từ các XP orb gần đó.
 */
public final class AnvilRitual {

    private static final Map<Integer, ServerLevel> activeAnvils = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer>     cooldown     = new ConcurrentHashMap<>();

    private static final double RADIUS         = 1;
    private static final int    COOLDOWN_TICKS = 30;
    private static final int    XP_PER_LEVEL   = 7;   // ~7 raw XP = 1 level at low levels

    private AnvilRitual() {}

    // ── Public API ────────────────────────────────────────────────────────

    public static void onStartDrag(int entityId, ServerLevel level) {
        boolean isNew = !activeAnvils.containsKey(entityId);
        activeAnvils.put(entityId, level);
        // Heavy thud only once — when the anvil is first picked up.
        // onStartDrag is called every sync packet (every 3 ticks), so
        // we must guard against replaying the sound repeatedly.
        if (isNew) {
            var e = level.getEntity(entityId);
            if (e instanceof ItemEntity anvil) {
                level.playSound(null, anvil.blockPosition(),
                        SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4f, 1.4f);
            }
        }
    }

    public static void onRelease(int entityId) {
        activeAnvils.remove(entityId);
        cooldown.remove(entityId);
    }

    public static void tick() {
        cooldown.replaceAll((id, ticks) -> ticks - 1);
        cooldown.values().removeIf(ticks -> ticks <= 0);

        if (activeAnvils.isEmpty()) return;

        Iterator<Map.Entry<Integer, ServerLevel>> it = activeAnvils.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ServerLevel> entry = it.next();
            int anvilId       = entry.getKey();
            ServerLevel level = entry.getValue();

            var e = level.getEntity(anvilId);
            if (!(e instanceof ItemEntity anvil) || !anvil.isAlive()) {
                it.remove();
                cooldown.remove(anvilId);
                continue;
            }

            if (!cooldown.containsKey(anvilId) && tryAnvil(level, anvil)) {
                cooldown.put(anvilId, COOLDOWN_TICKS);
            }
        }
    }

    // ── Core logic ────────────────────────────────────────────────────────

    private static boolean tryAnvil(ServerLevel level, ItemEntity anvil) {
        AABB box = new AABB(anvil.position(), anvil.position()).inflate(RADIUS);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != anvil && i.isAlive() && !i.getItem().isEmpty());

        if (nearby.size() < 2) return false;

        for (ItemEntity baseEntity : nearby) {
            ItemStack base = baseEntity.getItem();
            if (base.isEmpty()) continue;

            for (ItemEntity sacrificeEntity : nearby) {
                if (sacrificeEntity == baseEntity) continue;
                ItemStack sacrifice = sacrificeEntity.getItem();
                if (sacrifice.isEmpty()) continue;

                Result result = tryAnvilCombine(base, sacrifice);
                if (result == null) continue;

                // Cần đủ XP — orbs gần đây
                int available = consumeNearbyXP(level, anvil, result.xpCost);
                if (available < result.xpCost) continue;

                // Áp dụng kết quả
                if (baseEntity.getItem().getCount() <= 1) {
                    baseEntity.setItem(result.output.copy());
                    baseEntity.setPickUpDelay(20);
                } else {
                    RitualUtil.shrinkOrDiscard(baseEntity, 1);
                    RitualUtil.spawnResult(level, anvil, result.output);
                }
                RitualUtil.shrinkOrDiscard(sacrificeEntity, 1);

                // Effects — sound differs by operation type:
                //   enchant combine → magical "use" + level-up chime
                //   repair          → mechanical anvil strike + item pickup ding
                boolean isEnchantOp = result.xpCost > 0
                        && !sacrifice.getEnchantments().isEmpty();
                if (isEnchantOp) {
                    level.playSound(null, anvil.blockPosition(),
                            SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 1.0f);
                    level.playSound(null, anvil.blockPosition(),
                            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.7f, 1.2f);
                    level.playSound(null, anvil.blockPosition(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.4f);
                } else {
                    // Repair: solid anvil hit + subtle ding
                    level.playSound(null, anvil.blockPosition(),
                            SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 0.85f);
                    level.playSound(null, anvil.blockPosition(),
                            SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3f, 1.8f);
                }
                level.sendParticles(ParticleTypes.ENCHANT,
                        anvil.getX(), anvil.getY() + 0.3, anvil.getZ(),
                        20, 0.3, 0.2, 0.3, 0.05);
                level.sendParticles(ParticleTypes.CRIT,
                        baseEntity.getX(), baseEntity.getY() + 0.2, baseEntity.getZ(),
                        8, 0.2, 0.2, 0.2, 0.02);

                Dragthings.LOGGER.info("Anvil ritual: {} + {} → {} (cost={} lvl, anvil #{})",
                        base.getHoverName().getString(),
                        sacrifice.getHoverName().getString(),
                        result.output.getHoverName().getString(),
                        result.xpCost, anvil.getId());
                return true;
            }
        }
        return false;
    }

    // ── Result container ──────────────────────────────────────────────────

    private static final class Result {
        final ItemStack output;
        final int xpCost; // in levels
        Result(ItemStack out, int cost) { output = out; xpCost = cost; }
    }

    // ── Combine logic ─────────────────────────────────────────────────────

    /**
     * Thử combine base + sacrifice.
     * Trả về null nếu không có operation hợp lệ.
     *
     * Priority:
     *   1. Enchantment combine/upgrade (book hoặc same-type item)
     *   2. Durability repair (same item type, no enchants on sacrifice)
     */
    private static Result tryAnvilCombine(ItemStack base, ItemStack sacrifice) {

        // ── Case 1: Combine/upgrade enchantments ─────────────────────────
        // Sacrifice có stored enchants (book) hoặc regular enchants (item)
        ItemEnchantments baseEnchants     = EnchantmentHelper.getEnchantmentsForCrafting(base);
        ItemEnchantments sacrificeEnchants = EnchantmentHelper.getEnchantmentsForCrafting(sacrifice);

        if (!sacrificeEnchants.isEmpty()) {
            // Chỉ cho phép nếu base có enchantment value > 0 (enchantable)
            // hoặc base là item cùng loại với sacrifice
            boolean targetIsEnchantable = base.getItem().getEnchantmentValue() > 0;
            boolean sameType = base.getItem() == sacrifice.getItem();

            if (targetIsEnchantable || sameType) {
                ItemStack result = base.copy();
                ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(baseEnchants);
                boolean changed = false;
                int cost = 0;

                for (var enchEntry : sacrificeEnchants.entrySet()) {
                    var enchHolder = enchEntry.getKey();
                    int sacrificeLvl = enchEntry.getIntValue();
                    int baseLvl = mutable.getLevel(enchHolder);

                    int newLvl;
                    if (baseLvl == sacrificeLvl) {
                        // Same level → upgrade by 1 (capped at max)
                        newLvl = Math.min(baseLvl + 1, enchHolder.value().getMaxLevel());
                    } else {
                        // Different levels → keep the higher
                        newLvl = Math.max(baseLvl, sacrificeLvl);
                    }

                    if (newLvl > baseLvl) {
                        mutable.set(enchHolder, newLvl);
                        changed = true;
                        cost += (newLvl - baseLvl);
                    }
                }

                if (changed) {
                    EnchantmentHelper.setEnchantments(result, mutable.toImmutable());
                    return new Result(result, Math.max(1, cost));
                }
            }
        }

        // ── Case 2: Durability repair (same type, damageable) ────────────
        if (base.getItem() == sacrifice.getItem() && base.isDamageableItem()) {
            int currentDamage = base.getDamageValue();
            if (currentDamage > 0) {
                // Each sacrifice repairs up to 25% of max durability
                int repairAmount = base.getMaxDamage() / 4;
                int actualRepair = Math.min(currentDamage, repairAmount);
                ItemStack result = base.copy();
                result.setDamageValue(currentDamage - actualRepair);
                // Cost: 1 level per 25% repaired
                int cost = Math.max(1, actualRepair * 4 / base.getMaxDamage());
                return new Result(result, cost);
            }
        }

        return null;
    }

    // ── XP consumption ────────────────────────────────────────────────────

    /**
     * Consume XP orbs gần anvil để trả `needed` levels.
     * Returns số levels thực tế có thể trả (có thể ít hơn `needed` nếu thiếu orb).
     * Chỉ consume khi đủ XP để không phí.
     */
    private static int consumeNearbyXP(ServerLevel level, ItemEntity anvil, int needed) {
        AABB box = new AABB(anvil.position(), anvil.position()).inflate(RADIUS);
        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, box);

        // Tổng XP khả dụng
        int totalXP = 0;
        for (ExperienceOrb orb : orbs) totalXP += orb.getValue();

        int availableLevels = totalXP / XP_PER_LEVEL;
        if (availableLevels < needed) return availableLevels; // không đủ → không consume

        // Đủ XP — consume đúng lượng cần
        int xpToConsume = needed * XP_PER_LEVEL;
        for (ExperienceOrb orb : orbs) {
            if (xpToConsume <= 0) break;
            int take = Math.min(orb.getValue(), xpToConsume);
            xpToConsume -= take;
            level.sendParticles(ParticleTypes.ENCHANT,
                    orb.getX(), orb.getY(), orb.getZ(),
                    3, 0.1, 0.1, 0.1, 0.02);
            orb.discard();
        }

        return needed;
    }
}
