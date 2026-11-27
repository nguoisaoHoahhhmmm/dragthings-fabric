package dragthings.config;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * weight.json: how heavy things feel to drag.
 *
 * weightInfluence / stackWeightInfluence: 0-10 (0 = no effect, 10 = full).
 * itemWeights: item id -> weight, 0.2 (very light) to 2.0 (very heavy). It
 * overrides the built-in guess (swords, armor, blocks...) for those items.
 */
public final class WeightConfig {

    public boolean enableWeight = true;
    public int weightInfluence = 5;
    public int stackWeightInfluence = 3;

    public Map<String, Float> itemWeights = defaultWeights();

    private static Map<String, Float> defaultWeights() {
        Map<String, Float> m = new LinkedHashMap<>();
        m.put("minecraft:feather", 0.2f);
        m.put("minecraft:paper", 0.3f);
        m.put("minecraft:string", 0.3f);
        m.put("minecraft:snowball", 0.4f);
        m.put("minecraft:egg", 0.4f);
        m.put("minecraft:flower_pot", 0.5f);
        return m;
    }

    void sanitize() {
        weightInfluence = ConfigFiles.clamp(weightInfluence, 0, 10);
        stackWeightInfluence = ConfigFiles.clamp(stackWeightInfluence, 0, 10);
        if (itemWeights == null) itemWeights = defaultWeights();
        itemWeights.replaceAll((id, w) -> w == null ? 1f : ConfigFiles.clamp(w, 0.2f, 2.0f));
    }

    /**
     * Weight multiplier for a stack, 0.3 to 2.0:
     * below 1 = lighter (faster drag), 1 = neutral, above 1 = heavier (slower).
     */
    public float getMultiplier(ItemStack stack) {
        if (!enableWeight) return 1.0f;

        float typeWeight = getItemTypeWeight(stack);
        float stackFactor = 1.0f;
        if (stackWeightInfluence > 0) {
            // 1 item = 0.5x, 64 items = 1.5x, blended by the influence
            float raw = 0.5f + stack.getCount() / 64f;
            stackFactor = 1.0f + (raw - 1.0f) * (stackWeightInfluence / 10f);
        }
        float combined = typeWeight * stackFactor;
        float result = 1.0f + (combined - 1.0f) * (weightInfluence / 10f);
        return Math.max(0.3f, Math.min(2.0f, result));
    }

    private float getItemTypeWeight(ItemStack stack) {
        if (stack.isEmpty()) return 1.0f;
        Item item = stack.getItem();

        Float override = itemWeights.get(BuiltInRegistries.ITEM.getKey(item).toString());
        if (override != null) return override;

        if (item instanceof SwordItem)   return 1.6f;
        if (item instanceof AxeItem)     return 1.8f;
        if (item instanceof PickaxeItem) return 1.7f;
        if (item instanceof ShovelItem)  return 1.5f;
        if (item instanceof HoeItem)     return 1.4f;

        if (item instanceof ArmorItem armor) {
            return switch (armor.getType()) {
                case HELMET -> 1.3f;
                case CHESTPLATE -> 1.8f;
                case LEGGINGS -> 1.6f;
                case BOOTS -> 1.4f;
                default -> 1.3f;
            };
        }

        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block instanceof AnvilBlock) return 2.0f;
            String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
            return name.contains("ore") ? 1.6f : 1.3f;
        }

        if (item.components().has(DataComponents.FOOD)) return 0.7f;
        return 1.0f;
    }
}
