package dragthings.server;

import dragthings.ContainerUtil;
import dragthings.Dragthings;
import dragthings.network.DragItemPayload;
import dragthings.ritual.AnvilRitual;
import dragthings.ritual.ChestLootRitual;
import dragthings.ritual.CraftingGridRitual;
import dragthings.ritual.CraftingRitual;
import dragthings.ritual.EnchantRitual;
import dragthings.ritual.EnderChestRitual;
import dragthings.ritual.FurnaceRitual;
import dragthings.ritual.GrindstoneRitual;
import dragthings.ritual.SmithingRitual;
import dragthings.tool.DragToolHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server handling for DragItemPayload: position sync while dragging,
 * ritual registration, weapon-swing damage, tool interaction, and release.
 */
public final class DragItemHandler {

    private DragItemHandler() {}

    // ── Weapon swing ──────────────────────────────────────────────────────
    private static final Map<Integer, Vec3>    lastDragPos    = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> mobHitCooldown = new ConcurrentHashMap<>();

    private static final double MIN_SWING_DISTANCE   = 0.5;
    private static final double SWING_HIT_RADIUS     = 1.0;
    private static final float  BASE_SWING_DAMAGE    = 3.0f;
    private static final float  MAX_SWING_MULTIPLIER = 3.0f;
    private static final int    MOB_HIT_COOLDOWN     = 10;

    private static boolean isWeapon(ItemStack s) {
        return s.getItem() instanceof SwordItem
                || s.getItem() instanceof AxeItem
                || s.getItem() instanceof TridentItem;
    }

    /** Called every server tick by ServerTickHandler. */
    public static void tickCooldowns() {
        mobHitCooldown.replaceAll((id, ticks) -> ticks - 1);
        mobHitCooldown.values().removeIf(ticks -> ticks <= 0);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DragItemPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player == null || player.level() == null) return;

                    Entity entity = player.level().getEntity(payload.entityId());
                    if (!(entity instanceof ItemEntity item)) return;

                    if (payload.isDragging()) handleDrag(player, item, payload);
                    else handleRelease(player, item, payload);
                }));
    }

    private static void handleDrag(ServerPlayer player, ItemEntity item, DragItemPayload payload) {
        // Validate against the item entity's own server-side position. The
        // payload x/y/z is a (0,0,0) placeholder on the very first "start
        // drag" packet, so checking it would abort almost every drag start
        // before ritual registration ever ran.
        double itemDistSq = player.distanceToSqr(
                item.position().x, item.position().y, item.position().z);
        if (itemDistSq > DragConstants.MAX_DRAG_DISTANCE_SQ) {
            Dragthings.LOGGER.warn("Player {} tried to drag item too far ({} blocks²)",
                    player.getName().getString(), (int) itemDistSq);
            return;
        }

        // Crafting-grid capture runs BEFORE the normal position sync. If it
        // takes ownership this tick, setPos is skipped.
        ServerLevel gridLevel = (player.level() instanceof ServerLevel sLevel) ? sLevel : null;
        Vec3 gridTargetPos = new Vec3(payload.x(), payload.y(), payload.z());
        boolean gridHandled = gridLevel != null && CraftingGridRitual.tryHandleDrag(
                gridLevel, player, item, gridTargetPos, payload.isSneaking());
        if (gridLevel != null) {
            CraftingGridRitual.showGridPreview(gridLevel, player, gridTargetPos, payload.isSneaking());
        }

        if (!gridHandled) {
            item.setPos(payload.x(), payload.y(), payload.z());
        }
        item.setNoGravity(true);
        item.noPhysics = true;
        item.setDeltaMovement(0, 0, 0);
        item.setPickUpDelay(5);
        // Anything that has ever been dragged should never auto-despawn;
        // idempotent, so harmless to call every tick.
        item.setUnlimitedLifetime();

        registerRituals(player, item, payload);

        // Weapon swing damage
        Vec3 newPos  = item.position();
        Vec3 prevPos = lastDragPos.get(payload.entityId());
        lastDragPos.put(payload.entityId(), newPos);

        if (prevPos != null && isWeapon(item.getItem()) && player.level() instanceof ServerLevel sl) {
            swingDamage(sl, player, newPos, prevPos);
        }

        // Tool interaction: per-tool mining speed, sneak-gated actions, etc.
        if (player.level() instanceof ServerLevel sl) {
            DragToolHandler.handle(sl, player, item, newPos, prevPos, payload.isSneaking());
        }
    }

    private static void registerRituals(ServerPlayer player, ItemEntity item, DragItemPayload payload) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        int id = payload.entityId();

        if (item.getItem().is(Items.ENCHANTING_TABLE)) EnchantRitual.onStartDrag(id, sl, player);
        if (item.getItem().is(Items.SMITHING_TABLE))   SmithingRitual.onStartDrag(id, sl, player);
        if (item.getItem().is(Items.GRINDSTONE))       GrindstoneRitual.onStartDrag(id, sl, player);
        if (item.getItem().is(Items.FURNACE) || item.getItem().is(Items.BLAST_FURNACE) || item.getItem().is(Items.SMOKER))
            FurnaceRitual.onStartDrag(id, sl, player);
        if (item.getItem().is(Items.ANVIL) || item.getItem().is(Items.CHIPPED_ANVIL) || item.getItem().is(Items.DAMAGED_ANVIL))
            AnvilRitual.onStartDrag(id, sl);
        if (item.getItem().is(Items.CRAFTING_TABLE))
            CraftingRitual.onStartDrag(id, sl, player);
        if (ContainerUtil.isContainerItem(item.getItem())) {
            Dragthings.LOGGER.info("ChestLootRitual trigger matched for item={} entity={}",
                    item.getItem().getItem(), id);
            ChestLootRitual.onStartDrag(id, sl, player, payload.isSneaking());
        }
        if (item.getItem().is(Items.ENDER_CHEST))
            EnderChestRitual.onStartDrag(id, sl, player);
    }

    private static void swingDamage(ServerLevel sl, ServerPlayer player, Vec3 newPos, Vec3 prevPos) {
        double moved = newPos.distanceTo(prevPos);
        if (moved < MIN_SWING_DISTANCE) return;

        Vec3 dir = newPos.subtract(prevPos).normalize();
        for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(newPos, newPos).inflate(SWING_HIT_RADIUS))) {
            if (target instanceof Player || mobHitCooldown.containsKey(target.getId())) continue;

            float dmg = BASE_SWING_DAMAGE * (float) Math.min(MAX_SWING_MULTIPLIER, moved / MIN_SWING_DISTANCE);
            target.hurt(player.damageSources().playerAttack(player), dmg);
            target.knockback(0.4, -dir.x, -dir.z);
            mobHitCooldown.put(target.getId(), MOB_HIT_COOLDOWN);
            sl.sendParticles(ParticleTypes.CRIT, target.getX(),
                    target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    8, 0.2, 0.2, 0.2, 0.05);
            sl.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, 0.8f, 1f);
        }
    }

    private static void handleRelease(ServerPlayer player, ItemEntity item, DragItemPayload payload) {
        int id = payload.entityId();

        EnchantRitual.onRelease(id, player);
        SmithingRitual.onRelease(id);
        GrindstoneRitual.onRelease(id);
        FurnaceRitual.onRelease(id);
        AnvilRitual.onRelease(id);
        CraftingRitual.onRelease(id);
        ChestLootRitual.onRelease(id);
        EnderChestRitual.onRelease(id);
        lastDragPos.remove(id);

        Vec3 throwVec = new Vec3(payload.vx(), payload.vy(), payload.vz());
        if (throwVec.length() > DragConstants.MAX_THROW_SPEED) {
            throwVec = throwVec.normalize().scale(DragConstants.MAX_THROW_SPEED);
        }

        item.noPhysics = false;
        item.setNoGravity(false);
        item.setDeltaMovement(throwVec);
        item.setPickUpDelay(10);
    }
}