package dragthings.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * Entry point for dragged tools. Finds the block the tool is swept through,
 * checks the player may touch it, then hands over to the tool's behavior:
 *
 *   PickaxeTool, AxeTool, ShovelTool, HoeTool   what each does while sneaking
 *   ToolMining                                  swing-by-swing mining they all share
 *
 * Any other DiggerItem (a modded tool) simply mines. To add a new tool, write
 * a ToolBehavior and add it to BEHAVIORS.
 */
public final class DragToolHandler {

    private static final List<ToolBehavior> BEHAVIORS = List.of(
            new PickaxeTool(), new AxeTool(), new ShovelTool(), new HoeTool());

    /** The block must be this close to the player (squared), like the drag range. */
    private static final double MAX_TARGET_DIST_SQ = 24.0 * 24.0;

    private DragToolHandler() {}

    /**
     * Called from the drag packet handler every sync packet.
     *
     * @param prevPos    position from the previous sync packet (null on first)
     * @param isSneaking whether the client had Shift held when the packet was sent
     */
    public static void handle(ServerLevel sl, ServerPlayer player,
                              ItemEntity item, Vec3 newPos, Vec3 prevPos,
                              boolean isSneaking) {
        if (prevPos == null) return;

        ItemStack tool = item.getItem();
        if (!(tool.getItem() instanceof DiggerItem)) return;

        double moved = newPos.distanceTo(prevPos);
        if (moved < ToolMining.MIN_MOVE) return;

        // Raycast slightly ahead of travel direction to find the block being hit
        Vec3 dir = newPos.subtract(prevPos).normalize();
        BlockHitResult hit = sl.clip(new ClipContext(
                prevPos, newPos.add(dir.scale(0.4)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos bp = hit.getBlockPos();
        BlockState bs = sl.getBlockState(bp);
        if (bs.isAir()) return;

        // Protected spawn, adventure mode, too far from the player
        if (!sl.mayInteract(player, bp)
                || player.blockActionRestricted(sl, bp, player.gameMode.getGameModeForPlayer())
                || player.distanceToSqr(Vec3.atCenterOf(bp)) > MAX_TARGET_DIST_SQ) return;

        if (isSneaking) {
            for (ToolBehavior behavior : BEHAVIORS) {
                if (behavior.accepts(tool) && behavior.sneakAction(sl, player, item, hit, bs)) return;
            }
        }
        ToolMining.mine(sl, player, item, bp, bs, moved);
    }

    /** Tick-based idle decay: call once per server tick. */
    public static void tickMiningDecay() { ToolMining.tickDecay(); }

    /** Kept for old callers; same map as before. */
    public static Map<String, ToolMining.MiningProgress> getMiningMap() { return ToolMining.miningMap(); }

    public static String mineKey(ServerLevel l, BlockPos p) { return ToolMining.mineKey(l, p); }
}