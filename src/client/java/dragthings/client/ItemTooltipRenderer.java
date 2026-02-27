package dragthings.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ItemTooltipRenderer {


    private static final long COMBAT_TIMEOUT_MS = 1200;


    private static long lastAttackTime = 0;

    public static void init() {
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Hide tooltip when: dragging, in GUI, or in combat
            if (ItemDragHandler.isDragging()) return;
            if (mc.screen != null) return;
            if (isInCombatMode(mc)) return;

            ItemEntity hoveredItem = ItemDragHandler.getHoveredItem();
            if (hoveredItem != null) {
                renderItemTooltip(guiGraphics, hoveredItem);
            }
        });
    }


    private static void updateCombatState(Minecraft mc) {
        if (mc.options.keyAttack.isDown() || mc.player.hurtTime > 0) {
            lastAttackTime = System.currentTimeMillis();
        }
    }

    private static boolean isInCombatMode(Minecraft mc) {
        updateCombatState(mc);

        if (mc.options.keyAttack.isDown()) return true;

        long timeSinceAttack = System.currentTimeMillis() - lastAttackTime;
        if (timeSinceAttack < COMBAT_TIMEOUT_MS) {

            return mc.options.keyUp.isDown()
                    || mc.options.keyDown.isDown()
                    || mc.options.keyLeft.isDown()
                    || mc.options.keyRight.isDown()
                    || mc.options.keyJump.isDown();
        }

        return false;
    }

    private static void renderItemTooltip(net.minecraft.client.gui.GuiGraphics graphics, ItemEntity itemEntity) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        ItemStack stack = itemEntity.getItem();
        List<Component> lines = new ArrayList<>();

        // Item name (+ count if stacked)
        Component itemName = stack.getHoverName();
        if (stack.getCount() > 1) {
            itemName = Component.literal("")
                    .append(itemName)
                    .append(Component.literal(" x" + stack.getCount())
                            .withStyle(ChatFormatting.GRAY));
        }
        lines.add(itemName);


        boolean shiftPressed = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        boolean hasEnchantments = enchantments != null && !enchantments.isEmpty();

        if (shiftPressed) {
            if (hasEnchantments) {
                enchantments.entrySet().forEach(entry -> {
                    String enchantName = entry.getKey().value().description().getString();
                    int level = entry.getIntValue();
                    lines.add(Component.literal("  ")
                            .append(Component.literal(enchantName).withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(" " + toRoman(level)).withStyle(ChatFormatting.GRAY)));
                });
            } else {
                lines.add(Component.literal("No enchantments")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        } else if (hasEnchantments) {
            lines.add(Component.literal("[Shift] for enchantments")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }

        int lineHeight = mc.font.lineHeight + 2;
        int startY = (screenHeight / 2) + 15;

        int currentY = startY;
        for (Component line : lines) {
            int textWidth = mc.font.width(line);
            int x = (screenWidth - textWidth) / 2;
            graphics.drawString(mc.font, line, x, currentY, 0xFFFFFF, true);
            currentY += lineHeight;
        }
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}