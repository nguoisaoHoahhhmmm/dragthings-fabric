package dragthings;

import dragthings.network.ArmGrabPayload;
import dragthings.network.ArmStatePayload;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the stretched-arm feature. The arm itself is purely visual
 * and drawn on each client; the server only remembers who is reaching for
 * which item and relays that to every other player who can see them.
 *
 * Call {@link #init()} once from Dragthings#onInitialize, after the payload
 * types have been registered.
 */
public final class ArmSync {

    private ArmSync() {}

    // Same slack as Dragthings.MAX_DRAG_DISTANCE_SQ: leader reach (12) plus
    // generous headroom, still blocks absurd forged distances.
    private static final double MAX_DIST_SQ = 24.0 * 24.0;

    /** player UUID -> item entity id their arm is currently reaching for. */
    private static final Map<UUID, Integer> ACTIVE = new ConcurrentHashMap<>();

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(ArmGrabPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player == null) return;

                    int itemId = payload.itemId();
                    if (itemId >= 0) {
                        Entity e = player.level().getEntity(itemId);
                        if (!(e instanceof ItemEntity item)
                                || !item.isAlive()
                                || player.distanceToSqr(item) > MAX_DIST_SQ) {
                            itemId = -1; // forged or stale request: treat as release
                        }
                    }
                    set(player, itemId);
                }));

        // A disconnecting player's entity disappears from every client's
        // level, and the client renderer drops arms whose player is gone, so
        // no broadcast is needed here — only forget the server-side entry.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) ACTIVE.remove(handler.player.getUUID());
        });

        // Someone walks into range of a player who is already mid-grab: they
        // missed the original packet, so catch them up.
        EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
            if (!(tracked instanceof ServerPlayer grabber)) return;
            Integer itemId = ACTIVE.get(grabber.getUUID());
            if (itemId != null && ServerPlayNetworking.canSend(viewer, ArmStatePayload.TYPE)) {
                ServerPlayNetworking.send(viewer, new ArmStatePayload(grabber.getId(), itemId));
            }
        });
    }

    private static void set(ServerPlayer player, int itemId) {
        if (itemId >= 0) ACTIVE.put(player.getUUID(), itemId);
        else ACTIVE.remove(player.getUUID());

        ArmStatePayload out = new ArmStatePayload(player.getId(), itemId);
        for (ServerPlayer other : PlayerLookup.tracking(player)) {
            if (other != player && ServerPlayNetworking.canSend(other, ArmStatePayload.TYPE)) {
                ServerPlayNetworking.send(other, out);
            }
        }
    }
}