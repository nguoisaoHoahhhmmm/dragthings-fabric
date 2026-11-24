package dragthings.server;

import dragthings.ItemNameResolver;
import dragthings.network.SetCraftingTargetPayload;
import dragthings.ritual.CraftingGridRitual;
import dragthings.ritual.CraftingRitual;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/** Server handling for SetCraftingTargetPayload (crafting GUI target). */
public final class CraftingTargetHandler {

    private CraftingTargetHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SetCraftingTargetPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player == null) return;

                    var resolved = ItemNameResolver.resolve(payload.query());
                    if (resolved.isEmpty()) {
                        player.displayClientMessage(
                                Component.literal("Unknown item: \"" + payload.query() + "\""),
                                true); // action bar
                        return;
                    }
                    Item target = resolved.get();

                    if (payload.isBlockTarget()) {
                        if (!(player.level() instanceof ServerLevel sl)) return;
                        GlobalPos tableKey = GlobalPos.of(sl.dimension(), payload.blockPos());
                        CraftingGridRitual.setGuiTarget(sl, player, tableKey, target);
                    } else {
                        CraftingRitual.setGuiTarget(payload.entityId(), target);
                    }

                    player.displayClientMessage(
                            Component.literal("Crafting target set: "
                                    + target.getDescription().getString()),
                            true); // action bar
                }));
    }
}