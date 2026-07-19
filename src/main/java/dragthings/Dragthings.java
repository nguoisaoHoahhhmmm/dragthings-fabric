package dragthings;

import dragthings.network.DragItemPayload;
import dragthings.network.PlaceBlockPayload;
import dragthings.network.RitualProgressPayload;
import dragthings.ritual.AnvilRitual;
import dragthings.ritual.ChestLootRitual;
import dragthings.ritual.CraftingRitual;
import dragthings.ritual.EnchantRitual;
import dragthings.ritual.EnderChestRitual;
import dragthings.ritual.FurnaceRitual;
import dragthings.ritual.GrindstoneRitual;
import dragthings.ritual.SmithingRitual;
import dragthings.tool.DragToolHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Dragthings implements ModInitializer {

    public static final String MOD_ID = "physicitem";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Exposes active enchanting-table entity IDs so the XP-orb pickup mixin
     * can freeze orbs near active ritual tables.
     */
    public static java.util.Set<Integer> activeRitualTables =
            new java.util.AbstractSet<Integer>() {
                @Override public java.util.Iterator<Integer> iterator() { return dragthings.ritual.EnchantRitual.activeTableIds().iterator(); }
                @Override public int size()                             { return dragthings.ritual.EnchantRitual.activeTableIds().size(); }
                @Override public boolean isEmpty()                      { return dragthings.ritual.EnchantRitual.activeTableIds().isEmpty(); }
            };

    // FIX: was 10*10 (10 blocks), but the client allows scrolling the drag
    // distance out to 12 blocks (see ItemDragHandler.MAX_DRAG_DIST), and
    // chain followers can trail further behind the leader by
    // CHAIN_SPACING (0.45) per follower. With the old 10-block cap, simply
    // scrolling out past 10 blocks caused every sync packet to be silently
    // rejected — the server's authoritative position froze at the last
    // accepted (closer) spot, and vanilla's normal entity-tracking sync then
    // kept snapping the client's local, further-out position back to that
    // stale one every time it broadcast, producing frequent visible jitter.
    // 16 blocks comfortably covers the full client range with slack, while
    // still blocking absurd distances (e.g. the old (0,0,0) teleport bug,
    // which was ~65+ blocks).
    private static final double MAX_DRAG_DISTANCE_SQ = 16.0 * 16.0;
    private static final double MAX_THROW_SPEED      = 2.5;

    // ItemTags has no SHULKER_BOXES constant (only BlockTags does), and to
    // rule out any tag-data mismatch entirely, check the underlying block
    // class directly instead of relying on a tag lookup — this is immune to
    // datapack/tag registration issues since it's a plain instanceof check.
    private static boolean isContainerItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
            var block = blockItem.getBlock();
            return block instanceof net.minecraft.world.level.block.ShulkerBoxBlock
                    || block instanceof net.minecraft.world.level.block.BarrelBlock
                    || block instanceof net.minecraft.world.level.block.ChestBlock; // covers chest + trapped chest
        }
        return false;
    }

    // ── Weapon-swing ─────────────────────────────────────────────────────
    private static final Map<Integer, Vec3>    lastDragPos    = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> mobHitCooldown = new ConcurrentHashMap<>();

    private static final double MIN_SWING_DISTANCE   = 0.5;
    private static final double SWING_HIT_RADIUS     = 1.0;
    private static final float  BASE_SWING_DAMAGE    = 3.0f;
    private static final float  MAX_SWING_MULTIPLIER = 3.0f;
    private static final int    MOB_HIT_COOLDOWN     = 10;

    // ── Tool mining — managed by DragToolHandler ─────────────────────────
    // (MiningProgress, blockMining map, and tool constants moved to
    //  dragthings.tool.DragToolHandler — see that class for details.)

    // ── Helpers ───────────────────────────────────────────────────────────
    private static boolean isWeapon(ItemStack s) {
        return s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem || s.getItem() instanceof TridentItem;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("DragThings v0.2 initialized!");

        PayloadTypeRegistry.playC2S().register(DragItemPayload.TYPE, DragItemPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlaceBlockPayload.TYPE, PlaceBlockPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RitualProgressPayload.TYPE, RitualProgressPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(DragItemPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player == null || player.level() == null) return;

                Entity entity = player.level().getEntity(payload.entityId());
                if (!(entity instanceof ItemEntity item)) return;

                if (payload.isDragging()) {
                    // ── Validate distance ────────────────────────────────
                    // FIX: previously validated distance against payload.x/y/z,
                    // but the client sends a (0,0,0) placeholder on the very
                    // first "start drag" packet (before any real position is
                    // computed) — this placeholder is nowhere near the player,
                    // so that check failed almost every single time a drag
                    // began, silently aborting before ritual registration
                    // (ChestLootRitual, EnderChestRitual, etc.) ever ran.
                    // The item entity's own server-side position is the only
                    // value that actually matters for anti-cheat purposes
                    // (it can't be spoofed by the client), so validate against
                    // that alone.
                    double itemDistSq = player.distanceToSqr(
                            item.position().x, item.position().y, item.position().z);
                    if (itemDistSq > MAX_DRAG_DISTANCE_SQ) {
                        LOGGER.warn("Player {} tried to drag item too far ({} blocks²)",
                                player.getName().getString(), (int) itemDistSq);
                        return;
                    }

                    item.setPos(payload.x(), payload.y(), payload.z());
                    item.setNoGravity(true);
                    item.noPhysics = true;
                    item.setDeltaMovement(0, 0, 0);
                    item.setPickUpDelay(5);

                    // ── Ritual registration ───────────────────────────────
                    if (player.level() instanceof ServerLevel sl) {
                        if (item.getItem().is(Items.ENCHANTING_TABLE)) EnchantRitual.onStartDrag(payload.entityId(), sl, player);
                        if (item.getItem().is(Items.SMITHING_TABLE))   SmithingRitual.onStartDrag(payload.entityId(), sl);
                        if (item.getItem().is(Items.GRINDSTONE))       GrindstoneRitual.onStartDrag(payload.entityId(), sl);
                        if (item.getItem().is(Items.FURNACE) || item.getItem().is(Items.BLAST_FURNACE) || item.getItem().is(Items.SMOKER))
                            FurnaceRitual.onStartDrag(payload.entityId(), sl);
                        if (item.getItem().is(Items.ANVIL) || item.getItem().is(Items.CHIPPED_ANVIL) || item.getItem().is(Items.DAMAGED_ANVIL))
                            AnvilRitual.onStartDrag(payload.entityId(), sl);
                        if (item.getItem().is(Items.CRAFTING_TABLE))
                            CraftingRitual.onStartDrag(payload.entityId(), sl);
                        if (isContainerItem(item.getItem())) {
                            LOGGER.info("ChestLootRitual trigger matched for item={} entity={}",
                                    item.getItem().getItem(), payload.entityId());
                            ChestLootRitual.onStartDrag(payload.entityId(), sl, player, payload.isSneaking());
                        }
                        if (item.getItem().is(Items.ENDER_CHEST))
                            EnderChestRitual.onStartDrag(payload.entityId(), sl, player);
                    }

                    // ── Weapon swing damage ───────────────────────────────
                    Vec3 newPos  = item.position();
                    Vec3 prevPos = lastDragPos.get(payload.entityId());
                    lastDragPos.put(payload.entityId(), newPos);

                    if (prevPos != null && isWeapon(item.getItem()) && player.level() instanceof ServerLevel sl2) {
                        double moved = newPos.distanceTo(prevPos);
                        if (moved >= MIN_SWING_DISTANCE) {
                            Vec3 dir = newPos.subtract(prevPos).normalize();
                            for (LivingEntity target : sl2.getEntitiesOfClass(LivingEntity.class,
                                    new AABB(newPos, newPos).inflate(SWING_HIT_RADIUS))) {
                                if (target instanceof Player || mobHitCooldown.containsKey(target.getId())) continue;
                                float dmg = BASE_SWING_DAMAGE * (float) Math.min(MAX_SWING_MULTIPLIER, moved / MIN_SWING_DISTANCE);
                                target.hurt(player.damageSources().playerAttack(player), dmg);
                                target.knockback(0.4, -dir.x, -dir.z);
                                mobHitCooldown.put(target.getId(), MOB_HIT_COOLDOWN);
                                sl2.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                                sl2.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 1f);
                            }
                        }
                    }

                    // ── Tool interaction ──────────────────────────────────
                    // Delegated to DragToolHandler — handles per-tool mining
                    // speed, sneak-gated secondary actions (strip/path/till),
                    // and MiningProgress tracking.
                    if (player.level() instanceof ServerLevel sl3) {
                        DragToolHandler.handle(sl3, player, item, newPos, prevPos,
                                payload.isSneaking());
                    }

                } else {
                    // ── Release ───────────────────────────────────────────
                    EnchantRitual.onRelease(payload.entityId(), player);
                    SmithingRitual.onRelease(payload.entityId());
                    GrindstoneRitual.onRelease(payload.entityId());
                    FurnaceRitual.onRelease(payload.entityId());
                    AnvilRitual.onRelease(payload.entityId());
                    CraftingRitual.onRelease(payload.entityId());
                    ChestLootRitual.onRelease(payload.entityId());
                    EnderChestRitual.onRelease(payload.entityId());
                    lastDragPos.remove(payload.entityId());

                    Vec3 throwVec = new Vec3(payload.vx(), payload.vy(), payload.vz());
                    if (throwVec.length() > MAX_THROW_SPEED) throwVec = throwVec.normalize().scale(MAX_THROW_SPEED);

                    item.noPhysics = false;
                    item.setNoGravity(false);
                    item.setDeltaMovement(throwVec);
                    item.setPickUpDelay(10);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PlaceBlockPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player == null || !(player.level() instanceof ServerLevel sl)) return;

                // ── Security checks ───────────────────────────────────────
                // 1. Player must be in survival or creative (not spectator/adventure)
                if (player.isSpectator()) return;

                // 2. Target position must be within reasonable reach
                double distSq = player.distanceToSqr(
                        payload.blockPos().getX() + 0.5,
                        payload.blockPos().getY() + 0.5,
                        payload.blockPos().getZ() + 0.5);
                if (distSq > MAX_DRAG_DISTANCE_SQ) {
                    LOGGER.warn("Player {} tried to place block too far away ({} blocks²)",
                            player.getName().getString(), (int) distSq);
                    return;
                }

                // 3. Source item entity must exist and be a BlockItem
                Entity entity = sl.getEntity(payload.entityId());
                if (!(entity instanceof ItemEntity itemEntity)) return;
                if (!(itemEntity.getItem().getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) return;

                // 4. Target must be air / replaceable and inside world bounds
                if (!sl.isInWorldBounds(payload.blockPos())) return;
                net.minecraft.world.item.ItemStack stack = itemEntity.getItem().copyWithCount(1);

                // Reconstruct a BlockHitResult from the payload data
                Vec3 hitVec = Vec3.atLowerCornerOf(payload.blockPos()).add(
                        payload.hitX(), payload.hitY(), payload.hitZ());
                net.minecraft.world.phys.BlockHitResult serverHit =
                        new net.minecraft.world.phys.BlockHitResult(
                                hitVec, payload.face(), payload.blockPos(), false);

                net.minecraft.world.item.context.BlockPlaceContext ctx =
                        new net.minecraft.world.item.context.BlockPlaceContext(
                                sl, player,
                                net.minecraft.world.InteractionHand.MAIN_HAND,
                                stack, serverHit);

                net.minecraft.world.level.block.state.BlockState state =
                        blockItem.getBlock().getStateForPlacement(ctx);
                if (state == null) return;
                if (!state.canSurvive(sl, payload.blockPos())) return;
                if (!sl.getBlockState(payload.blockPos()).canBeReplaced(ctx)) return;

                // ── Place the block ───────────────────────────────────────
                sl.setBlock(payload.blockPos(), state,
                        net.minecraft.world.level.block.Block.UPDATE_ALL);

                // FIX: setBlock() only creates an empty BlockEntity. Item
                // components such as DataComponents.CONTAINER (chest contents
                // accumulated by ChestLootRitual while dragging) must be
                // explicitly copied onto the new BlockEntity — vanilla
                // BlockItem.place() does this internally via
                // BlockEntity#applyComponentsFromItem, but this custom
                // placement path bypasses that method entirely, so without
                // this call chests/shulker boxes always place empty.
                net.minecraft.world.level.block.entity.BlockEntity placedBe =
                        sl.getBlockEntity(payload.blockPos());
                if (placedBe != null) {
                    placedBe.applyComponentsFromItemStack(stack);
                }

                // Play server-side sound so other players hear it
                net.minecraft.world.level.block.SoundType snd = state.getSoundType();
                sl.playSound(null,
                        payload.blockPos().getX() + 0.5,
                        payload.blockPos().getY() + 0.5,
                        payload.blockPos().getZ() + 0.5,
                        snd.getPlaceSound(),
                        net.minecraft.sounds.SoundSource.BLOCKS,
                        (snd.getVolume() + 1f) / 2f, snd.getPitch() * 0.8f);

                // Consume one item from the entity; discard if empty
                net.minecraft.world.item.ItemStack entityStack = itemEntity.getItem();
                entityStack.shrink(1);
                if (entityStack.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setItem(entityStack);
                    // Restore physics — the entity is no longer being dragged
                    itemEntity.setNoGravity(false);
                    itemEntity.noPhysics = false;
                }

                LOGGER.debug("Server placed {} @ {} for player {}",
                        state.getBlock(), payload.blockPos(),
                        player.getName().getString());
            });
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Cooldowns
            mobHitCooldown.replaceAll((id, ticks) -> ticks - 1);
            mobHitCooldown.values().removeIf(ticks -> ticks <= 0);

            // Mining idle decay — delegated to DragToolHandler
            DragToolHandler.tickMiningDecay();

            // Ritual ticks
            EnchantRitual.tick();
            SmithingRitual.tick();
            GrindstoneRitual.tick();
            FurnaceRitual.tick();
            AnvilRitual.tick();
            CraftingRitual.tick();
            ChestLootRitual.tick();
            EnderChestRitual.tick();
        });
    }
}