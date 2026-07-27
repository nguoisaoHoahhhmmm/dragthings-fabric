package dragthings;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Single source of truth for "is this a Chest / Trapped Chest / Barrel /
 * Shulker Box item" — i.e. anything that carries a 27-slot CONTAINER data
 * component and is eligible for ChestLootRitual + the container-contents
 * tooltip preview.
 *
 * Previously this exact check was duplicated in two places:
 *   - Dragthings.java (server-side ritual trigger registration)
 *   - ItemTooltipRenderer.java (client-side "[Shift] for contents" tooltip)
 * Two copies of the same check is exactly the kind of thing that silently
 * drifts out of sync — e.g. add a new container-like block later and only
 * remember to update one of the two. Common code (this package, not
 * dragthings.client) so both server and client call sites can share it.
 */
public final class ContainerUtil {

    private ContainerUtil() {}

    public static boolean isContainerItem(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        return block instanceof ChestBlock       // covers Chest + Trapped Chest
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock;
    }
}