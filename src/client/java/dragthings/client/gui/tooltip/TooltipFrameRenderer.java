package dragthings.client.gui.tooltip;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws the animated frame: two thin white lines that fade out toward both
 * ends, which draw out and then move apart, with the body text revealed in
 * the gap between them. Timing and size come from {@link TooltipAnimator};
 * the lines of text from {@link TooltipContent}. The name, hint and footer
 * are NOT drawn here; they are plain text.
 */
public final class TooltipFrameRenderer {

    private TooltipFrameRenderer() {}

    // ── Tuning (visual only) ──────────────────────────────────────────────
    /**
     * false: both lines start together at the top edge, the lower one drops
     * down. true: both start in the middle of the box and move apart.
     */
    private static final boolean OPEN_FROM_CENTER = false;
    private static final int LINE_RGB = 0xFFFFFF;
    private static final int LINE_THICKNESS = 1;
    /** How far in from each end the lines take to reach full brightness. */
    private static final float END_FADE_LENGTH = 40f;
    /** Dark backdrop between the lines; 0 turns it off. */
    private static final int BACKDROP_ALPHA = 0x66;
    private static final int BACKDROP_RGB = 0x000000;

    /** Frame centered on the screen, as long as it needs to be, never past the bottom. */
    public static int render(GuiGraphics g, Minecraft mc, TooltipAnimator anim,
                             List<Component> body, int top) {
        int screenH = mc.getWindow().getGuiScaledHeight();
        return renderAt(g, mc, anim, body, mc.getWindow().getGuiScaledWidth() / 2f, top,
                Math.max(40, screenH - top - 40));
    }

    /**
     * Draws one frame centered on {@code cx} with its upper edge at {@code top}.
     * Taller than {@code maxHeight} turns into several columns. Returns the Y
     * just below the lower line, so text under the frame can follow it as it
     * opens and closes.
     */
    public static int renderAt(GuiGraphics g, Minecraft mc, TooltipAnimator anim,
                               List<Component> body, float cx, int top, int maxHeight) {
        if (body.isEmpty()) return top;

        TooltipLayout layout = TooltipLayout.of(mc.font, body, maxHeight);
        anim.setTargetSize(layout.width, layout.height);

        float w = anim.width();
        float h = anim.height();
        float lineT = anim.lineT();
        float openT = anim.openT();

        float bottom = top + h;
        float anchor = OPEN_FROM_CENTER ? top + h / 2f : top;

        // Both lines start at the anchor and move out to the box edges.
        int topY = Math.round(anchor - openT * (anchor - top));
        int bottomY = Math.round(anchor + openT * (bottom - anchor));
        float lineHalfW = w * lineT / 2f;
        int lineAlpha = Math.round(255f * Math.min(1f, lineT * 1.5f));

        // Backdrop between the lines
        if (BACKDROP_ALPHA > 0 && bottomY - topY > LINE_THICKNESS) {
            fadeBar(g, cx, lineHalfW, topY + LINE_THICKNESS, bottomY,
                    BACKDROP_RGB, Math.round(BACKDROP_ALPHA * openT));
        }

        // The two lines
        fadeBar(g, cx, lineHalfW, topY, topY + LINE_THICKNESS, LINE_RGB, lineAlpha);
        if (bottomY > topY) {
            fadeBar(g, cx, lineHalfW, bottomY, bottomY + LINE_THICKNESS, LINE_RGB, lineAlpha);
        }
        g.flush(); // quads must be on screen before the scissor changes

        // Text, clipped to the gap between the lines so it is revealed as
        // they move apart.
        int textAlpha = Math.round(255f * openT);
        if (textAlpha >= 8 && bottomY - topY > LINE_THICKNESS) {
            g.enableScissor(
                    (int) Math.floor(cx - w / 2f), topY + LINE_THICKNESS,
                    (int) Math.ceil(cx + w / 2f), bottomY);
            int color = (textAlpha << 24) | 0xFFFFFF;
            float left = cx - layout.width / 2f;
            for (int c = 0; c < layout.columns.size(); c++) {
                int y = top + TooltipLayout.PADDING_Y;
                for (Component line : layout.columns.get(c)) {
                    int x = layout.isSingleColumn()
                            ? Math.round(cx - mc.font.width(line) / 2f)
                            : Math.round(left + layout.columnX[c]);
                    g.drawString(mc.font, line, x, y, color, true);
                    y += layout.lineHeight;
                }
            }
            g.disableScissor();
        }

        return bottomY + LINE_THICKNESS;
    }

    /**
     * A horizontal bar centered on cx that is fully opaque in the middle and
     * fades to transparent at both ends, drawn as three vertex-colored quads.
     */
    public static void fadeBar(GuiGraphics g, float cx, float halfW, float y1, float y2,
                               int rgb, int peakAlpha) {
        fadeBar(g.bufferSource().getBuffer(RenderType.gui()), g.pose().last().pose(),
                cx, halfW, y1, y2, 0f, rgb, peakAlpha);
    }

    /** Same bar into any vertex consumer / matrix (used by the world-space billboard). */
    public static void fadeBar(VertexConsumer vc, Matrix4f m, float cx, float halfW, float y1, float y2,
                               float z, int rgb, int peakAlpha) {
        if (halfW < 1f || peakAlpha <= 0 || y2 <= y1) return;

        float fade = Math.min(END_FADE_LENGTH, halfW * 0.5f);
        float xl = cx - halfW;
        float xr = cx + halfW;

        int clear = rgb & 0xFFFFFF;
        int solid = (Math.min(peakAlpha, 255) << 24) | (rgb & 0xFFFFFF);

        quad(vc, m, xl, xl + fade, y1, y2, z, clear, solid);
        quad(vc, m, xl + fade, xr - fade, y1, y2, z, solid, solid);
        quad(vc, m, xr - fade, xr, y1, y2, z, solid, clear);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x1, float x2, float y1, float y2, float z,
                             int colorLeft, int colorRight) {
        vc.addVertex(m, x1, y1, z).setColor(colorLeft);
        vc.addVertex(m, x1, y2, z).setColor(colorLeft);
        vc.addVertex(m, x2, y2, z).setColor(colorRight);
        vc.addVertex(m, x2, y1, z).setColor(colorRight);
    }
}