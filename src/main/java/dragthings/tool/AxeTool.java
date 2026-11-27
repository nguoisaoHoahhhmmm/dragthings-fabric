package dragthings.tool;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Axe: mines wood fast. While sneaking: strip logs, scrape oxidised copper, remove wax. */
public final class AxeTool implements ToolBehavior {

    @Override
    public boolean accepts(ItemStack tool) {
        return tool.getItem() instanceof AxeItem;
    }

    @Override
    public boolean sneakAction(ServerLevel level, ServerPlayer player, ItemEntity item,
                               BlockHitResult hit, BlockState state) {
        ToolMining.useOn(level, player, item, hit);
        return true;
    }
}