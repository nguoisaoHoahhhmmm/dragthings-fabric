package dragthings.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import dragthings.mobdrag.MobDragHandler;

public class DragDistanceHudRenderer {

    private static final double ITEM_MIN_DIST = 1.0;
    private static final double ITEM_MAX_DIST = 12.0;
    private static final long   ACTIVE_MS = 1800;

    private static float alpha   = 0f;
    private static float expandT = 0f;
    private static float dotT    = 0f;
    private static long  lastScrollTime = 0;

    // Layout
    private static final int BAR_HALF       = 55;
    private static final int Y_FROM_BOTTOM  = 70;
    private static final int TICK_HEIGHT    = 5; // Chiều cao của 2 vạch chặn ở 2 đầu |

    // Palette màu sắc mới
    private static final int COLOR_WHITE  = 0xFFFFFF; // Màu trắng cho thanh track
    private static final int COLOR_BLUE   = 0x38B6FF; // Màu xanh dương cho dấu +
    private static final int COLOR_BORDER = 0x000000; // Viền đen chuẩn Vanilla UI

    public static void notifyScrolled() {
        lastScrollTime = System.currentTimeMillis();
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            boolean itemDragging = ItemDragHandler.isDragging();
            boolean mobDragging  = MobDragHandler.isDragging();
            boolean dragging     = itemDragging || mobDragging;

            if (dragging) {
                long ago = System.currentTimeMillis() - lastScrollTime;
                float targetA = (ago < ACTIVE_MS) ? 1.0f : 0.55f;
                alpha   += (targetA - alpha)   * 0.14f;
                expandT += (1f      - expandT) * 0.16f;

                double dist, minDist, maxDist;
                if (itemDragging) {
                    dist = ItemDragHandler.getDynamicDragDistance();
                    if (dist <= 0) dist = DragThingsConfig.get().getDragDistance();
                    minDist = ITEM_MIN_DIST;
                    maxDist = ITEM_MAX_DIST;
                } else {
                    dist = MobDragHandler.getDynamicHoldDistance();
                    minDist = MobDragHandler.getMinHoldDistance();
                    maxDist = MobDragHandler.getMaxHoldDistance();
                }

                float t = (float)((dist - minDist) / (maxDist - minDist));
                dotT += (Math.max(0f, Math.min(1f, t)) - dotT) * 0.20f;
            } else {
                alpha   *= 0.84f;
                expandT *= 0.84f;
                if (alpha   < 0.005f) alpha   = 0f;
                if (expandT < 0.005f) expandT = 0f;
            }
        });

        HudRenderCallback.EVENT.register((gfx, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();
            int cx = sw / 2;

            if (alpha < 0.01f || expandT < 0.01f) return;

            double dist;
            if (ItemDragHandler.isDragging()) {
                dist = ItemDragHandler.getDynamicDragDistance();
                if (dist <= 0) dist = DragThingsConfig.get().getDragDistance();
            } else if (MobDragHandler.isDragging()) {
                dist = MobDragHandler.getDynamicHoldDistance();
            } else {
                return;
            }
            draw(gfx, mc, dist, cx, sh - Y_FROM_BOTTOM);
        });
    }

    private static void draw(GuiGraphics gfx, Minecraft mc, double dist, int cx, int barY) {
        int halfW = (int)(BAR_HALF * expandT);
        int x1    = cx - halfW;
        int x2    = cx + halfW;
        int ia    = Math.max(0, Math.min(255, (int)(alpha * 255f)));

        if (halfW <= 8) return;

        int argbBorder = (ia << 24) | COLOR_BORDER;
        int argbWhite  = (ia << 24) | COLOR_WHITE;
        int argbBlue   = (ia << 24) | COLOR_BLUE;

        // 1. Vạch ngang chính (Dạng '-') màu trắng
        fillWithBorder(gfx, x1, barY, x2, barY + 1, argbWhite, argbBorder);

        // 2. Hai vạch chặn ở 2 đầu (Dạng '|') màu trắng
        fillWithBorder(gfx, x1 - 1, barY - TICK_HEIGHT, x1, barY + TICK_HEIGHT + 1, argbWhite, argbBorder);
        fillWithBorder(gfx, x2, barY - TICK_HEIGHT, x2 + 1, barY + TICK_HEIGHT + 1, argbWhite, argbBorder);

        // 3. Vị trí con trượt dấu '+' màu xanh dương — không viền, 2 nét cùng
        // độ dày 3px và đối xứng thật quanh tâm (trước đây nét dọc chỉ 2px
        // lệch tâm 0.5px so với nét ngang 3px, gây cảm giác méo/lệch).
        int sliderX = x1 + (int)(dotT * (x2 - x1));

        // Nét đứng của dấu '+' (3px rộng, đối xứng quanh sliderX)
        gfx.fill(sliderX - 1, barY - 4, sliderX + 2, barY + 5, argbBlue);
        // Nét ngang của dấu '+' (3px cao, đối xứng quanh barY)
        gfx.fill(sliderX - 4, barY - 1, sliderX + 5, barY + 2, argbBlue);

        // 4. Nhãn khoảng cách (Text hiển thị số mét/block)
        if (expandT > 0.5f) {
            int labelAlpha = (int)(ia * ((expandT - 0.5f) / 0.5f));
            String text = String.format("%.1fm", dist);
            int tw = mc.font.width(text);

            gfx.drawString(mc.font, text, sliderX - tw / 2, barY + 7,
                    (labelAlpha << 24) | COLOR_WHITE, true);
        }
    }

    /**
     * Hàm vẽ hình chữ nhật có viền đen bao quanh giúp UI nổi bật trên mọi nền game
     */
    private static void fillWithBorder(GuiGraphics gfx, int minX, int minY, int maxX, int maxY, int color, int borderColor) {
        // Viền đen phía sau
        gfx.fill(minX - 1, minY - 1, maxX + 1, maxY + 1, borderColor);
        // Khối màu chính phía trước
        gfx.fill(minX, minY, maxX, maxY, color);
    }
}