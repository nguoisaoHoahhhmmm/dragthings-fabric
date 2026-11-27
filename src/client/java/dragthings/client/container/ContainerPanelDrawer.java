package dragthings.client.container;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dragthings.client.gui.tooltip.TooltipFrameRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws the container with Minecraft's OWN inventory GUI texture
 * (generic_54.png for chests and barrels, shulker_box.png for shulker boxes),
 * the same way the container screen does: slot positions, title position and
 * colours are the vanilla ones.
 *
 * It is drawn into a PoseStack that is already placed in the world, turned
 * toward the camera and scaled so that one unit = one GUI pixel (y down).
 * The panel's origin is the middle of its BOTTOM edge, so it grows upward.
 *
 * The texture is cut like ContainerScreen does for a 3-row container: the top
 * 71 rows (border, title, 3 rows of slots) plus the last 7 rows of the file,
 * which close the panel (the part below the player's hotbar).
 */
final class ContainerPanelDrawer {

    static final int COLUMNS = 9;
    static final int ROWS = 3;
    static final int SLOTS = COLUMNS * ROWS;

    // ── Vanilla layout, in texture pixels ─────────────────────────────────
    private static final int PANEL_W = 176;
    private static final int TOP_H = 17 + ROWS * 18;   // 71
    private static final int BOTTOM_H = 7;             // closing strip at the end of the file
    private static final int PANEL_H = TOP_H + BOTTOM_H;
    private static final int SLOT_X = 8;               // first item, from the panel's left
    private static final int SLOT_Y = 18;              // first item, from the panel's top
    private static final int SLOT_SIZE = 18;
    private static final int TEX_SIZE = 256;

    static final int WIDTH = PANEL_W;
    static final int HEIGHT = PANEL_H;

    // ── Dividers: well clear of the panel ─────────────────────────────────
    private static final int DIVIDER_GAP = 8;          // between a divider and the panel
    private static final int DIVIDER_EXTRA_W = 28;     // how far a divider reaches past each side

    // Layers (in pixels toward the camera). Items are flattened, so these stay tiny
    // and the icons cannot slide against the slots when seen at an angle.
    private static final float Z_PANEL = 0f;
    private static final float Z_LINE = 0.2f;
    private static final float Z_FLASH = 1.0f;
    private static final float Z_ITEM = 1.6f;
    private static final float Z_TEXT = 3.5f;

    private static final long FLASH_MS = 450L;

    private ContainerPanelDrawer() {}

    /**
     * @param appear     0..1 open animation (dividers draw out, then the panel fades in)
     * @param texture    the GUI texture to cut the panel from
     * @param texHeight  height in pixels of the GUI part of that texture (222 or 167)
     * @param flashAt    time (ms) each slot last changed, 0 = never
     */
    static void draw(Minecraft mc, PoseStack ps, MultiBufferSource buffers, List<ItemStack> slots,
                     long[] flashAt, long nowMs, float appear, Component title,
                     ResourceLocation texture, int texHeight) {
        Font font = mc.font;
        Matrix4f m = ps.last().pose();

        float lineT = Mth.clamp(appear / 0.4f, 0f, 1f);
        float openT = Mth.clamp((appear - 0.4f) / 0.6f, 0f, 1f);
        openT = openT * openT * (3f - 2f * openT);
        int a = Math.round(255f * openT);
        int lineAlpha = Math.round(255f * Math.min(1f, lineT * 1.5f));

        float left = -PANEL_W / 2f;
        float top = -PANEL_H;

        // NEVER keep a VertexConsumer across a call that asks the buffer source for a
        // different RenderType (text, items): that ends the open builder and the old
        // consumer then throws "Not building!". Ask for a fresh one every time.

        // The two dividers, outside the panel
        float halfW = (PANEL_W / 2f + DIVIDER_EXTRA_W) * lineT;
        TooltipFrameRenderer.fadeBar(buffers.getBuffer(RenderType.gui()), m, 0f, halfW,
                top - DIVIDER_GAP - 1, top - DIVIDER_GAP, Z_LINE, 0xFFFFFF, lineAlpha);
        TooltipFrameRenderer.fadeBar(buffers.getBuffer(RenderType.gui()), m, 0f, halfW,
                DIVIDER_GAP, DIVIDER_GAP + 1, Z_LINE, 0xFFFFFF, lineAlpha);
        if (a < 8) return;

        // Panel: the top part of the texture, then the closing strip
        blit(buffers, m, texture, left, top, PANEL_W, TOP_H, 0, 0, a);
        blit(buffers, m, texture, left, top + TOP_H, PANEL_W, BOTTOM_H, 0, texHeight - BOTTOM_H, a);

        // Title where the container screen puts it (8, 6), vanilla dark gray, no shadow
        if (a >= 8) {
            font.drawInBatch(title, left + 8, top + 6, (a << 24) | 0x404040, false,
                    new Matrix4f(m).translate(0f, 0f, Z_TEXT), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }

        float itemScale = 0.5f + 0.5f * openT;
        for (int i = 0; i < SLOTS; i++) {
            float x = left + SLOT_X + (i % COLUMNS) * SLOT_SIZE;   // top-left of the 16x16 item
            float y = top + SLOT_Y + (i / COLUMNS) * SLOT_SIZE;
            ItemStack stack = i < slots.size() ? slots.get(i) : ItemStack.EMPTY;

            long age = flashAt[i] == 0L ? Long.MAX_VALUE : nowMs - flashAt[i];
            if (age < FLASH_MS) {
                float f = 1f - age / (float) FLASH_MS;
                rect(buffers, m, x, y, x + 16, y + 16, Z_FLASH, (Math.round(0xA0 * f * openT) << 24) | 0xFFFFFF);
            }

            if (stack.isEmpty() || openT < 0.3f) continue;

            ps.pushPose();
            ps.translate(x + 8f, y + 8f, Z_ITEM);
            // 16 px icon, y flipped (models are y up), squashed flat so it cannot shift against the slot
            ps.scale(16f * itemScale, -16f * itemScale, 0.05f * itemScale);
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI, 0xF000F0,
                    OverlayTexture.NO_OVERLAY, ps, buffers, mc.level, i);
            ps.popPose();

            if (stack.getCount() > 1 && a >= 120) {
                String count = Integer.toString(stack.getCount());
                // Same spot as vanilla's item count: bottom right of the slot
                font.drawInBatch(count, x + 17 - font.width(count), y + 9, (a << 24) | 0xFFFFFF, true,
                        new Matrix4f(m).translate(0f, 0f, Z_TEXT), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            }
        }
    }

    /** One textured rectangle cut from the GUI texture; (u, v) is its top-left in the file. */
    private static void blit(MultiBufferSource buffers, Matrix4f m, ResourceLocation texture,
                             float x, float y, int w, int h, int u, int v, int alpha) {
        VertexConsumer vc = buffers.getBuffer(RenderType.text(texture));
        float u0 = u / (float) TEX_SIZE, u1 = (u + w) / (float) TEX_SIZE;
        float v0 = v / (float) TEX_SIZE, v1 = (v + h) / (float) TEX_SIZE;
        vc.addVertex(m, x,     y,     Z_PANEL).setColor(255, 255, 255, alpha).setUv(u0, v0).setLight(0xF000F0);
        vc.addVertex(m, x,     y + h, Z_PANEL).setColor(255, 255, 255, alpha).setUv(u0, v1).setLight(0xF000F0);
        vc.addVertex(m, x + w, y + h, Z_PANEL).setColor(255, 255, 255, alpha).setUv(u1, v1).setLight(0xF000F0);
        vc.addVertex(m, x + w, y,     Z_PANEL).setColor(255, 255, 255, alpha).setUv(u1, v0).setLight(0xF000F0);
    }

    private static void rect(MultiBufferSource buffers, Matrix4f m, float x1, float y1, float x2, float y2, float z, int argb) {
        VertexConsumer vc = buffers.getBuffer(RenderType.gui());
        vc.addVertex(m, x1, y1, z).setColor(argb);
        vc.addVertex(m, x1, y2, z).setColor(argb);
        vc.addVertex(m, x2, y2, z).setColor(argb);
        vc.addVertex(m, x2, y1, z).setColor(argb);
    }
}