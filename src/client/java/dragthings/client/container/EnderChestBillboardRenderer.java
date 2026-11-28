package dragthings.client.container;

import dragthings.client.ItemDragHandler;
import dragthings.network.EnderChestSyncPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Ender chest preview, data side.
 *
 * An ender chest's contents only exist on the server, so EnderChestRitual sends
 * them (EnderChestSyncPayload) when the drag starts and after every change.
 * This class keeps the latest copy and hands it to ContainerBillboardRenderer,
 * which draws the panel for a dragged Ender Chest exactly like it does for a
 * Chest, so both share the same switches (containerPreview in the config).
 *
 * The copy is kept for as long as an ender chest is being dragged (so it is
 * still there when Shift is pressed later) and forgotten when the drag ends.
 */
public final class EnderChestBillboardRenderer {

    /** Latest contents from the server; null until the first packet of this drag arrives. */
    private static List<ItemStack> synced = null;

    private EnderChestBillboardRenderer() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(EnderChestSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> synced = payload.stacks()));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ItemEntity dragged = ItemDragHandler.getDraggedItem();
            if (dragged == null || !isEnderChest(dragged.getItem())) synced = null;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> synced = null);
    }

    public static boolean isEnderChest(ItemStack stack) {
        return stack.is(Items.ENDER_CHEST);
    }

    /** The synced contents, or null when nothing has arrived for this drag yet. */
    public static List<ItemStack> contents() {
        return synced;
    }
}