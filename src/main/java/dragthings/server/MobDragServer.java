package dragthings.server;

import dragthings.Dragthings;
import dragthings.network.MobDragPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Server handling for MobDragPayload, plus ownership and disconnect safety.
 */
public final class MobDragServer {

    private MobDragServer() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MobDragPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player == null || !(player.level() instanceof ServerLevel level)) return;

                    Entity entity = level.getEntity(payload.entityId());
                    // Never allow Player entities to be grabbed.
                    if (!(entity instanceof LivingEntity mob) || entity instanceof Player) return;

                    DragSession session = DragSessionRegistry.getOwned(payload.entityId(), player);

                    if (payload.isDragging()) {
                        if (session == null) {
                            if (DragSessionRegistry.get(payload.entityId()) != null) return;
                            double distSq = player.distanceToSqr(mob.getX(), mob.getY(), mob.getZ());
                            if (distSq > DragConstants.MAX_DRAG_DISTANCE_SQ) {
                                Dragthings.LOGGER.warn("Player {} tried to drag mob too far ({} blocks²)",
                                        player.getName().getString(), (int) distSq);
                                return;
                            }
                            session = DragSessionRegistry.start(
                                    player, payload.entityId(), DragSession.Kind.MOB, level, mob);
                            if (session == null) return;

                            // Remember the ORIGINAL flag once, so release
                            // restores it instead of forcing false.
                            if (payload.invulnerable()) mob.setInvulnerable(true);
                            return;
                        }

                        Vec3 requested = new Vec3(payload.x(), payload.y(), payload.z());
                        if (!DragValidation.validMove(player, requested, session)) {
                            Dragthings.LOGGER.warn("Rejected invalid mob drag packet from {} for mob #{}",
                                    player.getName().getString(), payload.entityId());
                            return;
                        }
                        session.setLastServerPos(requested);

                        mob.setPos(requested.x, requested.y, requested.z);
                        mob.setDeltaMovement(0, 0, 0);
                        if (mob instanceof Mob aiMob) aiMob.setNoAi(true);
                        mob.setNoGravity(true);
                        mob.noPhysics = true;
                        mob.fallDistance = 0;
                        if (payload.invulnerable()) mob.setInvulnerable(true);
                        return;
                    }

                    // Release: only the owner may change this mob.
                    if (session == null) return;
                    DragSessionRegistry.release(payload.entityId());
                    mob.setDeltaMovement(DragValidation.clampThrow(
                            new Vec3(payload.vx(), payload.vy(), payload.vz())));
                    mob.fallDistance = 0;
                }));
    }
}
