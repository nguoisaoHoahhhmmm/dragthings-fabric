package dragthings.ritual;

import dragthings.Dragthings;
import dragthings.config.GameplayConfig;
import dragthings.config.RitualConfig;
import dragthings.network.RitualProgressPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enchant ritual: drag an Enchanting Table next to an enchantable item and
 * some lapis, and the dragging player pays XP LEVELS to enchant the item.
 *
 * Flow (all server side):
 *   1. every tick, check the table has an enchantable item and lapis nearby
 *      and the player has enough levels (creative players always do)
 *   2. when all three are met, start a short "channel" and tell clients, who
 *      play the XP orbs flying from the player into the table
 *   3. when the channel ends, enchant the item and take the levels and lapis
 *   4. if anything is lost mid-channel (item moved away, table released),
 *      cancel it and charge nothing
 */
public final class EnchantRitual {

    // ── Phases and flags sent to the client (RitualProgressPayload) ───────
    public static final int PHASE_HIDE    = -1; // stop showing the HUD
    public static final int PHASE_IDLE    = 0;  // flags changed
    public static final int PHASE_CHANNEL = 1;  // ritual started, play the orbs
    public static final int PHASE_DONE    = 2;  // item was enchanted
    public static final int PHASE_CANCEL  = 3;  // channel was interrupted

    public static final int FLAG_TARGET = 1;
    public static final int FLAG_LAPIS  = 2;
    public static final int FLAG_XP     = 4;
    public static final int FLAGS_READY = FLAG_TARGET | FLAG_LAPIS | FLAG_XP;

    // ── Tuning: rituals.json, section "enchant" ──────────────────────────
    private static RitualConfig.Enchant cfg() { return GameplayConfig.rituals().enchant; }
    private static double radius() { return cfg().radius; }
    private static int levelCost() { return cfg().levelCost; }
    private static int enchantPower() { return cfg().enchantPower; }
    private static int lapisCost() { return cfg().lapisCost; }
    private static int cooldownTicks() { return cfg().cooldownTicks; }
    /** Ticks from the orbs leaving the player to the item being enchanted. */
    public static final int CHANNEL_TICKS = 16;

    private static final class State {
        ServerLevel level;
        ServerPlayer player;
        int channel = -1;      // ticks left, -1 when not channeling
        int cooldown = 0;
        int lastFlags = -99;   // last flags sent to the dragger; -99 forces a resend

        State(ServerLevel l, ServerPlayer p) { level = l; player = p; }
    }

    private static final Map<Integer, State> activeTables = new ConcurrentHashMap<>();

    private EnchantRitual() {}

    /** Expose the set of active enchanting table entity IDs. */
    public static java.util.Set<Integer> activeTableIds() { return activeTables.keySet(); }

    /** Called on every drag sync packet, so it must not reset a running ritual. */
    public static void onStartDrag(int entityId, ServerLevel level, ServerPlayer player) {
        if (!cfg().enabled) return;
        State existing = activeTables.get(entityId);
        if (existing != null) {
            existing.level = level;
            existing.player = player;
            return;
        }
        activeTables.put(entityId, new State(level, player));
        // Mystical hum only on first pickup, not on every sync packet.
        if (level.getEntity(entityId) instanceof ItemEntity table) {
            level.playSound(null, table.blockPosition(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.5f, 0.6f);
        }
    }

    public static void onRelease(int entityId, ServerPlayer player) {
        State st = activeTables.remove(entityId);
        if (!player.isRemoved()) {
            if (st != null && st.channel >= 0 && st.level.getEntity(entityId) instanceof ItemEntity table) {
                broadcast(table, player, PHASE_CANCEL, 0);
            }
            send(player, new RitualProgressPayload(entityId, player.getId(), PHASE_HIDE, 0, levelCost()));
        }
    }

    public static void tick() {
        if (activeTables.isEmpty()) return;

        Iterator<Map.Entry<Integer, State>> it = activeTables.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, State> entry = it.next();
            int tableId = entry.getKey();
            State st = entry.getValue();
            ServerLevel level = st.level;
            ServerPlayer player = st.player;

            // The player disconnected mid-ritual: drop the entry so it does
            // not leak (the table item itself can outlive their connection).
            if (player.isRemoved()) {
                it.remove();
                continue;
            }

            if (!(level.getEntity(tableId) instanceof ItemEntity table) || !table.isAlive()) {
                it.remove();
                send(player, new RitualProgressPayload(tableId, player.getId(), PHASE_HIDE, 0, levelCost()));
                continue;
            }

            // What is next to the table
            AABB box = new AABB(table.position(), table.position()).inflate(radius());
            ItemEntity lapis = null;
            ItemEntity target = null;
            for (ItemEntity nearby : level.getEntitiesOfClass(ItemEntity.class, box)) {
                if (nearby == table) continue;
                ItemStack s = nearby.getItem();
                if (lapis == null && s.is(Items.LAPIS_LAZULI) && s.getCount() >= lapisCost()) lapis = nearby;
                else if (target == null && canEnchant(s)) target = nearby;
            }

            boolean creative = player.getAbilities().instabuild;
            boolean hasXp = creative || player.experienceLevel >= levelCost();
            int flags = (target != null ? FLAG_TARGET : 0)
                    | (lapis != null ? FLAG_LAPIS : 0)
                    | (hasXp ? FLAG_XP : 0);
            boolean ready = flags == FLAGS_READY;

            if (st.cooldown > 0) st.cooldown--;

            if (st.channel >= 0) {
                if (!ready) {
                    // Lost something mid-channel: cancel, nothing is charged.
                    st.channel = -1;
                    st.lastFlags = -99;
                    broadcast(table, player, PHASE_CANCEL, flags);
                } else if (--st.channel <= 0) {
                    finish(level, player, table, lapis, target, creative);
                    st.channel = -1;
                    st.cooldown = cooldownTicks();
                    st.lastFlags = -99;
                }
            } else if (ready && st.cooldown <= 0) {
                st.channel = CHANNEL_TICKS;
                broadcast(table, player, PHASE_CHANNEL, flags);
                level.playSound(null, table.blockPosition(),
                        SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.7f, 1.0f);
            }

            // Keep the dragger's HUD in step with what is next to the table.
            if (st.channel < 0 && flags != st.lastFlags) {
                st.lastFlags = flags;
                send(player, new RitualProgressPayload(tableId, player.getId(), PHASE_IDLE, flags, levelCost()));
            }
        }
    }

    /**
     * Only something that has NOT been enchanted yet can be a target. Without
     * this, a stack of lapis would re-enchant the same item over and over.
     */
    private static boolean canEnchant(ItemStack s) {
        return !s.is(Items.LAPIS_LAZULI)
                && !s.is(Items.ENCHANTED_BOOK)
                && !s.has(DataComponents.STORED_ENCHANTMENTS)
                && s.isEnchantable();
    }

    private static void finish(ServerLevel level, ServerPlayer player, ItemEntity table,
                               ItemEntity lapis, ItemEntity target, boolean creative) {
        ItemStack lapisStack = lapis.getItem();
        ItemStack targetStack = target.getItem();

        // Only what an enchanting table can give: no curses, no Mending or
        // other treasure enchantments (an empty Optional would allow ALL of them).
        Optional<HolderSet<Enchantment>> pool = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getTag(EnchantmentTags.IN_ENCHANTING_TABLE)
                .map(named -> (HolderSet<Enchantment>) named);
        ItemStack enchanted = EnchantmentHelper.enchantItem(RandomSource.create(), targetStack.copy(),
                enchantPower(), level.registryAccess(), pool);
        target.setItem(enchanted);

        lapisStack.shrink(lapisCost());
        if (lapisStack.isEmpty()) lapis.discard(); else lapis.setItem(lapisStack);

        if (!creative) player.giveExperienceLevels(-levelCost());

        level.playSound(null, table.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
        // Layered magical finish: glyph swirl + ethereal whoosh
        level.playSound(null, table.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.5f);
        level.playSound(null, table.blockPosition(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.PLAYERS, 0.5f, 1.2f);

        broadcast(table, player, PHASE_DONE, 0);
        Dragthings.LOGGER.debug("Enchant ritual: {} at power {} for {} levels (table #{})",
                targetStack.getHoverName().getString(), enchantPower(), creative ? 0 : levelCost(), table.getId());
    }

    /** To the dragger, and to everyone tracking the table, so they can all see the orbs. */
    private static void broadcast(ItemEntity table, ServerPlayer owner, int phase, int flags) {
        RitualProgressPayload payload =
                new RitualProgressPayload(table.getId(), owner.getId(), phase, flags, levelCost());
        send(owner, payload);
        for (ServerPlayer other : PlayerLookup.tracking(table)) {
            if (other != owner) send(other, payload);
        }
    }

    private static void send(ServerPlayer player, RitualProgressPayload payload) {
        if (ServerPlayNetworking.canSend(player, RitualProgressPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}