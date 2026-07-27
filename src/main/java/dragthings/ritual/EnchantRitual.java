package dragthings.ritual;

import dragthings.Dragthings;
import dragthings.network.RitualProgressPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class EnchantRitual {

    private static final class State {
        final ServerLevel level;
        final ServerPlayer player;
        State(ServerLevel l, ServerPlayer p) { level = l; player = p; }
    }

    private static final Map<Integer, State>   activeTables  = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> storedXp      = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> storedOrbCount = new ConcurrentHashMap<>();

    private static final double RADIUS        = 1.5;
    private static final double ABSORB_DIST   = 0.4;
    private static final double ORB_PULL      = 0.08;
    private static final int    XP_PER_LEVEL  = 7;
    private static final int    MAX_LEVEL     = 30;
    private static final int    LAPIS_COST    = 1;
    public  static final int    MAX_ORB_BAR   = 15;

    private EnchantRitual() {}

    /** Expose the set of active enchanting table entity IDs (used by the XP-orb pickup mixin). */
    public static java.util.Set<Integer> activeTableIds() { return activeTables.keySet(); }

    public static void onStartDrag(int entityId, ServerLevel level, ServerPlayer player) {
        boolean isNew = !activeTables.containsKey(entityId);
        activeTables.put(entityId, new State(level, player));
        // Mystical hum only on first pickup, not on every sync packet.
        if (isNew) {
            var e = level.getEntity(entityId);
            if (e instanceof ItemEntity table) {
                level.playSound(null, table.blockPosition(),
                        SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.5f, 0.6f);
            }
        }
        sendProgress(player, entityId, storedOrbCount.getOrDefault(entityId, 0));
    }

    public static void onRelease(int entityId, ServerPlayer player) {
        activeTables.remove(entityId);
        // FIX: onRelease is called from the client packet handler, so the
        // player should still be valid here in practice — but guard anyway
        // since sendProgress() otherwise silently no-ops on a bad connection,
        // and this makes the invariant explicit rather than implicit.
        if (!player.isRemoved()) {
            sendProgress(player, entityId, -1);
        }
    }

    public static void tick() {
        if (activeTables.isEmpty()) return;

        Iterator<Map.Entry<Integer, State>> it = activeTables.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, State> entry = it.next();
            int tableId         = entry.getKey();
            ServerLevel level   = entry.getValue().level;
            ServerPlayer player = entry.getValue().player;

            // FIX: previously only checked whether the table item entity was
            // still alive — if the PLAYER disconnected mid-ritual (while
            // dragging an enchanting table), this entry (plus storedXp and
            // storedOrbCount) leaked forever, since the item entity itself
            // can remain alive in the world independent of the player's
            // connection. Same cleanup pattern already used by
            // ChestLootRitual/EnderChestRitual.
            if (player.isRemoved()) {
                it.remove(); storedXp.remove(tableId); storedOrbCount.remove(tableId);
                continue;
            }

            var e = level.getEntity(tableId);
            if (!(e instanceof ItemEntity table) || !table.isAlive()) {
                it.remove(); storedXp.remove(tableId); storedOrbCount.remove(tableId);
                continue;
            }

            AABB box = new AABB(table.position(), table.position()).inflate(RADIUS);

            // Pull + absorb XP orbs
            boolean absorbed = false;
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
                Vec3 toTable = table.position().subtract(orb.position());
                double dist  = toTable.length();
                if (dist < ABSORB_DIST) {
                    storedXp.merge(tableId, orb.getValue(), Integer::sum);
                    storedOrbCount.merge(tableId, 1, Integer::sum);
                    absorbed = true;
                    level.sendParticles(ParticleTypes.ENCHANT, orb.getX(), orb.getY() + 0.1, orb.getZ(), 10, 0.15, 0.15, 0.15, 0.04);
                    level.sendParticles(ParticleTypes.WAX_OFF, table.getX(), table.getY() + 0.15, table.getZ(), 3, 0.1, 0.1, 0.1, 0.01);
                    level.playSound(null, table.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.4f);
                    orb.discard();
                } else if (dist > 0.001) {
                    Vec3 pull = toTable.normalize().scale(ORB_PULL);
                    orb.setDeltaMovement(pull.x, pull.y + 0.02, pull.z);
                }
            }
            if (absorbed) sendProgress(player, tableId, Math.min(MAX_ORB_BAR, storedOrbCount.getOrDefault(tableId, 0)));

            // Check enchant readiness
            ItemEntity lapis = null, target = null;
            for (ItemEntity nearby : level.getEntitiesOfClass(ItemEntity.class, box)) {
                if (nearby == table) continue;
                ItemStack s = nearby.getItem();
                if (lapis == null && s.is(Items.LAPIS_LAZULI) && s.getCount() >= LAPIS_COST) lapis = nearby;
                else if (target == null && !s.is(Items.LAPIS_LAZULI) && s.getItem().getEnchantmentValue() > 0) target = nearby;
            }

            int bank = storedXp.getOrDefault(tableId, 0);
            int enchLvl = Math.min(MAX_LEVEL, bank / XP_PER_LEVEL);

            if (lapis != null && target != null && enchLvl >= 1) {
                ItemStack lapisStack = lapis.getItem();
                ItemStack targetStack = target.getItem();
                ItemStack enchanted = EnchantmentHelper.enchantItem(RandomSource.create(), targetStack.copy(),
                        enchLvl, level.registryAccess(), Optional.<HolderSet<Enchantment>>empty());
                target.setItem(enchanted);
                lapisStack.shrink(LAPIS_COST);
                if (lapisStack.isEmpty()) lapis.discard(); else lapis.setItem(lapisStack);
                storedXp.put(tableId, bank - enchLvl * XP_PER_LEVEL);
                storedOrbCount.put(tableId, 0);
                sendProgress(player, tableId, 0);
                level.playSound(null, table.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
                // Layered magical finish: glyph swirl + ethereal whoosh
                level.playSound(null, table.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.5f);
                level.playSound(null, table.blockPosition(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.PLAYERS, 0.5f, 1.2f);
                level.sendParticles(ParticleTypes.ENCHANT, table.getX(), table.getY() + 0.3, table.getZ(), 40, 0.4, 0.4, 0.4, 0.08);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, table.getX(), table.getY() + 0.5, table.getZ(), 12, 0.3, 0.3, 0.3, 0.0);
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, table.getX(), table.getY() + 0.4, table.getZ(), 8, 0.25, 0.25, 0.25, 0.05);
                Dragthings.LOGGER.info("Enchant ritual: {} at level {} (table #{})", targetStack.getHoverName().getString(), enchLvl, tableId);
            }
        }
    }

    private static void sendProgress(ServerPlayer player, int tableId, int orbCount) {
        if (ServerPlayNetworking.canSend(player, RitualProgressPayload.TYPE))
            ServerPlayNetworking.send(player, new RitualProgressPayload(tableId, orbCount));
    }
}