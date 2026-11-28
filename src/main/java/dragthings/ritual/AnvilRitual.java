package dragthings.ritual;

import dragthings.Dragthings;
import dragthings.config.GameplayConfig;
import dragthings.config.RitualConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anvil ritual: drag an anvil next to two items.
 *   - base + enchanted book / enchanted item  -> merge enchantments
 *   - base + same item type                   -> repair durability
 * The XP is taken from XP orbs lying near the anvil.
 */
public final class AnvilRitual {

    private static final Map<Integer, ServerPlayer> activeAnvils = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer>      cooldown     = new ConcurrentHashMap<>();

    // rituals.json, section "anvil"
    private static RitualConfig.Anvil cfg() { return GameplayConfig.rituals().anvil; }

    private AnvilRitual() {}

    // ── Public API ────────────────────────────────────────────────────────

    /** Now takes the player, so an anvil left behind by a disconnect is cleaned up. */
    public static void onStartDrag(int entityId, ServerLevel level, ServerPlayer player) {
        if (!cfg().enabled) return;
        boolean isNew = !activeAnvils.containsKey(entityId);
        activeAnvils.put(entityId, player);
        // Heavy thud only on the first pickup, not on every sync packet.
        if (isNew && level.getEntity(entityId) instanceof ItemEntity anvil) {
            level.playSound(null, anvil.blockPosition(),
                    SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4f, 1.4f);
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

        Iterator<Map.Entry<Integer, ServerPlayer>> it = activeAnvils.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ServerPlayer> entry = it.next();
            int anvilId = entry.getKey();
            ServerPlayer player = entry.getValue();

            if (player.isRemoved() || !(player.level() instanceof ServerLevel level)) {
                it.remove();
                cooldown.remove(anvilId);
                continue;
            }
            if (!(level.getEntity(anvilId) instanceof ItemEntity anvil) || !anvil.isAlive()) {
                it.remove();
                cooldown.remove(anvilId);
                continue;
            }

            if (!cooldown.containsKey(anvilId) && tryAnvil(level, anvil)) {
                cooldown.put(anvilId, cfg().cooldownTicks);
            }
        }
    }

    // ── Core logic ────────────────────────────────────────────────────────

    private static boolean tryAnvil(ServerLevel level, ItemEntity anvil) {
        AABB box = new AABB(anvil.position(), anvil.position()).inflate(cfg().radius);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i != anvil && i.isAlive() && !i.getItem().isEmpty() && !i.hasPickUpDelay());

        if (nearby.size() < 2) return false;

        for (ItemEntity baseEntity : nearby) {
            ItemStack base = baseEntity.getItem();

            for (ItemEntity sacrificeEntity : nearby) {
                if (sacrificeEntity == baseEntity) continue;
                ItemStack sacrifice = sacrificeEntity.getItem();

                Result result = tryAnvilCombine(base, sacrifice);
                if (result == null) continue;

                // XP is only taken when there is enough of it.
                if (!consumeNearbyXP(level, anvil, result.xpCost * cfg().xpPerLevel)) continue;

                if (base.getCount() <= 1) {
                    baseEntity.setItem(result.output.copy());
                    baseEntity.setPickUpDelay(20);
                } else {
                    RitualUtil.shrinkOrDiscard(baseEntity, 1);
                    RitualUtil.spawnResult(level, anvil, result.output);
                }
                RitualUtil.shrinkOrDiscard(sacrificeEntity, 1);

                if (result.enchant) {
                    level.playSound(null, anvil.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 1.0f);
                    level.playSound(null, anvil.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.7f, 1.2f);
                    level.playSound(null, anvil.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.4f);
                } else {
                    level.playSound(null, anvil.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 0.85f);
                    level.playSound(null, anvil.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3f, 1.8f);
                }
                level.sendParticles(ParticleTypes.ENCHANT,
                        anvil.getX(), anvil.getY() + 0.3, anvil.getZ(), 20, 0.3, 0.2, 0.3, 0.05);
                level.sendParticles(ParticleTypes.CRIT,
                        baseEntity.getX(), baseEntity.getY() + 0.2, baseEntity.getZ(), 8, 0.2, 0.2, 0.2, 0.02);

                Dragthings.LOGGER.debug("Anvil ritual: {} -> {} (cost={} lvl, anvil #{})",
                        base.getHoverName().getString(), result.output.getHoverName().getString(),
                        result.xpCost, anvil.getId());
                return true;
            }
        }
        return false;
    }

    private static final class Result {
        final ItemStack output;
        final int xpCost;      // in levels
        final boolean enchant; // enchant merge (true) or durability repair (false)
        Result(ItemStack out, int cost, boolean enchant) { output = out; xpCost = cost; this.enchant = enchant; }
    }

    // ── Combine logic ─────────────────────────────────────────────────────

    /** Returns null when base + sacrifice have no valid operation. */
    private static Result tryAnvilCombine(ItemStack base, ItemStack sacrifice) {

        // ── Case 1: merge enchantments ───────────────────────────────────
        ItemEnchantments baseEnchants = EnchantmentHelper.getEnchantmentsForCrafting(base);
        ItemEnchantments sacrificeEnchants = EnchantmentHelper.getEnchantmentsForCrafting(sacrifice);
        boolean baseIsBook = base.is(Items.ENCHANTED_BOOK);

        if (!sacrificeEnchants.isEmpty()
                && (baseIsBook || base.getItem().getEnchantmentValue() > 0)) {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(baseEnchants);
            boolean changed = false;
            int cost = 0;

            for (var enchEntry : sacrificeEnchants.entrySet()) {
                var holder = enchEntry.getKey();
                int sacrificeLvl = enchEntry.getIntValue();
                int baseLvl = mutable.getLevel(holder);

                // A book can carry anything; a tool can only take what fits it.
                if (!baseIsBook && !holder.value().canEnchant(base)) continue;

                // Conflicting enchantments (Sharpness + Smite, two Protections...) are skipped.
                boolean conflict = false;
                for (var existing : mutable.keySet()) {
                    if (!existing.equals(holder) && !Enchantment.areCompatible(holder, existing)) {
                        conflict = true;
                        break;
                    }
                }
                if (conflict) continue;

                int newLvl = baseLvl == sacrificeLvl
                        ? Math.min(baseLvl + 1, holder.value().getMaxLevel())
                        : Math.max(baseLvl, sacrificeLvl);

                if (newLvl > baseLvl) {
                    mutable.set(holder, newLvl);
                    changed = true;
                    cost += newLvl - baseLvl;
                }
            }

            if (changed) {
                ItemStack out = base.copy();
                out.setCount(1); // one item out, even when the base was a stack
                EnchantmentHelper.setEnchantments(out, mutable.toImmutable());
                return new Result(out, Math.max(1, cost), true);
            }
        }

        // ── Case 2: repair (same type, damaged) ──────────────────────────
        if (base.getItem() == sacrifice.getItem() && base.isDamageableItem()) {
            int damage = base.getDamageValue();
            if (damage > 0) {
                int repair = Math.min(damage, base.getMaxDamage() / 4); // 25% per sacrifice
                ItemStack out = base.copy();
                out.setCount(1);
                out.setDamageValue(damage - repair);
                int cost = Math.max(1, repair * 4 / base.getMaxDamage());
                return new Result(out, cost, false);
            }
        }

        return null;
    }

    // ── XP consumption ────────────────────────────────────────────────────

    /**
     * Takes {@code neededXp} raw XP from the orbs near the anvil, only when
     * there is enough. A partly used orb is not wasted: the surplus is
     * dropped again as a new orb.
     */
    private static boolean consumeNearbyXP(ServerLevel level, ItemEntity anvil, int neededXp) {
        AABB box = new AABB(anvil.position(), anvil.position()).inflate(cfg().radius);
        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, box, ExperienceOrb::isAlive);

        int total = 0;
        for (ExperienceOrb orb : orbs) total += orb.getValue();
        if (total < neededXp) return false;

        int taken = 0;
        for (ExperienceOrb orb : orbs) {
            if (taken >= neededXp) break;
            taken += orb.getValue();
            level.sendParticles(ParticleTypes.ENCHANT,
                    orb.getX(), orb.getY(), orb.getZ(), 3, 0.1, 0.1, 0.1, 0.02);
            orb.discard();
        }

        int surplus = taken - neededXp;
        if (surplus > 0) ExperienceOrb.award(level, anvil.position().add(0, 0.3, 0), surplus);
        return true;
    }
}