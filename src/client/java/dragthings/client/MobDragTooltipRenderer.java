package dragthings.client;

import dragthings.mobdrag.MobDragHandler;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/**
 * Same centered-text HUD spot ItemTooltipRenderer uses for hovered items,
 * but for mobs: shows the mob's name plus an interaction hint whenever the
 * player is looking at something MobDragHandler considers grabbable.
 *
 * Deliberately simpler than ItemTooltipRenderer — no Shift-to-expand
 * content, no fade timing tied to combat state. A hovered mob is a much
 * more momentary thing than a hovered item sitting still on the ground, so
 * a plain instant show/hide (no alpha fade) reads better here and avoids
 * fighting with MobDragGlowMixin's outline, which also just snaps on/off.
 */
public class MobDragTooltipRenderer {

    public static void init() {
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            if (mc.screen != null) return;
            if (!DragThingsConfig.get().showTooltip) return;

            // Don't show the "grab" hint while a drag (item or mob) is
            // already active, or the hint would linger over whatever the
            // player happens to be looking at mid-drag.
            if (MobDragHandler.isDragging() || dragthings.client.ItemDragHandler.isDragging()) return;

            LivingEntity hovered = MobDragHandler.getHoveredMob();
            if (hovered == null) return;

            int screenWidth  = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int lineHeight   = mc.font.lineHeight + 2;
            int currentY     = (screenHeight / 2) + 15;

            Component nameLine = hovered.getDisplayName().copy().withStyle(ChatFormatting.WHITE);
            int nameX = (screenWidth - mc.font.width(nameLine)) / 2;
            guiGraphics.drawString(mc.font, nameLine, nameX, currentY, 0xFFFFFFFF, true);
            currentY += lineHeight;

            Component hintLine = Component.literal("[Sneak + Right Click] to grab")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
            int hintX = (screenWidth - mc.font.width(hintLine)) / 2;
            guiGraphics.drawString(mc.font, hintLine, hintX, currentY, 0xFFFFFFFF, true);
        });
    }
}