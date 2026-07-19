package dragthings.tool;

import dragthings.Dragthings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all dragged-tool interactions server-side.
 *
 * ── Primary actions (always active while dragging) ─────────────────────────
 *   Pickaxe  → mine blocks. Speed bonus when the block is in the pickaxe's
 *              preferred tag (mineable/pickaxe). Slow but still possible on
 *              other blocks.
 *   Axe      → mine wood/logs fast (mineable/axe tag), slow on others.
 *   Shovel   → mine dirt/sand/gravel fast (mineable/shovel tag), slow on others.
 *   Hoe      → mine leaves/crops fast (mineable/hoe tag), slow on others.
 *
 * ── Secondary actions (only when player is sneaking / holding Shift) ────────
 *   Axe      → strip log / scrape oxidised copper / remove wax (useOn).
 *   Shovel   → create grass path (useOn).
 *   Hoe      → till dirt / farmland (useOn).
 *              Also harvests mature crops that it sweeps over when sneaking.
 *
 * The sneak gate prevents accidental secondary actions while just dragging
 * the tool around normally.
 */
public final class DragToolHandler {

    // ── Mining progress tracking ──────────────────────────────────────────
    public static final class MiningProgress {
        public final ServerLevel level;
        public final BlockPos    pos;
        public int breakerId;
        public int progress  = 0;
        public int idleTicks = 0;

        public MiningProgress(ServerLevel l, BlockPos p, int id) {
            level = l; pos = p; breakerId = id;
        }
    }

    private static final Map<String, MiningProgress> blockMining = new ConcurrentHashMap<>();

    // ── Tuning constants ──────────────────────────────────────────────────

    /** Minimum item travel per packet to count as a "swing". */
    private static final double MIN_MOVE = 0.4;

    /**
     * Base progress units added per swing on a MATCHING block.
     * Total to break = STAGE_MAX (9 stages × 1 unit = 9 needed).
     */
    private static final int PROGRESS_FAST = 3; // ~3 swings on matching block
    private static final int PROGRESS_SLOW = 1; // ~9 swings on non-matching block
    private static final int STAGE_MAX     = 9;

    /** Ticks without a swing before mining progress resets. */
    private static final int IDLE_RESET = 40;

    private DragToolHandler() {}

    // ── Public entry point ────────────────────────────────────────────────

    /**
     * Called from the DragItemPayload server handler every sync packet.
     *
     * @param sl        the server level
     * @param player    the dragging player
     * @param item      the dragged ItemEntity
     * @param newPos    current position of the dragged item
     * @param prevPos   position from the previous sync packet (null on first)
     * @param isSneaking whether the client had Shift held when the packet was sent
     */
    public static void handle(ServerLevel sl, ServerPlayer player,
                               ItemEntity item, Vec3 newPos, Vec3 prevPos,
                               boolean isSneaking) {
        if (prevPos == null) return;

        ItemStack tool = item.getItem();
        double moved   = newPos.distanceTo(prevPos);
        if (moved < MIN_MOVE) return;

        // Raycast slightly ahead of travel direction to find the block being hit
        Vec3 dir = newPos.subtract(prevPos).normalize();
        BlockHitResult hit = sl.clip(new ClipContext(
                prevPos,
                newPos.add(dir.scale(0.4)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player));

        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos    bp = hit.getBlockPos();
        BlockState  bs = sl.getBlockState(bp);
        if (bs.isAir()) return;

        // ── Dispatch by tool type ─────────────────────────────────────────
        if (tool.getItem() instanceof PickaxeItem) {
            handleMine(sl, player, item, bp, bs, moved, isPreferredByPickaxe(bs));

        } else if (tool.getItem() instanceof AxeItem) {
            if (isSneaking) {
                // Secondary: strip / scrape / de-wax
                handleUseOn(sl, player, item, hit);
            } else {
                // Primary: chop
                handleMine(sl, player, item, bp, bs, moved, isPreferredByAxe(bs));
            }

        } else if (tool.getItem() instanceof ShovelItem) {
            if (isSneaking) {
                // Secondary: create grass path
                handleUseOn(sl, player, item, hit);
            } else {
                // Primary: dig
                handleMine(sl, player, item, bp, bs, moved, isPreferredByShovel(bs));
            }

        } else if (tool.getItem() instanceof HoeItem) {
            if (isSneaking) {
                // Secondary: till / harvest
                handleUseOn(sl, player, item, hit);
            } else {
                // Primary: cut
                handleMine(sl, player, item, bp, bs, moved, isPreferredByHoe(bs));
            }
        }
    }

    /** Tick-based idle decay — call once per server tick from Dragthings. */
    public static void tickMiningDecay() {
        if (blockMining.isEmpty()) return;
        var it = blockMining.entrySet().iterator();
        while (it.hasNext()) {
            MiningProgress mp = it.next().getValue();
            if (++mp.idleTicks >= IDLE_RESET) {
                mp.level.destroyBlockProgress(mp.breakerId, mp.pos, -1);
                it.remove();
            }
        }
    }

    // ── Mining ────────────────────────────────────────────────────────────

    private static void handleMine(ServerLevel sl, ServerPlayer player,
                                   ItemEntity item, BlockPos bp, BlockState bs,
                                   double moved, boolean preferred) {
        String key = mineKey(sl, bp);
        MiningProgress mp = blockMining.computeIfAbsent(
                key, k -> new MiningProgress(sl, bp, player.getId()));
        mp.breakerId = player.getId();
        mp.idleTicks = 0;

        // Progress per swing scales with speed; preferred block gets a 3× head-start
        int baseProgress = preferred ? PROGRESS_FAST : PROGRESS_SLOW;
        double swingScale = Math.min(3.0, moved / MIN_MOVE); // cap at 3× for large moves
        mp.progress += (int) Math.max(1, Math.round(baseProgress * swingScale));

        if (mp.progress >= STAGE_MAX) {
            // Break the block
            sl.destroyBlockProgress(mp.breakerId, bp, -1);
            blockMining.remove(key);
            Block.dropResources(bs, sl, bp, sl.getBlockEntity(bp), player, item.getItem());
            sl.removeBlock(bp, false);
            sl.levelEvent(2001, bp, Block.getId(bs));
            // Damage the tool only in survival — creative tools have no durability
            if (!player.isCreative()) {
                item.getItem().hurtAndBreak(1, player,
                        net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
            Dragthings.LOGGER.debug("DragTool broke {} @ {}", bs.getBlock(), bp);
        } else {
            // Show crack animation (stage 0–8)
            int stage = (int)((mp.progress / (float) STAGE_MAX) * 9);
            sl.destroyBlockProgress(mp.breakerId, bp, Math.min(stage, 8));
            sl.playSound(null, bp, bs.getSoundType().getHitSound(),
                    SoundSource.BLOCKS, 0.5f, 1f);
        }
    }

    // ── Secondary (useOn) ─────────────────────────────────────────────────

    private static void handleUseOn(ServerLevel sl, ServerPlayer player,
                                    ItemEntity item, BlockHitResult hit) {
        ItemStack tool = item.getItem();
        InteractionResult res = tool.useOn(
                new UseOnContext(sl, player, InteractionHand.MAIN_HAND, tool, hit));
        if (res.consumesAction()) {
            // Write back any durability change etc. that useOn may have applied
            item.setItem(tool);
        }
    }

    // ── Block tag helpers ─────────────────────────────────────────────────
    // Using vanilla tags — works for modded blocks that tag themselves correctly.

    private static boolean isPreferredByPickaxe(BlockState bs) {
        return bs.is(BlockTags.MINEABLE_WITH_PICKAXE);
    }

    private static boolean isPreferredByAxe(BlockState bs) {
        return bs.is(BlockTags.MINEABLE_WITH_AXE);
    }

    private static boolean isPreferredByShovel(BlockState bs) {
        return bs.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    private static boolean isPreferredByHoe(BlockState bs) {
        return bs.is(BlockTags.MINEABLE_WITH_HOE);
    }

    // ── Key ───────────────────────────────────────────────────────────────

    public static String mineKey(ServerLevel l, BlockPos p) {
        return l.dimension().location() + "|" + p.asLong();
    }

    /** Expose map so Dragthings can forward existing references if needed. */
    public static Map<String, MiningProgress> getMiningMap() {
        return blockMining;
    }
}
