package dragthings.client;

import dragthings.network.BundleEjectPayload;
import dragthings.ritual.BundleRitual;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Bundle, client side: while a bundle is being dragged, the drop key (Q,
 * whatever it is bound to) takes items out of it instead of dropping the
 * held hotbar item.
 *
 *   Q        one stack out
 *   Ctrl + Q the whole bundle out
 *
 * Runs at the START of the tick, before vanilla handles its key bindings,
 * so the key press is used up here and the vanilla drop never happens.
 */
public final class BundleEjectKey {

    private BundleEjectKey() {}

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.screen != null) return;

            ItemEntity dragged = ItemDragHandler.getDraggedItem();
            if (dragged == null || !BundleRitual.isBundle(dragged.getItem())) return;

            while (mc.options.keyDrop.consumeClick()) {
                ClientPlayNetworking.send(new BundleEjectPayload(dragged.getId(), Screen.hasControlDown()));
            }
        });
    }
}