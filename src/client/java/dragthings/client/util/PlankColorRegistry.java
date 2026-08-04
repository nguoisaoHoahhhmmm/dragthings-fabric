package dragthings.client.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Tint palette for the shared plank-chip sprites. */
public final class PlankColorRegistry {
    private static final Vector3f DEFAULT_PLANK = new Vector3f(0.68F, 0.52F, 0.32F);
    private static final Map<Block, Vector3f> COLORS = new HashMap<>();

    static {
        put(0.78F, 0.65F, 0.42F, Blocks.OAK_PLANKS);
        put(0.43F, 0.29F, 0.18F, Blocks.SPRUCE_PLANKS);
        put(0.84F, 0.76F, 0.54F, Blocks.BIRCH_PLANKS);
        put(0.64F, 0.45F, 0.29F, Blocks.JUNGLE_PLANKS);
        put(0.71F, 0.40F, 0.23F, Blocks.ACACIA_PLANKS);
        put(0.29F, 0.21F, 0.14F, Blocks.DARK_OAK_PLANKS);
        put(0.44F, 0.24F, 0.20F, Blocks.MANGROVE_PLANKS);
        put(0.85F, 0.61F, 0.65F, Blocks.CHERRY_PLANKS);
        put(0.78F, 0.71F, 0.35F, Blocks.BAMBOO_PLANKS, Blocks.BAMBOO_MOSAIC);
        put(0.48F, 0.22F, 0.29F, Blocks.CRIMSON_PLANKS);
        put(0.22F, 0.50F, 0.47F, Blocks.WARPED_PLANKS);
    }

    private PlankColorRegistry() {}

    private static void put(float red, float green, float blue, Block... blocks) {
        Vector3f color = new Vector3f(red, green, blue);
        for (Block block : blocks) COLORS.put(block, color);
    }

    public static Vector3f getColor(Block block) { return new Vector3f(COLORS.getOrDefault(block, DEFAULT_PLANK)); }
}
