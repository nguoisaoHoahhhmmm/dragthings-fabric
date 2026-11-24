package dragthings.server;

import dragthings.network.ArmGrabPayload;
import dragthings.network.ArmStatePayload;
import dragthings.network.CraftingProgressPayload;
import dragthings.network.DragItemPayload;
import dragthings.network.EatItemPayload;
import dragthings.network.MobDragPayload;
import dragthings.network.PlaceBlockPayload;
import dragthings.network.RitualProgressPayload;
import dragthings.network.SetCraftingTargetPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers every payload type. Must run before any receiver is registered. */
public final class ModPayloads {

    private ModPayloads() {}

    public static void register() {
        // Client -> Server
        PayloadTypeRegistry.playC2S().register(DragItemPayload.TYPE, DragItemPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MobDragPayload.TYPE, MobDragPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlaceBlockPayload.TYPE, PlaceBlockPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetCraftingTargetPayload.TYPE, SetCraftingTargetPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EatItemPayload.TYPE, EatItemPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ArmGrabPayload.TYPE, ArmGrabPayload.CODEC);

        // Server -> Client
        PayloadTypeRegistry.playS2C().register(RitualProgressPayload.TYPE, RitualProgressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CraftingProgressPayload.TYPE, CraftingProgressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ArmStatePayload.TYPE, ArmStatePayload.CODEC);
    }
}