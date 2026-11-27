package dragthings.client.gui.tooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides WHAT the tooltip says for an item stack. Knows nothing about
 * animation or drawing. Every item gets at least its name.
 *
 * The result is split by how it is shown:
 *  - name, hint, footer: plain colored text, no animation
 *  - body: the hovered tooltip (what vanilla shows in an inventory), shown
 *    inside the animated dividers while Shift is held
 */
public final class TooltipContent {

    private TooltipContent() {}

    /**
     * @param name   item name (+ stack count), rarity colored
     * @param hint   "[Shift] for ..." prompt, or null when there is nothing to reveal
     *               or Shift is already held
     * @param body   lines for the animated frame; empty when the frame should stay closed
     * @param footer action prompt such as "[R] to eat", or null
     */
    public record Parts(Component name, Component hint, List<Component> body, Component footer) {}

    private static final int CONTAINER_SLOTS = 27;

    // The vanilla tooltip is needed every frame while an item is hovered (to
    // know whether the "[Shift]" hint applies), so cache it per stack
    // contents and per F3+H state.
    private static ItemStack cachedStack = ItemStack.EMPTY;
    private static boolean cachedAdvanced = false;
    private static List<Component> cachedVanillaLines = List.of();

    public static Parts build(Minecraft mc, ItemStack stack, boolean shiftPressed) {
        // Name + optional stack count, tinted by the item's rarity tier.
        Component name = stack.getHoverName().copy().withStyle(rarityColor(stack.getRarity()));
        if (stack.getCount() > 1) {
            name = Component.literal("")
                    .append(name)
                    .append(Component.literal(" x" + stack.getCount()).withStyle(ChatFormatting.GRAY));
        }

        boolean isContainer = dragthings.ContainerUtil.isContainerItem(stack);
        FoodProperties food = stack.get(DataComponents.FOOD);
        boolean isFood = food != null;

        // Line 0 of the vanilla tooltip is the name, which is shown
        // separately, so only the lines after it count.
        List<Component> vanilla = getVanillaLines(mc, stack);
        boolean hasExtra = vanilla.size() > 1;

        Component hint = null;
        List<Component> body = List.of();

        if (isContainer) {
            // Vanilla's own shulker tooltip only lists the first few stacks,
            // so the full slot list replaces it.
            // The contents are shown by the container billboard (Shift), not in the tooltip.
            if (!shiftPressed) hint = dim("[Shift] for contents");
        } else if (hasExtra) {
            if (shiftPressed) body = new ArrayList<>(vanilla.subList(1, vanilla.size()));
            else hint = dim("[Shift] for description");
        } else if (shiftPressed && !isFood) {
            body = List.of(dim("No description"));
        }

        Component footer = isFood ? dim("[R] to eat") : null;
        return new Parts(name, hint, body, footer);
    }

    private static Component dim(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

    /**
     * The same lines vanilla shows when hovering the stack in an inventory,
     * honoring the F3+H "advanced tooltips" toggle. Line 0 is the name.
     */
    private static List<Component> getVanillaLines(Minecraft mc, ItemStack stack) {
        boolean advanced = mc.options.advancedItemTooltips;
        if (advanced != cachedAdvanced || !ItemStack.isSameItemSameComponents(cachedStack, stack)) {
            cachedStack = stack.copyWithCount(1);
            cachedAdvanced = advanced;
            cachedVanillaLines = stack.getTooltipLines(
                    Item.TooltipContext.of(mc.level),
                    mc.player,
                    advanced ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL);
        }
        return cachedVanillaLines;
    }

    /**
     * One line per occupied slot, in slot order, e.g. "#3  Diamond x12".
     * Reads the item's own CONTAINER component, which the client already has
     * through normal entity tracking.
     */
    private static List<Component> buildContainerContentLines(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        NonNullList<ItemStack> slots = NonNullList.withSize(CONTAINER_SLOTS, ItemStack.EMPTY);
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) contents.copyInto(slots);

        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = slots.get(i);
            if (s.isEmpty()) continue;
            Component line = Component.literal("#" + i + "  ").withStyle(ChatFormatting.GRAY)
                    .copy()
                    .append(s.getHoverName().copy().withStyle(rarityColor(s.getRarity())))
                    .append(Component.literal(" x" + s.getCount()).withStyle(ChatFormatting.GRAY));
            lines.add(line);
        }
        if (lines.isEmpty()) {
            lines.add(dim("(empty)"));
        }
        return lines;
    }

    /**
     * Rarity has no style accessor in this mapping, and its 4 colors are
     * stable public constants, so they are mapped by hand.
     */
    private static ChatFormatting rarityColor(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> ChatFormatting.WHITE;
            case UNCOMMON -> ChatFormatting.YELLOW;
            case RARE -> ChatFormatting.AQUA;
            case EPIC -> ChatFormatting.LIGHT_PURPLE;
        };
    }
}