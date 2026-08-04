package dragthings.client.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

import java.util.Map;

public final class WoodColorRegistry {

    private WoodColorRegistry() {}

    private static final Vector3f DEFAULT_WOOD = new Vector3f(0.75F, 0.60F, 0.40F);

    private static final Map<Block, Vector3f> WOOD_COLORS = Map.ofEntries(

            // Oak
            Map.entry(Blocks.OAK_LOG, new Vector3f(0.75F, 0.60F, 0.38F)),
            Map.entry(Blocks.OAK_WOOD, new Vector3f(0.75F, 0.60F, 0.38F)),
            Map.entry(Blocks.STRIPPED_OAK_LOG, new Vector3f(0.77F, 0.66F, 0.47F)),
            Map.entry(Blocks.STRIPPED_OAK_WOOD, new Vector3f(0.77F, 0.66F, 0.47F)),

            // Spruce
            Map.entry(Blocks.SPRUCE_LOG, new Vector3f(0.45F, 0.34F, 0.21F)),
            Map.entry(Blocks.SPRUCE_WOOD, new Vector3f(0.45F, 0.34F, 0.21F)),
            Map.entry(Blocks.STRIPPED_SPRUCE_LOG, new Vector3f(0.63F, 0.48F, 0.30F)),
            Map.entry(Blocks.STRIPPED_SPRUCE_WOOD, new Vector3f(0.63F, 0.48F, 0.30F)),

            // Birch
            Map.entry(Blocks.BIRCH_LOG, new Vector3f(0.85F, 0.83F, 0.75F)),
            Map.entry(Blocks.BIRCH_WOOD, new Vector3f(0.85F, 0.83F, 0.75F)),
            Map.entry(Blocks.STRIPPED_BIRCH_LOG, new Vector3f(0.90F, 0.82F, 0.60F)),
            Map.entry(Blocks.STRIPPED_BIRCH_WOOD, new Vector3f(0.90F, 0.82F, 0.60F)),

            // Jungle
            Map.entry(Blocks.JUNGLE_LOG, new Vector3f(0.65F, 0.48F, 0.34F)),
            Map.entry(Blocks.JUNGLE_WOOD, new Vector3f(0.65F, 0.48F, 0.34F)),
            Map.entry(Blocks.STRIPPED_JUNGLE_LOG, new Vector3f(0.82F, 0.63F, 0.42F)),
            Map.entry(Blocks.STRIPPED_JUNGLE_WOOD, new Vector3f(0.82F, 0.63F, 0.42F)),

            // Acacia
            Map.entry(Blocks.ACACIA_LOG, new Vector3f(0.68F, 0.40F, 0.25F)),
            Map.entry(Blocks.ACACIA_WOOD, new Vector3f(0.68F, 0.40F, 0.25F)),
            Map.entry(Blocks.STRIPPED_ACACIA_LOG, new Vector3f(0.86F, 0.59F, 0.37F)),
            Map.entry(Blocks.STRIPPED_ACACIA_WOOD, new Vector3f(0.86F, 0.59F, 0.37F)),

            // Dark Oak
            Map.entry(Blocks.DARK_OAK_LOG, new Vector3f(0.30F, 0.21F, 0.12F)),
            Map.entry(Blocks.DARK_OAK_WOOD, new Vector3f(0.30F, 0.21F, 0.12F)),
            Map.entry(Blocks.STRIPPED_DARK_OAK_LOG, new Vector3f(0.46F, 0.33F, 0.19F)),
            Map.entry(Blocks.STRIPPED_DARK_OAK_WOOD, new Vector3f(0.46F, 0.33F, 0.19F)),

            // Mangrove
            Map.entry(Blocks.MANGROVE_LOG, new Vector3f(0.46F, 0.22F, 0.20F)),
            Map.entry(Blocks.MANGROVE_WOOD, new Vector3f(0.46F, 0.22F, 0.20F)),
            Map.entry(Blocks.STRIPPED_MANGROVE_LOG, new Vector3f(0.73F, 0.47F, 0.38F)),
            Map.entry(Blocks.STRIPPED_MANGROVE_WOOD, new Vector3f(0.73F, 0.47F, 0.38F)),

            // Cherry
            Map.entry(Blocks.CHERRY_LOG, new Vector3f(0.90F, 0.67F, 0.70F)),
            Map.entry(Blocks.CHERRY_WOOD, new Vector3f(0.90F, 0.67F, 0.70F)),
            Map.entry(Blocks.STRIPPED_CHERRY_LOG, new Vector3f(0.93F, 0.77F, 0.73F)),
            Map.entry(Blocks.STRIPPED_CHERRY_WOOD, new Vector3f(0.93F, 0.77F, 0.73F)),

            // Bamboo
            Map.entry(Blocks.BAMBOO_BLOCK, new Vector3f(0.76F, 0.70F, 0.34F)),
            Map.entry(Blocks.STRIPPED_BAMBOO_BLOCK, new Vector3f(0.88F, 0.82F, 0.48F)),

            // Crimson
            Map.entry(Blocks.CRIMSON_STEM, new Vector3f(0.45F, 0.18F, 0.27F)),
            Map.entry(Blocks.CRIMSON_HYPHAE, new Vector3f(0.45F, 0.18F, 0.27F)),
            Map.entry(Blocks.STRIPPED_CRIMSON_STEM, new Vector3f(0.64F, 0.30F, 0.36F)),
            Map.entry(Blocks.STRIPPED_CRIMSON_HYPHAE, new Vector3f(0.64F, 0.30F, 0.36F)),

            // Warped
            Map.entry(Blocks.WARPED_STEM, new Vector3f(0.21F, 0.45F, 0.45F)),
            Map.entry(Blocks.WARPED_HYPHAE, new Vector3f(0.21F, 0.45F, 0.45F)),
            Map.entry(Blocks.STRIPPED_WARPED_STEM, new Vector3f(0.42F, 0.70F, 0.66F)),
            Map.entry(Blocks.STRIPPED_WARPED_HYPHAE, new Vector3f(0.42F, 0.70F, 0.66F))
    );

    public static Vector3f getColor(Block block) {
        return new Vector3f(WOOD_COLORS.getOrDefault(block, DEFAULT_WOOD));
    }
}