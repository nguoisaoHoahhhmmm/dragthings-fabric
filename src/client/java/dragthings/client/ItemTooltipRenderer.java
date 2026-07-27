package dragthings.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ItemTooltipRenderer {

    // FIX: Separated combat timeout — was 1200ms and hid tooltip during normal walking
    private static final long ATTACK_SUPPRESS_MS  = 600;
    private static final long HURT_SUPPRESS_MS    = 400;

    private static long lastAttackTime = 0;
    private static long lastHurtTime   = 0;

    // Smooth fade
    private static float tooltipAlpha = 0f;
    private static final float FADE_IN_SPEED  = 0.12f;
    private static final float FADE_OUT_SPEED = 0.20f;

    private static final int CONTAINER_SLOTS = 27;

    public static void init() {
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            DragThingsConfig cfg = DragThingsConfig.get();
            if (!cfg.showTooltip) { tooltipAlpha = 0f; return; }

            // Hide when dragging or in a screen/menu
            if (ItemDragHandler.isDragging()) {
                tooltipAlpha = Math.max(0f, tooltipAlpha - FADE_OUT_SPEED);
                return;
            }
            if (mc.screen != null) { tooltipAlpha = 0f; return; }

            ItemEntity hoveredItem = ItemDragHandler.getHoveredItem();
            boolean shouldShow = hoveredItem != null && !isInCombatMode(mc);

            if (shouldShow) {
                tooltipAlpha = Math.min(1f, tooltipAlpha + FADE_IN_SPEED);
            } else {
                tooltipAlpha = Math.max(0f, tooltipAlpha - FADE_OUT_SPEED);
            }

            if (tooltipAlpha > 0.01f && hoveredItem != null) {
                renderItemTooltip(guiGraphics, hoveredItem, tooltipAlpha);
            }
        });
    }

    private static void updateCombatState(Minecraft mc) {
        if (mc.options.keyAttack.isDown()) {
            lastAttackTime = System.currentTimeMillis();
        }
        if (mc.player.hurtTime > 0) {
            lastHurtTime = System.currentTimeMillis();
        }
    }

    private static boolean isInCombatMode(Minecraft mc) {
        updateCombatState(mc);
        // FIX: Only suppress during active attack window or when recently hurt.
        // Movement alone no longer hides the tooltip (was the main annoyance).
        if (mc.options.keyAttack.isDown()) return true;
        if (System.currentTimeMillis() - lastAttackTime < ATTACK_SUPPRESS_MS) return true;
        if (System.currentTimeMillis() - lastHurtTime   < HURT_SUPPRESS_MS)   return true;
        return false;
    }

    /**
     * Chest / Trapped Chest / Barrel / Shulker Box — anything whose contents
     * are worth previewing. Delegates to ContainerUtil (shared common code)
     * so this check stays in sync with the server-side ritual trigger in
     * Dragthings.java instead of drifting apart as a separate duplicate.
     */
    private static boolean isPreviewableContainer(ItemStack stack) {
        return dragthings.ContainerUtil.isContainerItem(stack);
    }

    /**
     * One line per occupied slot, in slot order — e.g. "#3  Diamond x12".
     * Reads straight from the item's own CONTAINER data component, which
     * the client already has via normal entity tracking (no extra
     * networking needed, same data ChestLootRitual reads server-side).
     *
     * Each item's own name is tinted by its rarity tier, same as the main
     * hovered item's name — slot index and count stay gray so the rarity
     * color stands out as the thing that actually varies per line.
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
            lines.add(Component.literal("(empty)").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        return lines;
    }

    /**
     * FIX: Rarity has no getStyle()-style accessor in this mapping (that
     * method belongs to a different class — Attribute — not Rarity; the
     * internal Rarity API has churned across versions). The 4 rarity
     * colors themselves are stable, public-facing constants unlikely to
     * change, so hand-mapping them directly is more robust than chasing
     * whatever internal accessor Rarity happens to expose this version.
     */
    private static ChatFormatting rarityColor(net.minecraft.world.item.Rarity rarity) {
        return switch (rarity) {
            case COMMON -> ChatFormatting.WHITE;
            case UNCOMMON -> ChatFormatting.YELLOW;
            case RARE -> ChatFormatting.AQUA;
            case EPIC -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    private static void renderItemTooltip(net.minecraft.client.gui.GuiGraphics graphics,
                                          ItemEntity itemEntity, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth  = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        ItemStack stack = itemEntity.getItem();
        List<Component> lines = new ArrayList<>();

        // Item name + optional stack count — tinted by the item's vanilla
        // rarity tier (COMMON=white, UNCOMMON=yellow, RARE=aqua, EPIC=pink),
        // same colors vanilla's own tooltip uses.
        Component itemName = stack.getHoverName().copy().withStyle(rarityColor(stack.getRarity()));
        if (stack.getCount() > 1) {
            itemName = Component.literal("")
                    .append(itemName)
                    .append(Component.literal(" x" + stack.getCount())
                            .withStyle(ChatFormatting.GRAY));
        }
        lines.add(itemName);

        boolean shiftPressed =
                GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)  == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        boolean hasEnchantments = enchantments != null && !enchantments.isEmpty();
        boolean isContainer     = isPreviewableContainer(stack);

        if (shiftPressed) {
            if (hasEnchantments) {
                enchantments.entrySet().forEach(entry -> {
                    String enchantName = entry.getKey().value().description().getString();
                    int level = entry.getIntValue();
                    lines.add(Component.literal("  ")
                            .append(Component.literal(enchantName).withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(" " + toRoman(level)).withStyle(ChatFormatting.GRAY)));
                });
            } else if (isContainer) {
                // Same slot-by-slot listing ContainerPreviewRenderer used to
                // draw as a 3D world-space billboard — now folded into this
                // existing 2D HUD tooltip instead, reusing the same
                // "Shift reveals more" pattern already built for enchants.
                for (Component line : buildContainerContentLines(stack)) {
                    lines.add(Component.literal("  ").append(line));
                }
            } else {
                lines.add(Component.literal("No description")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        } else if (hasEnchantments) {
            lines.add(Component.literal("[Shift] for description")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (isContainer) {
            lines.add(Component.literal("[Shift] for contents")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }

        int lineHeight = mc.font.lineHeight + 2;
        int currentY   = (screenHeight / 2) + 15;

        int alphaInt = (int)(alpha * 255f) & 0xFF;
        int textColor = (alphaInt << 24) | 0xFFFFFF;

        for (Component line : lines) {
            int x = (screenWidth - mc.font.width(line)) / 2;
            graphics.drawString(mc.font, line, x, currentY, textColor, true);
            currentY += lineHeight;
        }
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";   case 2 -> "II";  case 3 -> "III";
            case 4 -> "IV";  case 5 -> "V";   case 6 -> "VI";
            case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}