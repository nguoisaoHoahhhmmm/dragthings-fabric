package dragthings.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class DragDistanceHudRenderer {

    private static final double MIN_DIST = 1.0;
    private static final double MAX_DIST = 12.0;
    private static final long   ACTIVE_MS = 1800;

    private static float alpha   = 0f;
    private static float expandT = 0f;
    private static float dotT    = 0f;
    private static long  lastScrollTime = 0;

    // Layout
    private static final int BAR_HALF       = 54;
    private static final int DOT_R          = 3;
    private static final int Y_FROM_BOTTOM  = 40;  // px above bottom of screen

    public static void notifyScrolled() {
        lastScrollTime = System.currentTimeMillis();
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            boolean dragging = ItemDragHandler.isDragging();

            if (dragging) {
                long ago = System.currentTimeMillis() - lastScrollTime;
                float targetA = (ago < ACTIVE_MS) ? 1.0f : 0.55f;
                alpha   += (targetA - alpha)   * 0.14f;
                expandT += (1f      - expandT) * 0.16f;

                double dist = ItemDragHandler.getDynamicDragDistance();
                if (dist <= 0) dist = DragThingsConfig.get().getDragDistance();
                float t = (float)((dist - MIN_DIST) / (MAX_DIST - MIN_DIST));
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

            double dist = ItemDragHandler.getDynamicDragDistance();
            if (dist <= 0) dist = DragThingsConfig.get().getDragDistance();
            draw(gfx, mc, dist, cx, sh - Y_FROM_BOTTOM);
        });
    }

    private static void draw(GuiGraphics gfx, Minecraft mc, double dist, int cx, int barY) {
        int halfW = (int)(BAR_HALF * expandT);
        int x1    = cx - halfW;
        int x2    = cx + halfW;
        int ia    = Math.max(0, Math.min(255, (int)(alpha * 255f)));

        long  ago   = System.currentTimeMillis() - lastScrollTime;
        float blend = (ago < ACTIVE_MS) ? Math.max(0f, 1f - (float)ago / ACTIVE_MS) : 0f;
        int   rr    = (int)(0x55 + (0xFF - 0x55) * blend);

        int cBar = ((ia/2) << 24) | 0x00BBBBBB;
        int cCap = (ia << 24)     | 0x00FFFFFF;
        int cDot = (ia << 24)     | (rr << 16) | 0x0000FFFF;

        if (halfW > DOT_R + 2) {
            gfx.fill(x1 + DOT_R, barY - 1, x2 - DOT_R, barY + 1, cBar);
            gfx.fill(x1,     barY - 5, x1 + 1, barY + 6, cCap);
            gfx.fill(x2 - 1, barY - 5, x2,     barY + 6, cCap);
        }

        int dotXFull = x1 + DOT_R + (int)(dotT * Math.max(0, halfW * 2 - DOT_R * 2));
        int dotX     = cx + (int)((dotXFull - cx) * expandT);
        gfx.fill(dotX - DOT_R, barY - DOT_R, dotX + DOT_R, barY + DOT_R, cDot);

        if (expandT > 0.5f) {
            int labelIa = (int)(ia * ((expandT - 0.5f) / 0.5f));
            String text = String.format("%.1f", dist);
            int tw = mc.font.width(text);
            gfx.drawString(mc.font, text, dotX - tw / 2, barY + DOT_R + 4,
                    (labelIa << 24) | (rr << 16) | 0x0000FFFF, false);
        }
    }
}