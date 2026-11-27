package dragthings.client.gui.tooltip;

import dragthings.client.DragThingsConfig;
import dragthings.client.EatDrinkHandler;
import dragthings.client.ItemDragHandler;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * HUD entry point for the hovered-item tooltip. Layout, top to bottom:
 *
 *   name                      plain colored text
 *   [Shift] for description   plain text, only while the frame is closed
 *   ───────────────────       animated dividers; the hovered (vanilla)
 *     tooltip lines           tooltip is inside them, open while Shift is held
 *   ───────────────────
 *   [R] to eat                plain text
 *
 * This class only decides WHEN things are shown; the rest lives in
 * dragthings.client.tooltip:
 *  - TooltipContent        what it says
 *  - TooltipAnimator       how far along the open/close animation is
 *  - TooltipFrameRenderer  how the frame is drawn
 */
public class ItemTooltipRenderer {

    // Combat suppression: hides the tooltip while attacking or just hurt.
    // Movement alone does not hide it.
    private static final long ATTACK_SUPPRESS_MS = 600;
    private static final long HURT_SUPPRESS_MS   = 400;

    private static long lastAttackTime = 0;
    private static long lastHurtTime   = 0;

    // Plain text just fades in and out; there is no other effect on it.
    private static float textAlpha = 0f;
    private static final float FADE_IN_SPEED  = 0.12f;
    private static final float FADE_OUT_SPEED = 0.20f;

    // Distance of the name line below the screen center.
    private static final int TOP_OFFSET = 15;

    // Eat progress bar dimensions: sits just under the tooltip, same "solid
    // gray bar" look as the drag-distance HUD.
    private static final int EAT_BAR_WIDTH  = 60;
    private static final int EAT_BAR_HEIGHT = 4;

    private static final TooltipAnimator animator = new TooltipAnimator();
    // Both are kept while closing, so nothing vanishes the moment the
    // player looks away or lets go of Shift.
    private static TooltipContent.Parts shown = null;
    private static List<Component> frameBody = List.of();
    private static ItemEntity shownEntity = null;

    // Read by TooltipBillboardRenderer
    public static ItemEntity currentEntity() { return shownEntity; }
    public static TooltipContent.Parts currentParts() { return shown; }
    public static List<Component> currentBody() { return frameBody; }
    public static TooltipAnimator currentAnimator() { return animator; }
    public static float currentTextAlpha() { return textAlpha; }

    public static void init() {
        TooltipAnchor.init();
        TooltipBillboardRenderer.init();
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            DragThingsConfig cfg = DragThingsConfig.get();
            if (!cfg.showTooltip || mc.screen != null) {
                resetAll();
                return;
            }

            ItemEntity hovered = ItemDragHandler.getHoveredItem();
            boolean wantOpen = !ItemDragHandler.isDragging()
                    && hovered != null
                    && !isInCombatMode(mc);

            if (wantOpen) {
                shown = TooltipContent.build(mc, hovered.getItem(), isShiftDown(mc));
                shownEntity = hovered;
            }

            textAlpha = wantOpen
                    ? Math.min(1f, textAlpha + FADE_IN_SPEED)
                    : Math.max(0f, textAlpha - FADE_OUT_SPEED);

            boolean wantFrame = wantOpen && shown != null && !shown.body().isEmpty();
            if (wantFrame) frameBody = shown.body();
            animator.tick(wantFrame);

            if (!animator.isVisible()) frameBody = List.of();
            if (textAlpha <= 0.01f && !animator.isVisible()) {
                shown = null;
                return;
            }
            if (shown == null) return;

            draw(graphics, mc, wantOpen ? hovered : null, tickDelta.getGameTimeDeltaPartialTick(false));
        });
    }

    private static void resetAll() {
        animator.reset();
        shown = null;
        frameBody = List.of();
        shownEntity = null;
        textAlpha = 0f;
    }

    private static void draw(GuiGraphics graphics, Minecraft mc, ItemEntity hoveredNow, float partial) {
        int lineHeight = mc.font.lineHeight + 2;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        boolean frameVisible = animator.isVisible() && !frameBody.isEmpty();

        // Where the panel goes: centre X, top Y and the height it may use.
        float cx = screenW / 2f;
        int y = screenH / 2 + TOP_OFFSET;
        int maxBody = Math.max(60, screenH - y - 50);

        TooltipMode mode = TooltipSettings.mode();
        if (mode == TooltipMode.BILLBOARD) {
            // Drawn in the world by TooltipBillboardRenderer; only the eat bar stays here.
            if (hoveredNow != null && EatDrinkHandler.isHolding()
                    && EatDrinkHandler.getHoveredFood() == hoveredNow) {
                renderEatProgressBar(graphics, mc, y);
            }
            return;
        }

        if (mode == TooltipMode.SIDE && shownEntity != null) {
            Vec3 at = shownEntity.getPosition(partial).add(0, 0.25, 0);
            float[] p = TooltipAnchor.project(at);
            if (p != null) {
                maxBody = (int) (screenH * 0.65f);
                int panelW = mc.font.width(shown.name());
                if (shown.hint() != null) panelW = Math.max(panelW, mc.font.width(shown.hint()));
                if (shown.footer() != null) panelW = Math.max(panelW, mc.font.width(shown.footer()));
                if (!frameBody.isEmpty()) {
                    panelW = Math.max(panelW, TooltipLayout.of(mc.font, frameBody, maxBody).width);
                }
                float leftX = p[0] + TooltipSettings.sideGap();
                if (leftX + panelW > screenW - 6) leftX = p[0] - TooltipSettings.sideGap() - panelW; // flip
                leftX = Mth.clamp(leftX, 6f, Math.max(6f, screenW - 6f - panelW));
                cx = leftX + panelW / 2f;
                y = Mth.clamp((int) p[1] - 24, 6, (int) (screenH * 0.3f));
                maxBody = Math.min(maxBody, screenH - y - 12 - lineHeight * 3);
            }
        }

        drawCentered(graphics, mc, shown.name(), cx, y);
        y += lineHeight;

        if (frameVisible) {
            // The text under the frame follows its lower line as it moves.
            y = TooltipFrameRenderer.renderAt(graphics, mc, animator, frameBody, cx, y, maxBody) + 3;
        } else if (shown.hint() != null) {
            drawCentered(graphics, mc, shown.hint(), cx, y);
            y += lineHeight;
        }

        if (shown.footer() != null) {
            drawCentered(graphics, mc, shown.footer(), cx, y);
            y += lineHeight;
        }

        if (hoveredNow != null && EatDrinkHandler.isHolding()
                && EatDrinkHandler.getHoveredFood() == hoveredNow) {
            renderEatProgressBar(graphics, mc, y, cx);
        }
    }

    private static void drawCentered(GuiGraphics graphics, Minecraft mc, Component text, float cx, int y) {
        // Font treats alpha below 4 as "no alpha given" and draws fully opaque.
        int alpha = (int) (textAlpha * 255f) & 0xFF;
        if (alpha < 4) return;
        int x = Math.round(cx - mc.font.width(text) / 2f);
        graphics.drawString(mc.font, text, x, y, (alpha << 24) | 0xFFFFFF, true);
    }

    private static boolean isShiftDown(Minecraft mc) {
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
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

    /**
     * Solid gray fill bar, same visual language as DragDistanceHudRenderer's
     * distance bar: a plain background rect plus a foreground rect scaled by
     * progress.
     */
    private static void renderEatProgressBar(GuiGraphics graphics, Minecraft mc, int topY) {
        renderEatProgressBar(graphics, mc, topY, mc.getWindow().getGuiScaledWidth() / 2f);
    }

    private static void renderEatProgressBar(GuiGraphics graphics, Minecraft mc, int topY, float cx) {
        int x = Math.round(cx - EAT_BAR_WIDTH / 2f);
        int y = topY + 2;

        graphics.fill(x, y, x + EAT_BAR_WIDTH, y + EAT_BAR_HEIGHT, 0x80404040);

        int filledWidth = (int) (EAT_BAR_WIDTH * EatDrinkHandler.getProgress());
        if (filledWidth > 0) {
            graphics.fill(x, y, x + filledWidth, y + EAT_BAR_HEIGHT, 0xFFDCDCDC);
        }
    }
}