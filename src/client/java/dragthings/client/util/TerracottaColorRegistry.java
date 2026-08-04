package dragthings.client.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Tint palette for the shared terracotta-chip sprites. */
public final class TerracottaColorRegistry {
    private static final Vector3f DEFAULT_TERRACOTTA = new Vector3f(0.60F, 0.37F, 0.29F);
    private static final Map<Block, Vector3f> COLORS = new HashMap<>();

    static {
        put(0.60F, 0.37F, 0.29F, Blocks.TERRACOTTA);
        put(0.82F, 0.81F, 0.77F, Blocks.WHITE_TERRACOTTA);
        put(0.62F, 0.33F, 0.18F, Blocks.ORANGE_TERRACOTTA);
        put(0.58F, 0.34F, 0.42F, Blocks.MAGENTA_TERRACOTTA);
        put(0.44F, 0.42F, 0.54F, Blocks.LIGHT_BLUE_TERRACOTTA);
        put(0.73F, 0.56F, 0.25F, Blocks.YELLOW_TERRACOTTA);
        put(0.40F, 0.46F, 0.20F, Blocks.LIME_TERRACOTTA);
        put(0.66F, 0.33F, 0.39F, Blocks.PINK_TERRACOTTA);
        put(0.22F, 0.18F, 0.18F, Blocks.GRAY_TERRACOTTA);
        put(0.53F, 0.42F, 0.38F, Blocks.LIGHT_GRAY_TERRACOTTA);
        put(0.34F, 0.36F, 0.36F, Blocks.CYAN_TERRACOTTA);
        put(0.46F, 0.27F, 0.34F, Blocks.PURPLE_TERRACOTTA);
        put(0.29F, 0.34F, 0.49F, Blocks.BLUE_TERRACOTTA);
        put(0.30F, 0.20F, 0.16F, Blocks.BROWN_TERRACOTTA);
        put(0.56F, 0.24F, 0.18F, Blocks.RED_TERRACOTTA);
        put(0.15F, 0.09F, 0.06F, Blocks.BLACK_TERRACOTTA);
    }

    private TerracottaColorRegistry() {}

    private static void put(float red, float green, float blue, Block block) { COLORS.put(block, new Vector3f(red, green, blue)); }

    public static Vector3f getColor(Block block) { return new Vector3f(COLORS.getOrDefault(block, DEFAULT_TERRACOTTA)); }
}
