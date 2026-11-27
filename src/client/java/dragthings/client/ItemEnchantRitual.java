package dragthings.client;

import dragthings.client.gui.hud.EnchantRitualHud;
import dragthings.network.RitualProgressPayload;
import dragthings.ritual.EnchantRitual;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Enchant ritual, client side: init + state.
 *
 * Receives RitualProgressPayload from the server and keeps the few values the
 * other two files need:
 *   - EnchantRitualHud : the requirements panel and progress bar
 *   - EnchantOrbFx     : the XP orbs flying from the player into the table
 *
 * All logic (what is next to the table, XP cost, enchanting) is on the server.
 */
public final class ItemEnchantRitual {

    private static final int DONE_FLASH_TICKS = 34;

    private static int tableId = -1;
    private static int flags = 0;
    private static int levelCost = 0;
    private static int channelTicks = -1;  // -1 when not channeling
    private static int doneTicks = 0;      // counts down after an enchant

    private ItemEnchantRitual() {}

    public static void init() {
        EnchantRitualHud.init();
        EnchantOrbFx.init();

        ClientPlayNetworking.registerGlobalReceiver(RitualProgressPayload.TYPE, (payload, context) ->
                context.client().execute(() -> onPayload(payload)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) { reset(); return; }
            if (channelTicks >= 0) channelTicks++;
            if (doneTicks > 0) doneTicks--;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            reset();
            EnchantOrbFx.clear();
        });
    }

    private static void onPayload(RitualProgressPayload p) {
        boolean mine = isMine(p);
        switch (p.phase()) {
            case EnchantRitual.PHASE_HIDE -> {
                if (p.tableEntityId() == tableId) reset();
                EnchantOrbFx.cancel(p.tableEntityId());
            }
            case EnchantRitual.PHASE_IDLE -> {
                if (!mine) return;
                tableId = p.tableEntityId();
                flags = p.flags();
                levelCost = p.levelCost();
            }
            case EnchantRitual.PHASE_CHANNEL -> {
                if (mine) {
                    tableId = p.tableEntityId();
                    flags = p.flags();
                    levelCost = p.levelCost();
                    channelTicks = 0;
                    doneTicks = 0;
                }
                EnchantOrbFx.spawn(p.tableEntityId(), p.playerId());
            }
            case EnchantRitual.PHASE_DONE -> {
                if (mine) { channelTicks = -1; doneTicks = DONE_FLASH_TICKS; }
                EnchantOrbFx.finish(p.tableEntityId());
            }
            case EnchantRitual.PHASE_CANCEL -> {
                if (mine) { channelTicks = -1; flags = p.flags(); }
                EnchantOrbFx.cancel(p.tableEntityId());
            }
            default -> { }
        }
    }

    /** True when this packet is about the table the local player is dragging. */
    private static boolean isMine(RitualProgressPayload p) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player != null && p.playerId() == mc.player.getId();
    }

    private static void reset() {
        tableId = -1;
        flags = 0;
        channelTicks = -1;
        doneTicks = 0;
    }

    // ── Read-only state for the HUD ───────────────────────────────────────

    /** True while the local player drags the table the server is tracking. */
    public static boolean isActive() {
        ItemEntity dragged = ItemDragHandler.getDraggedItem();
        return dragged != null && dragged.getId() == tableId;
    }

    public static int flags() { return flags; }
    public static int levelCost() { return levelCost; }
    public static boolean isChanneling() { return channelTicks >= 0; }
    public static int channelTicks() { return channelTicks; }
    public static int doneTicks() { return doneTicks; }
    public static int doneFlashTicks() { return DONE_FLASH_TICKS; }
}