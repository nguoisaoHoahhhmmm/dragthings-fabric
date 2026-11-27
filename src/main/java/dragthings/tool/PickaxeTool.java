package dragthings.tool;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Pickaxe: mines only. Sneaking does nothing special, so it keeps mining. */
public final class PickaxeTool implements ToolBehavior {

    @Override
    public boolean accepts(ItemStack tool) {
        return tool.getItem() instanceof PickaxeItem;
    }

    @Override
    public boolean sneakAction(ServerLevel level, ServerPlayer player, ItemEntity item,
                               BlockHitResult hit, BlockState state) {
        return false;
    }
}