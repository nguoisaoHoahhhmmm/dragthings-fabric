package dragthings.server;

import dragthings.network.ArmGrabPayload;
import dragthings.network.ArmStatePayload;
import dragthings.network.BundleEjectPayload;
import dragthings.network.DragItemPayload;
import dragthings.network.EatItemPayload;
import dragthings.network.EnderChestSyncPayload;
import dragthings.network.MobDragPayload;
import dragthings.network.PlaceBlockPayload;
import dragthings.network.RitualProgressPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers every payload type. Must run before any receiver is registered. */
public final class ModPayloads {

    private ModPayloads() {}

    public static void register() {
        // Client -> Server
        PayloadTypeRegistry.playC2S().register(DragItemPayload.TYPE, DragItemPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MobDragPayload.TYPE, MobDragPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlaceBlockPayload.TYPE, PlaceBlockPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EatItemPayload.TYPE, EatItemPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ArmGrabPayload.TYPE, ArmGrabPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BundleEjectPayload.TYPE, BundleEjectPayload.CODEC);

        // Server -> Client
        PayloadTypeRegistry.playS2C().register(RitualProgressPayload.TYPE, RitualProgressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ArmStatePayload.TYPE, ArmStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EnderChestSyncPayload.TYPE, EnderChestSyncPayload.CODEC);
    }
}