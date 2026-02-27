package dragthings;

import dragthings.network.DragItemPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dragthings implements ModInitializer {

    public static final String MOD_ID = "physicitem";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final double MAX_DRAG_DISTANCE_SQ = 8.0 * 8.0; // 8 blocks

    @Override
    public void onInitialize() {
        LOGGER.info("ItemPhysic initialized!");

        PayloadTypeRegistry.playC2S().register(DragItemPayload.TYPE, DragItemPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(DragItemPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player == null || player.level() == null) return;

                Entity entity = player.level().getEntity(payload.entityId());
                if (!(entity instanceof ItemEntity item)) return;

                if (payload.isDragging()) {
                    // FIX #1: Validate player is close enough — prevents remote item teleport exploit
                    double distSq = player.distanceToSqr(payload.x(), payload.y(), payload.z());
                    if (distSq > MAX_DRAG_DISTANCE_SQ) {
                        LOGGER.warn("Player {} tried to drag item too far away ({} blocks sq)",
                                player.getName().getString(), (int) distSq);
                        return;
                    }


                    double itemDistSq = player.distanceToSqr(item.position().x, item.position().y, item.position().z);
                    if (itemDistSq > MAX_DRAG_DISTANCE_SQ) {
                        return;
                    }

                    item.setPos(payload.x(), payload.y(), payload.z());
                    item.setNoGravity(true);
                    item.noPhysics = true;
                    item.setDeltaMovement(0, 0, 0);
                } else {

                    item.noPhysics = false;
                    item.setNoGravity(false);
                }
            });
        });
    }
}