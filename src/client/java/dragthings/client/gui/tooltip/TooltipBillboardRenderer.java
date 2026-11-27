package dragthings.client.gui.tooltip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * BILLBOARD mode: the same tooltip, but as a panel in the world. It floats
 * beside the hovered item, always turned toward the camera, and gets smaller
 * the farther away you are (kept legible by a clamp).
 *
 * Layout in "pixels" (one pixel = TooltipSettings.billboardPixelSize() blocks
 * at 2.5 blocks away): the panel's left edge is x = 0 and its vertical middle
 * is y = 0; y grows downward like in the GUI.
 */
public final class TooltipBillboardRenderer {

    private static final int LINE_RGB = 0xFFFFFF;
    private static final int BACKDROP_ALPHA = 0x66;
    private static final float END_FADE = 40f;
    private static final int MAX_BODY_HEIGHT = 170; // taller bodies become columns

    // Tiny z steps so layers do not fight: backdrop < lines < text.
    private static final float Z_LINE = 0.2f;
    private static final float Z_TEXT = 0.6f;

    private TooltipBillboardRenderer() {}

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(TooltipBillboardRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (TooltipSettings.mode() != TooltipMode.BILLBOARD) return;
        ItemEntity entity = ItemTooltipRenderer.currentEntity();
        TooltipContent.Parts parts = ItemTooltipRenderer.currentParts();
        TooltipAnimator anim = ItemTooltipRenderer.currentAnimator();
        float textAlpha = ItemTooltipRenderer.currentTextAlpha();
        if (entity == null || parts == null || ctx.consumers() == null) return;
        if (textAlpha <= 0.01f && !anim.isVisible()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        List<Component> body = ItemTooltipRenderer.currentBody();
        boolean frameVisible = anim.isVisible() && !body.isEmpty();

        float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        Vec3 cam = ctx.camera().getPosition();
        Vector3f left = ctx.camera().getLeftVector();
        float gap = TooltipSettings.billboardGap();
        Vec3 anchor = entity.getPosition(partial).add(0, 0.3, 0)
                .add(-left.x * gap, -left.y * gap, -left.z * gap);

        // What goes in the panel, top to bottom
        int lineH = font.lineHeight + 2;
        TooltipLayout layout = frameVisible ? TooltipLayout.of(font, body, MAX_BODY_HEIGHT) : null;
        int panelW = font.width(parts.name());
        if (parts.hint() != null) panelW = Math.max(panelW, font.width(parts.hint()));
        if (parts.footer() != null) panelW = Math.max(panelW, font.width(parts.footer()));
        if (layout != null) panelW = Math.max(panelW, layout.width);

        int totalH = lineH;
        totalH += frameVisible ? layout.height + 4 : (parts.hint() != null ? lineH : 0);
        if (parts.footer() != null) totalH += lineH;

        // Distance-based size
        float dist = (float) cam.distanceTo(anchor);
        float s = TooltipSettings.billboardPixelSize() * Mth.clamp(dist / 2.5f, 0.7f, 3f);

        PoseStack ps = ctx.matrixStack();
        MultiBufferSource buffers = ctx.consumers();
        ps.pushPose();
        ps.translate(anchor.x - cam.x, anchor.y - cam.y, anchor.z - cam.z);
        ps.mulPose(ctx.camera().rotation());
        ps.scale(s, -s, s); // y down, like the GUI
        Matrix4f m = ps.last().pose();
        Matrix4f mt = new Matrix4f(m).translate(0f, 0f, Z_TEXT);

        float cx = panelW / 2f;
        float y = -totalH / 2f;
        int textA = (int) (textAlpha * 255f) & 0xFF;

        drawText(font, parts.name(), cx, y, textA, mt, buffers);
        y += lineH;

        if (frameVisible) {
            y = drawFrame(font, anim, layout, cx, y, m, mt, buffers) + 4;
        } else if (parts.hint() != null) {
            drawText(font, parts.hint(), cx, y, textA, mt, buffers);
            y += lineH;
        }
        if (parts.footer() != null) {
            drawText(font, parts.footer(), cx, y, textA, mt, buffers);
        }
        ps.popPose();
    }

    /** The animated frame; returns the y below its lower line. */
    private static float drawFrame(Font font, TooltipAnimator anim, TooltipLayout layout, float cx, float top,
                                   Matrix4f m, Matrix4f mt, MultiBufferSource buffers) {
        anim.setTargetSize(layout.width, layout.height);
        float w = anim.width();
        float h = anim.height();
        float lineT = anim.lineT();
        float openT = anim.openT();

        float topY = top;
        float bottomY = top + openT * h;
        float halfW = w * lineT / 2f;
        int lineAlpha = Math.round(255f * Math.min(1f, lineT * 1.5f));

        VertexConsumer vc = buffers.getBuffer(RenderType.gui());
        if (bottomY - topY > 1f) {
            TooltipFrameRenderer.fadeBar(vc, m, cx, halfW, topY + 1, bottomY, 0f, 0x000000,
                    Math.round(BACKDROP_ALPHA * openT));
        }
        TooltipFrameRenderer.fadeBar(vc, m, cx, halfW, topY, topY + 1, Z_LINE, LINE_RGB, lineAlpha);
        if (bottomY > topY) {
            TooltipFrameRenderer.fadeBar(vc, m, cx, halfW, bottomY, bottomY + 1, Z_LINE, LINE_RGB, lineAlpha);
        }

        // No scissor in the world: a line of text shows once the gap has
        // opened far enough to hold it, fading in with the opening.
        int alpha = Math.round(255f * openT);
        if (alpha >= 8) {
            int color = (alpha << 24) | 0xFFFFFF;
            float left = cx - layout.width / 2f;
            for (int c = 0; c < layout.columns.size(); c++) {
                float ly = top + TooltipLayout.PADDING_Y;
                for (Component line : layout.columns.get(c)) {
                    if (ly + font.lineHeight <= bottomY) {
                        float lx = layout.isSingleColumn()
                                ? cx - font.width(line) / 2f
                                : left + layout.columnX[c];
                        font.drawInBatch(line, lx, ly, color, true, mt, buffers,
                                Font.DisplayMode.NORMAL, 0, 0xF000F0);
                    }
                    ly += layout.lineHeight;
                }
            }
        }
        return bottomY + 1;
    }

    private static void drawText(Font font, Component text, float cx, float y, int alpha,
                                 Matrix4f m, MultiBufferSource buffers) {
        if (alpha < 4) return; // the font treats very small alpha as opaque
        font.drawInBatch(text, cx - font.width(text) / 2f, y, (alpha << 24) | 0xFFFFFF, true, m, buffers,
                Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }
}