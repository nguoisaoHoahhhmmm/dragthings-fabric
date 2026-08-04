package dragthings.client.particle;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LeavesBlock;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * One OR-branch of a profile's "match" array. A profile matches an item or
 * entity if ANY of its conditions match.
 *
 * kind = "item_id" / "item_tag" / "item_component" / "item_kind" (special,
 * see below) / "item_default"
 * kind = "entity_id" / "entity_tag" / "entity_default"
 *
 * "item_kind" exists for the handful of categories that have no reliable
 * single vanilla tag to match on, OR where a plain "has this component"
 * check would be wrong — value is one of "armor", "block", "leaves",
 * "enchanted". This is the one place matching logic still lives in code
 * rather than pure JSON; everything else (specific items, specific mobs,
 * tag groups, genuinely-optional component checks like FOOD) is fully
 * data-driven and needs no code changes to extend.
 *
 * IMPORTANT gotcha for anyone adding new "item_component" matches: as of
 * the 1.20.5+ component refactor, several vanilla components (enchantments
 * being the classic trap) are part of EVERY item's default component
 * prototype with an empty/zero default value — so `.has()` on them returns
 * true for every item, not just ones actually carrying real data. Only use
 * "item_component" for components that are genuinely absent-by-default
 * (FOOD is a safe example — only food items carry it at all). For anything
 * that needs "present AND non-empty", add an "item_kind" case instead, the
 * way "enchanted" does below.
 */
public class MatchCondition {

    public enum Kind { ITEM_ID, ITEM_TAG, ITEM_COMPONENT, ITEM_KIND, ITEM_DEFAULT,
        ENTITY_ID, ENTITY_TAG, ENTITY_KIND, ENTITY_DEFAULT }

    public final Kind kind;
    public final String value; // null for *_DEFAULT

    /**
     * FIX: ItemStack.is(TagKey) was confirmed (via direct debug logging) to
     * return false for EVERY tag check in this dev environment, even for
     * items that unquestionably belong to the tag (e.g. red_dye against
     * #minecraft:dyes). Root cause not fully pinned down — something about
     * how tag data gets bound to item holders isn't happening the way it
     * does in a normal launch — but rather than depend on a vanilla API
     * that's empirically unreliable here, every "item_tag" this project
     * actually uses is hand-mirrored below from the real vanilla tag
     * contents, checked by plain Item identity instead. Zero dependency on
     * tag-binding timing/environment quirks this way.
     *
     * Only covers tags actually used by this mod's own JSON profiles. If a
     * profile references a tag not in this map, matching silently falls
     * back to false (see matchesItem) rather than crashing — safest
     * default until proven otherwise.
     */
    private static final Map<String, Set<Item>> TAG_FALLBACK = new HashMap<>();
    static {
        TAG_FALLBACK.put("minecraft:dyes", Set.of(
                Items.WHITE_DYE, Items.ORANGE_DYE, Items.MAGENTA_DYE, Items.LIGHT_BLUE_DYE,
                Items.YELLOW_DYE, Items.LIME_DYE, Items.PINK_DYE, Items.GRAY_DYE,
                Items.LIGHT_GRAY_DYE, Items.CYAN_DYE, Items.PURPLE_DYE, Items.BLUE_DYE,
                Items.BROWN_DYE, Items.GREEN_DYE, Items.RED_DYE, Items.BLACK_DYE));

        TAG_FALLBACK.put("minecraft:small_flowers", Set.of(
                Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM,
                Items.AZURE_BLUET, Items.RED_TULIP, Items.ORANGE_TULIP, Items.WHITE_TULIP,
                Items.PINK_TULIP, Items.OXEYE_DAISY, Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY,
                Items.WITHER_ROSE, Items.TORCHFLOWER));

        TAG_FALLBACK.put("minecraft:tall_flowers", Set.of(
                Items.SUNFLOWER, Items.LILAC, Items.ROSE_BUSH, Items.PEONY, Items.PITCHER_PLANT));

        TAG_FALLBACK.put("minecraft:wool", Set.of(
                Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL,
                Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
                Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
                Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL));

        TAG_FALLBACK.put("minecraft:planks", Set.of(
                Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS,
                Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS,
                Items.BAMBOO_PLANKS, Items.BAMBOO_MOSAIC, Items.CRIMSON_PLANKS, Items.WARPED_PLANKS));

        TAG_FALLBACK.put("minecraft:swords", Set.of(
                Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
                Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD));

        TAG_FALLBACK.put("minecraft:pickaxes", Set.of(
                Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
                Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE));

        TAG_FALLBACK.put("minecraft:axes", Set.of(
                Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE,
                Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE));

        TAG_FALLBACK.put("minecraft:shovels", Set.of(
                Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL,
                Items.GOLDEN_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL));

        TAG_FALLBACK.put("minecraft:hoes", Set.of(
                Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE,
                Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE));

        TAG_FALLBACK.put("minecraft:beacon_payment_items", Set.of(
                Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD, Items.NETHERITE_INGOT));

        TAG_FALLBACK.put("minecraft:ores", Set.of(
                Items.COAL_ORE, Items.DEEPSLATE_COAL_ORE,
                Items.COPPER_ORE, Items.DEEPSLATE_COPPER_ORE,
                Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE,
                Items.GOLD_ORE, Items.DEEPSLATE_GOLD_ORE, Items.NETHER_GOLD_ORE,
                Items.DIAMOND_ORE, Items.DEEPSLATE_DIAMOND_ORE,
                Items.EMERALD_ORE, Items.DEEPSLATE_EMERALD_ORE,
                Items.LAPIS_ORE, Items.DEEPSLATE_LAPIS_ORE,
                Items.REDSTONE_ORE, Items.DEEPSLATE_REDSTONE_ORE,
                Items.NETHER_QUARTZ_ORE));

        TAG_FALLBACK.put("minecraft:logs", Set.of(
                Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG,
                Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG,
                Items.BAMBOO_BLOCK, Items.CRIMSON_STEM, Items.WARPED_STEM,
                Items.STRIPPED_OAK_LOG, Items.STRIPPED_SPRUCE_LOG, Items.STRIPPED_BIRCH_LOG,
                Items.STRIPPED_JUNGLE_LOG, Items.STRIPPED_ACACIA_LOG, Items.STRIPPED_DARK_OAK_LOG,
                Items.STRIPPED_MANGROVE_LOG, Items.STRIPPED_CHERRY_LOG, Items.STRIPPED_BAMBOO_BLOCK,
                Items.STRIPPED_CRIMSON_STEM, Items.STRIPPED_WARPED_STEM));

        TAG_FALLBACK.put("minecraft:fence", Set.of(
                Items.OAK_FENCE, Items.SPRUCE_FENCE, Items.BIRCH_FENCE, Items.JUNGLE_FENCE,
                Items.ACACIA_FENCE, Items.DARK_OAK_FENCE, Items.MANGROVE_FENCE, Items.CHERRY_FENCE,
                Items.BAMBOO_FENCE, Items.CRIMSON_FENCE, Items.WARPED_FENCE,
                Items.NETHER_BRICK_FENCE));

        TAG_FALLBACK.put("minecraft:fence_gates", Set.of(
                Items.OAK_FENCE_GATE, Items.SPRUCE_FENCE_GATE, Items.BIRCH_FENCE_GATE, Items.JUNGLE_FENCE_GATE,
                Items.ACACIA_FENCE_GATE, Items.DARK_OAK_FENCE_GATE, Items.MANGROVE_FENCE_GATE, Items.CHERRY_FENCE_GATE,
                Items.BAMBOO_FENCE_GATE, Items.CRIMSON_FENCE_GATE, Items.WARPED_FENCE_GATE));

        TAG_FALLBACK.put("minecraft:doors", Set.of(
                Items.OAK_DOOR, Items.SPRUCE_DOOR, Items.BIRCH_DOOR, Items.JUNGLE_DOOR,
                Items.ACACIA_DOOR, Items.DARK_OAK_DOOR, Items.MANGROVE_DOOR, Items.CHERRY_DOOR,
                Items.BAMBOO_DOOR, Items.CRIMSON_DOOR, Items.WARPED_DOOR,
                Items.IRON_DOOR));

        TAG_FALLBACK.put("minecraft:trapdoors", Set.of(
                Items.OAK_TRAPDOOR, Items.SPRUCE_TRAPDOOR, Items.BIRCH_TRAPDOOR, Items.JUNGLE_TRAPDOOR,
                Items.ACACIA_TRAPDOOR, Items.DARK_OAK_TRAPDOOR, Items.MANGROVE_TRAPDOOR, Items.CHERRY_TRAPDOOR,
                Items.BAMBOO_TRAPDOOR, Items.CRIMSON_TRAPDOOR, Items.WARPED_TRAPDOOR,
                Items.IRON_TRAPDOOR));

        TAG_FALLBACK.put("minecraft:wooden_slabs", Set.of(
                Items.OAK_SLAB, Items.SPRUCE_SLAB, Items.BIRCH_SLAB, Items.JUNGLE_SLAB,
                Items.ACACIA_SLAB, Items.DARK_OAK_SLAB, Items.MANGROVE_SLAB, Items.CHERRY_SLAB,
                Items.BAMBOO_SLAB, Items.CRIMSON_SLAB, Items.WARPED_SLAB, Items.BAMBOO_MOSAIC_SLAB));

        TAG_FALLBACK.put("minecraft:wooden_stairs", Set.of(
                Items.OAK_STAIRS, Items.SPRUCE_STAIRS, Items.BIRCH_STAIRS, Items.JUNGLE_STAIRS,
                Items.ACACIA_STAIRS, Items.DARK_OAK_STAIRS, Items.MANGROVE_STAIRS, Items.CHERRY_STAIRS,
                Items.BAMBOO_STAIRS, Items.CRIMSON_STAIRS, Items.WARPED_STAIRS, Items.BAMBOO_MOSAIC_STAIRS));

        TAG_FALLBACK.put("minecraft:wooden_buttons", Set.of(
                Items.OAK_BUTTON, Items.SPRUCE_BUTTON, Items.BIRCH_BUTTON, Items.JUNGLE_BUTTON,
                Items.ACACIA_BUTTON, Items.DARK_OAK_BUTTON, Items.MANGROVE_BUTTON, Items.CHERRY_BUTTON,
                Items.BAMBOO_BUTTON, Items.CRIMSON_BUTTON, Items.WARPED_BUTTON));

        TAG_FALLBACK.put("minecraft:wooden_pressure_plates", Set.of(
                Items.OAK_PRESSURE_PLATE, Items.SPRUCE_PRESSURE_PLATE, Items.BIRCH_PRESSURE_PLATE, Items.JUNGLE_PRESSURE_PLATE,
                Items.ACACIA_PRESSURE_PLATE, Items.DARK_OAK_PRESSURE_PLATE, Items.MANGROVE_PRESSURE_PLATE, Items.CHERRY_PRESSURE_PLATE,
                Items.BAMBOO_PRESSURE_PLATE, Items.CRIMSON_PRESSURE_PLATE, Items.WARPED_PRESSURE_PLATE));

        TAG_FALLBACK.put("minecraft:signs", Set.of(
                Items.OAK_SIGN, Items.SPRUCE_SIGN, Items.BIRCH_SIGN, Items.JUNGLE_SIGN,
                Items.ACACIA_SIGN, Items.DARK_OAK_SIGN, Items.MANGROVE_SIGN, Items.CHERRY_SIGN,
                Items.BAMBOO_SIGN, Items.CRIMSON_SIGN, Items.WARPED_SIGN));

        TAG_FALLBACK.put("minecraft:hanging_signs", Set.of(
                Items.OAK_HANGING_SIGN, Items.SPRUCE_HANGING_SIGN, Items.BIRCH_HANGING_SIGN, Items.JUNGLE_HANGING_SIGN,
                Items.ACACIA_HANGING_SIGN, Items.DARK_OAK_HANGING_SIGN, Items.MANGROVE_HANGING_SIGN, Items.CHERRY_HANGING_SIGN,
                Items.BAMBOO_HANGING_SIGN, Items.CRIMSON_HANGING_SIGN, Items.WARPED_HANGING_SIGN));
    }

    private MatchCondition(Kind kind, String value) {
        this.kind = kind;
        this.value = value;
    }

    public static MatchCondition fromJson(JsonObject obj) {
        String type = obj.get("type").getAsString();
        String value = obj.has("value") ? obj.get("value").getAsString() : null;

        return switch (type) {
            case "item_id"        -> new MatchCondition(Kind.ITEM_ID, value);
            case "item_tag"       -> new MatchCondition(Kind.ITEM_TAG, value);
            case "item_component" -> new MatchCondition(Kind.ITEM_COMPONENT, value);
            case "item_kind"      -> new MatchCondition(Kind.ITEM_KIND, value);
            case "item_default"   -> new MatchCondition(Kind.ITEM_DEFAULT, null);
            case "entity_id"      -> new MatchCondition(Kind.ENTITY_ID, value);
            case "entity_tag"     -> new MatchCondition(Kind.ENTITY_TAG, value);
            case "entity_kind"    -> new MatchCondition(Kind.ENTITY_KIND, value);
            case "entity_default" -> new MatchCondition(Kind.ENTITY_DEFAULT, null);
            default -> throw new IllegalArgumentException("Unknown match type: " + type);
        };
    }

    public boolean isItemKind()   { return kind == Kind.ITEM_ID || kind == Kind.ITEM_TAG
            || kind == Kind.ITEM_COMPONENT || kind == Kind.ITEM_KIND || kind == Kind.ITEM_DEFAULT; }
    public boolean isEntityKind() { return kind == Kind.ENTITY_ID || kind == Kind.ENTITY_TAG
            || kind == Kind.ENTITY_KIND || kind == Kind.ENTITY_DEFAULT; }
    public boolean isDefault()    { return kind == Kind.ITEM_DEFAULT || kind == Kind.ENTITY_DEFAULT; }

    public boolean matchesItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (kind) {
            case ITEM_ID -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(value);
            case ITEM_TAG -> {
                String id = value.startsWith("#") ? value.substring(1) : value;
                Set<Item> members = TAG_FALLBACK.get(id);
                yield members != null && members.contains(stack.getItem());
            }
            case ITEM_COMPONENT -> {
                DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse(value));
                yield type != null && stack.getComponents().has(type);
            }
            case ITEM_KIND -> switch (value) {
                case "armor" -> stack.getItem() instanceof ArmorItem;
                case "block" -> stack.getItem() instanceof BlockItem;
                case "leaves" -> stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof LeavesBlock;
                case "enchanted" -> {
                    // FIX: was "item_component minecraft:enchantments", which
                    // matched EVERY item since the ENCHANTMENTS component is
                    // present-with-empty-value by default on all items in
                    // 1.21's component system, not just actually-enchanted
                    // ones — that's what made cherry leaves (and everything
                    // else) fall through to the "enchanted" profile instead
                    // of their real category. getEnchantments() + isEmpty()
                    // checks the actual content, not just presence.
                    ItemEnchantments ench = stack.getEnchantments();
                    yield ench != null && !ench.isEmpty();
                }
                case "channeling" -> {
                    // Holder<Enchantment>.is(ResourceKey) compares just the
                    // key, no registry/RegistryAccess lookup needed — safe
                    // to check from a plain ItemStack with no Level handy.
                    ItemEnchantments ench = stack.getEnchantments();
                    yield ench != null && ench.keySet().stream()
                            .anyMatch(h -> h.is(net.minecraft.world.item.enchantment.Enchantments.CHANNELING));
                }
                default -> false;
            };
            case ITEM_DEFAULT -> true;
            default -> false;
        };
    }

    public boolean matchesEntity(Entity entity) {
        return switch (kind) {
            case ENTITY_ID -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(value);
            case ENTITY_TAG -> {
                String id = value.startsWith("#") ? value.substring(1) : value;
                TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(id));
                yield entity.getType().is(tag);
            }
            case ENTITY_KIND -> switch (value) {
                // Charged Creeper is the SAME entity type as a normal
                // Creeper — only a boolean state (isPowered()) differs — so
                // this can't be an entity_id/entity_tag match, it needs an
                // actual state check like item_kind's "channeling" does.
                case "charged_creeper" -> entity instanceof net.minecraft.world.entity.monster.Creeper creeper
                        && creeper.isPowered();
                default -> false;
            };
            case ENTITY_DEFAULT -> true;
            default -> false;
        };
    }
}
