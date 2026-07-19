package dragthings.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ItemTooltipRenderer {

    private static final long ATTACK_SUPPRESS_MS  = 600;
    private static final long HURT_SUPPRESS_MS    = 400;

    private static long lastAttackTime = 0;
    private static long lastHurtTime   = 0;

    private static float tooltipAlpha = 0f;
    private static final float FADE_IN_SPEED  = 0.12f;
    private static final float FADE_OUT_SPEED = 0.20f;

    private static final int CONTAINER_SLOTS = 27;

    public static void init() {
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Giả định config của bạn đã sẵn sàng
            DragThingsConfig cfg = DragThingsConfig.get();
            if (!cfg.showTooltip) { tooltipAlpha = 0f; return; }

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
        if (mc.options.keyAttack.isDown()) return true;
        if (System.currentTimeMillis() - lastAttackTime < ATTACK_SUPPRESS_MS) return true;
        if (System.currentTimeMillis() - lastHurtTime   < HURT_SUPPRESS_MS)   return true;
        return false;
    }

    private static boolean isPreviewableContainer(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        var block = blockItem.getBlock();
        return block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock;
    }

    private static List<Component> buildContainerContentLines(ItemStack stack) {
        List<Component> lines = new ArrayList<>();

        // Lấy thành phần chứa đồ trực tiếp từ Component
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            int slotIndex = 0;

            // Sử dụng contents.stream() để quét qua mọi ô, kể cả các ô trống
            // Điều này giúp lấy chính xác index thực tế của item trong hòm
            List<ItemStack> allSlots = contents.stream().toList();

            for (int i = 0; i < allSlots.size(); i++) {
                ItemStack slotStack = allSlots.get(i);

                // Kiểm tra item thực sự tồn tại và không phải không khí (AIR)
                if (slotStack == null || slotStack.isEmpty()) continue;

                // Xử lý chuỗi văn bản an toàn: Ép tên hiển thị về dạng Component thuần túy
                Component itemName = slotStack.getHoverName();
                ChatFormatting rarityColor = slotStack.getRarity().color();

                Component line = Component.literal( (i + 1) + "." + "  ").withStyle(ChatFormatting.GRAY)
                        .copy()
                        .append(itemName.copy().withStyle(rarityColor))
                        .append(Component.literal(" x" + slotStack.getCount()).withStyle(ChatFormatting.GRAY));

                lines.add(line);
            }
        }

        if (lines.isEmpty()) {
            lines.add(Component.literal("(empty)").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        return lines;
    }

    private static void renderItemTooltip(GuiGraphics graphics, ItemEntity itemEntity, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth  = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        ItemStack stack = itemEntity.getItem();
        List<Component> lines = new ArrayList<>();

        Component itemName = stack.getHoverName().copy().withStyle(stack.getRarity().color());
        if (stack.getCount() > 1) {
            itemName = Component.literal("")
                    .append(itemName)
                    .append(Component.literal(" x" + stack.getCount()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(itemName);

        boolean shiftPressed = GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        boolean hasEnchantments = enchantments != null && !enchantments.isEmpty();
        boolean isContainer = isPreviewableContainer(stack);

        if (shiftPressed) {
            if (hasEnchantments) {
                enchantments.entrySet().forEach(entry -> {
                    // FIX 1.21.1: Trích xuất tên Enchantment trực tiếp qua phương thức tĩnh từ Enchantment class
                    Component enchantName = net.minecraft.world.item.enchantment.Enchantment.getFullname(entry.getKey(), entry.getIntValue());
                    lines.add(Component.literal("  ").append(enchantName));
                });
            } else if (isContainer) {
                for (Component line : buildContainerContentLines(stack)) {
                    lines.add(Component.literal("  ").append(line));
                }
            } else {
                lines.add(Component.literal("No information").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        } else if (hasEnchantments) {
            lines.add(Component.literal("[Shift] for information").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (isContainer) {
            lines.add(Component.literal("[Shift] for contents").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }

        int lineHeight = mc.font.lineHeight + 2;
        int currentY   = (screenHeight / 2) + 15;
        graphics.pose().pushPose();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        for (Component line : lines) {
            int x = (screenWidth - mc.font.width(line)) / 2;
            graphics.drawString(mc.font, line, x, currentY, 0xFFFFFF, true);
            currentY += lineHeight;
        }

        // 4. Khôi phục lại màu Shader hệ thống về mặc định (Alpha = 1.0f) để tránh làm mờ các UI khác của game
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();

        // 5. Khôi phục lại trạng thái ma trận render ban đầu
        graphics.pose().popPose();
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