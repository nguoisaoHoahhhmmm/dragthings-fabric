package dragthings.tool;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One kind of dragged tool. Every digging tool mines by default (see
 * {@link ToolMining}); a behavior adds what happens while the player SNEAKS.
 */
public interface ToolBehavior {

    /** Does this behavior own the given tool? */
    boolean accepts(ItemStack tool);

    /**
     * The sneak action on the block that was hit.
     *
     * @return true when the sweep is used up by this action (the tool does not
     *         mine); false to fall back to normal mining.
     */
    boolean sneakAction(ServerLevel level, ServerPlayer player, ItemEntity item,
                        BlockHitResult hit, BlockState state);
}