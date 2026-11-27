package dragthings.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Hoe: cuts leaves and crops fast. While sneaking: harvest and replant a fully
 * grown crop, or till dirt into farmland.
 */
public final class HoeTool implements ToolBehavior {

    @Override
    public boolean accepts(ItemStack tool) {
        return tool.getItem() instanceof HoeItem;
    }

    @Override
    public boolean sneakAction(ServerLevel level, ServerPlayer player, ItemEntity item,
                               BlockHitResult hit, BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            // A young crop is left alone; only a grown one is harvested.
            if (crop.isMaxAge(state)) harvest(level, player, item, hit.getBlockPos(), state, crop);
            return true;
        }
        ToolMining.useOn(level, player, item, hit);
        return true;
    }

    private static void harvest(ServerLevel level, ServerPlayer player, ItemEntity item,
                                BlockPos pos, BlockState state, CropBlock crop) {
        Block.dropResources(state, level, pos, level.getBlockEntity(pos), player, item.getItem());
        level.setBlock(pos, crop.getStateForAge(0), 2); // replant
        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1f, 1f);
        level.levelEvent(2001, pos, Block.getId(state));
    }
}