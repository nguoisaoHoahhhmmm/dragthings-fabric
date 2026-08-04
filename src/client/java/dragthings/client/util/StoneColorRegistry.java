package dragthings.client.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Runtime tint palette for the shared gray stone-chip sprites. */
public final class StoneColorRegistry {

    private static final Vector3f DEFAULT_STONE = new Vector3f(0.55F, 0.55F, 0.54F);
    private static final Map<Block, Vector3f> COLORS = new HashMap<>();

    static {
        put(new Vector3f(0.50F, 0.50F, 0.49F), Blocks.STONE, Blocks.COBBLESTONE,
                Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,
                Blocks.SMOOTH_STONE);
        put(new Vector3f(0.55F, 0.48F, 0.45F), Blocks.GRANITE, Blocks.POLISHED_GRANITE);
        put(new Vector3f(0.75F, 0.72F, 0.70F), Blocks.DIORITE, Blocks.POLISHED_DIORITE);
        put(new Vector3f(0.54F, 0.55F, 0.53F), Blocks.ANDESITE, Blocks.POLISHED_ANDESITE);
        put(new Vector3f(0.35F, 0.35F, 0.38F), Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE,
                Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES);
        put(new Vector3f(0.45F, 0.43F, 0.40F), Blocks.TUFF, Blocks.POLISHED_TUFF,
                Blocks.TUFF_BRICKS);
        put(new Vector3f(0.80F, 0.78F, 0.72F), Blocks.CALCITE);
        put(new Vector3f(0.54F, 0.50F, 0.45F), Blocks.DRIPSTONE_BLOCK);
        put(new Vector3f(0.25F, 0.25F, 0.25F), Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE,
                Blocks.POLISHED_BLACKSTONE_BRICKS);
        put(new Vector3f(0.26F, 0.26F, 0.27F), Blocks.BASALT, Blocks.SMOOTH_BASALT);
        put(new Vector3f(0.52F, 0.50F, 0.45F), Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICKS);
        put(new Vector3f(0.70F, 0.65F, 0.50F), Blocks.END_STONE);
    }

    private StoneColorRegistry() {}

    private static void put(Vector3f color, Block... blocks) {
        for (Block block : blocks) COLORS.put(block, color);
    }

    public static Vector3f getColor(Block block) {
        return new Vector3f(COLORS.getOrDefault(block, DEFAULT_STONE));
    }
}
