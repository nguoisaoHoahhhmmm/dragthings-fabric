package dragthings.server;

import dragthings.Dragthings;
import dragthings.network.DragItemPayload;
import dragthings.tool.DragToolHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server handling for DragItemPayload: position sync while dragging, ritual
 * registration, weapon-swing damage, tool interaction, and release.
 */
public final class DragItemHandler {

    private DragItemHandler() {}

    private static final Map<Integer, Integer> hitCooldown = new ConcurrentHashMap<>();

    private static final double MIN_SWING_DISTANCE   = 0.5;
    private static final double SWING_HIT_RADIUS     = 1.0;
    private static final float  BASE_SWING_DAMAGE    = 3.0f;
    private static final float  MAX_SWING_MULTIPLIER = 3.0f;
    private static final int    HIT_COOLDOWN_TICKS   = 10;

    private static boolean isWeapon(ItemStack s) {
        return s.getItem() instanceof SwordItem
                || s.getItem() instanceof AxeItem
                || s.getItem() instanceof TridentItem;
    }

    /** Called every server tick by ServerTickHandler. */
    public static void tickCooldowns() {
        hitCooldown.replaceAll((id, ticks) -> ticks - 1);
        hitCooldown.values().removeIf(ticks -> ticks <= 0);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DragItemPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player == null || !(player.level() instanceof ServerLevel level)) return;

                    Entity entity = level.getEntity(payload.entityId());
                    if (!(entity instanceof ItemEntity item)) return;

                    if (payload.isDragging()) handleDrag(player, level, item, payload);
                    else handleRelease(player, item, payload);
                }));

        // A disconnect never sends the release packet. Drop every session
        // owned by that player before their items are left floating.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                DragSessionRegistry.releaseAll(handler.player.getUUID(), DragRitualSupport::release));
    }

    private static void handleDrag(ServerPlayer player, ServerLevel level,
                                   ItemEntity item, DragItemPayload payload) {
        int id = payload.entityId();
        Vec3 requested = new Vec3(payload.x(), payload.y(), payload.z());
        DragSession session = DragSessionRegistry.getOwned(id, player);

        if (session == null) {
            // Somebody else is already dragging this entity: never take it.
            if (DragSessionRegistry.get(id) != null) return;

            double itemDistSq = player.distanceToSqr(item.position());
            if (itemDistSq > DragConstants.MAX_DRAG_DISTANCE_SQ) {
                Dragthings.LOGGER.warn("Player {} tried to drag item too far ({} blocks²)",
                        player.getName().getString(), (int) itemDistSq);
                return;
            }

            session = DragSessionRegistry.start(player, id, DragSession.Kind.ITEM, level, item);
            if (session == null) return;

            // The first packet carries a (0,0,0) placeholder, so keep the
            // item at its server position and only remember original state.
            item.setUnlimitedLifetime();
            DragRitualSupport.register(session, player, level, item, payload.isSneaking());
            return;
        }

        if (!DragValidation.validMove(player, requested, session)) {
            Dragthings.LOGGER.warn("Rejected invalid drag packet from {} for item #{}",
                    player.getName().getString(), id);
            return;
        }
        session.setLastServerPos(requested);

        item.setPos(requested.x, requested.y, requested.z);
        item.setNoGravity(true);
        item.noPhysics = true;
        item.setDeltaMovement(0, 0, 0);
        item.setPickUpDelay(5);

        // Weapon swing damage
        Vec3 newPos = item.position();
        Vec3 prevPos = session.takeSwingPos(newPos);
        if (prevPos != null && isWeapon(item.getItem())) {
            swingDamage(level, player, newPos, prevPos);
        }

        // Tool interaction: per-tool mining speed, sneak-gated actions, etc.
        DragToolHandler.handle(level, player, item, newPos, prevPos, payload.isSneaking());
    }

    private static void swingDamage(ServerLevel sl, ServerPlayer player, Vec3 newPos, Vec3 prevPos) {
        double moved = newPos.distanceTo(prevPos);
        if (moved < MIN_SWING_DISTANCE) return;

        Vec3 dir = newPos.subtract(prevPos).normalize();
        for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(newPos, newPos).inflate(SWING_HIT_RADIUS))) {
            // A dragged weapon can hit mobs AND other players. Only the owner
            // is exempt, so you cannot damage yourself with your own swing.
            if (target == player || hitCooldown.containsKey(target.getId())) continue;

            float dmg = BASE_SWING_DAMAGE * (float) Math.min(MAX_SWING_MULTIPLIER, moved / MIN_SWING_DISTANCE);
            target.hurt(player.damageSources().playerAttack(player), dmg);
            target.knockback(0.4, -dir.x, -dir.z);
            hitCooldown.put(target.getId(), HIT_COOLDOWN_TICKS);
            sl.sendParticles(ParticleTypes.CRIT, target.getX(),
                    target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    8, 0.2, 0.2, 0.2, 0.05);
            sl.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, 0.8f, 1f);
        }
    }

    private static void handleRelease(ServerPlayer player, ItemEntity item, DragItemPayload payload) {
        int id = payload.entityId();
        DragSession session = DragSessionRegistry.getOwned(id, player);
        if (session == null) return;

        DragRitualSupport.release(session);
        // Restores noGravity/noPhysics and the item's original lifetime.
        DragSessionRegistry.release(id);

        item.setDeltaMovement(DragValidation.clampThrow(
                new Vec3(payload.vx(), payload.vy(), payload.vz())));
        item.setPickUpDelay(10);
    }

    /** Used by placement/eating when the item disappears without a release packet. */
    public static void cleanupWithoutThrow(ServerPlayer player, int entityId) {
        DragSession session = DragSessionRegistry.getOwned(entityId, player);
        if (session == null) return;
        DragRitualSupport.release(session);
        DragSessionRegistry.release(entityId);
    }
}
