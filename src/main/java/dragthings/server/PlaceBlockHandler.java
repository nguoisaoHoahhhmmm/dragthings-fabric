package dragthings.server;

import dragthings.Dragthings;
import dragthings.network.PlaceBlockPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Server handling for PlaceBlockPayload: placing a dragged BlockItem. */
public final class PlaceBlockHandler {

    private PlaceBlockHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PlaceBlockPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (player == null || !(player.level() instanceof ServerLevel sl)) return;

                    // 1. Not a spectator
                    if (player.isSpectator()) return;

                    // 2. Target within reach
                    BlockPos pos = payload.blockPos();
                    double distSq = player.distanceToSqr(
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distSq > DragConstants.MAX_DRAG_DISTANCE_SQ) {
                        Dragthings.LOGGER.warn("Player {} tried to place block too far away ({} blocks²)",
                                player.getName().getString(), (int) distSq);
                        return;
                    }

                    // 3. Source must be an ItemEntity holding a BlockItem
                    Entity entity = sl.getEntity(payload.entityId());
                    if (!(entity instanceof ItemEntity itemEntity)) return;
                    if (!(itemEntity.getItem().getItem() instanceof BlockItem blockItem)) return;

                    // 4. Target inside world bounds
                    if (!sl.isInWorldBounds(pos)) return;
                    ItemStack stack = itemEntity.getItem().copyWithCount(1);

                    Vec3 hitVec = Vec3.atLowerCornerOf(pos).add(
                            payload.hitX(), payload.hitY(), payload.hitZ());
                    BlockHitResult serverHit = new BlockHitResult(hitVec, payload.face(), pos, false);
                    BlockPlaceContext ctx = new BlockPlaceContext(
                            sl, player, InteractionHand.MAIN_HAND, stack, serverHit);

                    BlockState state = blockItem.getBlock().getStateForPlacement(ctx);
                    if (state == null) return;
                    if (!state.canSurvive(sl, pos)) return;
                    if (!sl.getBlockState(pos).canBeReplaced(ctx)) return;

                    // DoublePlantBlock.getStateForPlacement does not check the
                    // space above, so a half-placed plant would be left behind.
                    if (state.getBlock() instanceof DoublePlantBlock) {
                        BlockPos above = pos.above();
                        if (!sl.isInWorldBounds(above) || !sl.getBlockState(above).canBeReplaced()) return;
                    }

                    // ── Place the block ──────────────────────────────────
                    sl.setBlock(pos, state, Block.UPDATE_ALL);

                    // setPlacedBy places the other half of multi-block
                    // blocks (doors, beds, tall plants), as vanilla
                    // BlockItem.place() does.
                    BlockState placed = sl.getBlockState(pos);
                    placed.getBlock().setPlacedBy(sl, pos, placed, player, stack);

                    // setBlock() only creates an empty BlockEntity; copy item
                    // components (e.g. chest contents from ChestLootRitual).
                    BlockEntity placedBe = sl.getBlockEntity(pos);
                    if (placedBe != null) {
                        placedBe.applyComponentsFromItemStack(stack);
                    }

                    // Server-side sound so other players hear it
                    SoundType snd = state.getSoundType();
                    sl.playSound(null,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            snd.getPlaceSound(), SoundSource.BLOCKS,
                            (snd.getVolume() + 1f) / 2f, snd.getPitch() * 0.8f);

                    // Consume one item; discard if empty
                    ItemStack entityStack = itemEntity.getItem();
                    entityStack.shrink(1);
                    if (entityStack.isEmpty()) {
                        itemEntity.discard();
                    } else {
                        itemEntity.setItem(entityStack);
                        // No longer being dragged: restore physics
                        itemEntity.setNoGravity(false);
                        itemEntity.noPhysics = false;
                    }

                    Dragthings.LOGGER.debug("Server placed {} @ {} for player {}",
                            state.getBlock(), pos, player.getName().getString());
                }));
    }
}