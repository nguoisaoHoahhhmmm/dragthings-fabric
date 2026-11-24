package dragthings.server;

import dragthings.Dragthings;
import dragthings.network.MobDragPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server handling for MobDragPayload, plus the disconnect safety net. */
public final class MobDragServer {

    private MobDragServer() {}

    private static final Map<Integer, Boolean> mobPrevInvulnerable = new ConcurrentHashMap<>();

    // If a player disconnects mid-drag the mob would stay permanently
    // noAi + invulnerable. Keyed by player UUID since entity ids are not
    // stable across a reconnect.
    private static final Map<UUID, Integer> playerDraggedMob = new ConcurrentHashMap<>();

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MobDragPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player == null || player.level() == null) return;

                    Entity entity = player.level().getEntity(payload.entityId());
                    // Never allow Player entities to be grabbed; only
                    // LivingEntity targets make sense here at all.
                    if (!(entity instanceof LivingEntity mob) || entity instanceof Player) return;

                    double distSq = player.distanceToSqr(mob.getX(), mob.getY(), mob.getZ());
                    if (distSq > DragConstants.MAX_DRAG_DISTANCE_SQ) {
                        Dragthings.LOGGER.warn("Player {} tried to drag mob too far ({} blocks²)",
                                player.getName().getString(), (int) distSq);
                        return;
                    }

                    if (payload.isDragging()) {
                        mob.setPos(payload.x(), payload.y(), payload.z());
                        mob.setDeltaMovement(0, 0, 0);
                        if (mob instanceof Mob aiMob) aiMob.setNoAi(true);
                        mob.setNoGravity(true);
                        mob.noPhysics = true;
                        mob.fallDistance = 0;

                        if (payload.invulnerable()) {
                            // Remember the ORIGINAL flag once, so release
                            // restores it instead of forcing false.
                            mobPrevInvulnerable.putIfAbsent(mob.getId(), mob.isInvulnerable());
                            mob.setInvulnerable(true);
                        }

                        playerDraggedMob.put(player.getUUID(), mob.getId());
                    } else {
                        releaseMob(mob, new Vec3(payload.vx(), payload.vy(), payload.vz()));
                        playerDraggedMob.remove(player.getUUID());
                    }
                }));

        // Disconnect mid-drag: the client never sends its release packet, so
        // release the mob here with zero throw velocity.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            if (player == null) return;

            Integer mobId = playerDraggedMob.remove(player.getUUID());
            if (mobId == null) return;

            Entity entity = player.level().getEntity(mobId);
            if (entity instanceof LivingEntity mob) {
                releaseMob(mob, Vec3.ZERO);
            }
        });
    }

    /**
     * Restores a mob to its normal state: AI, gravity and physics back on,
     * the pre-grab invulnerable flag restored, and a (possibly zero) throw.
     */
    private static void releaseMob(LivingEntity mob, Vec3 throwVelocity) {
        if (mob instanceof Mob aiMob) aiMob.setNoAi(false);
        mob.setNoGravity(false);
        mob.noPhysics = false;

        Boolean prevInvuln = mobPrevInvulnerable.remove(mob.getId());
        mob.setInvulnerable(prevInvuln != null && prevInvuln);

        Vec3 throwVec = throwVelocity;
        if (throwVec.length() > DragConstants.MAX_THROW_SPEED) {
            throwVec = throwVec.normalize().scale(DragConstants.MAX_THROW_SPEED);
        }
        mob.setDeltaMovement(throwVec);
    }
}