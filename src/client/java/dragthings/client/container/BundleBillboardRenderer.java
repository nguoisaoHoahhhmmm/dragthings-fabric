package dragthings.client.container;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dragthings.client.DragThingsConfig;
import dragthings.client.ItemDragHandler;
import dragthings.client.gui.tooltip.TooltipFrameRenderer;
import dragthings.ritual.BundleRitual;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A billboard above a Bundle you are dragging (or hovering) showing what is in
 * it: the stacks in a small grid, and a bar for how full it is.
 *
 * A bundle has no inventory screen, so unlike the chest preview this panel is
 * drawn with plain rectangles in the tooltip style (dark panel, purple border)
 * rather than a vanilla GUI texture. Same switches as the container preview
 * (containerPreview in the config): enabled, showOnHover, requireShift.
 */
public final class BundleBillboardRenderer {

    private static final double MAX_DISTANCE = 16.0;
    private static final float HEIGHT_ABOVE = 0.5f;
    private static final float PIXEL_SIZE = 0.006f;
    private static final float OPEN_SECONDS = 0.3f;
    private static final float CLOSE_SECONDS = 0.2f;

    // ── Layout, in pixels ─────────────────────────────────────────────────
    private static final int COLUMNS = 6;
    private static final int CELL = 18;
    private static final int PAD = 8;
    private static final int TITLE_H = 12;
    private static final int BAR_H = 5;
    private static final int BAR_GAP = 6;
    private static final int PANEL_W = PAD * 2 + COLUMNS * CELL;      // 124
    private static final int DIVIDER_GAP = 8;
    private static final int DIVIDER_EXTRA_W = 22;

    // Layers (pixels toward the camera). Items are flattened so they cannot slide against the cells.
    private static final float Z_PANEL = 0f;
    private static final float Z_CELL = 0.3f;
    private static final float Z_FLASH = 1.0f;
    private static final float Z_ITEM = 1.6f;
    private static final float Z_TEXT = 3.5f;

    private static final int BG = 0xF0100010;        // vanilla tooltip colours
    private static final int BORDER_TOP = 0x505000FF;
    private static final int BORDER_BOTTOM = 0x5028007F;
    private static final int CELL_FILL = 0x30FFFFFF;
    private static final int BAR_BACK = 0xFF2B2B2B;
    private static final int BAR_FILL = 0xFF6A9BFF;
    private static final int BAR_FULL = 0xFFE5543D;

    private static final long FLASH_MS = 450L;

    private static ItemEntity shown = null;
    private static float appear = 0f;
    private static long lastNanos = 0L;

    private static List<ItemStack> current = new ArrayList<>();
    private static List<ItemStack> previous = null;
    private static final List<Long> FLASH = new ArrayList<>();

    private BundleBillboardRenderer() {}

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(BundleBillboardRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!cfg().enabled || mc.level == null || mc.player == null || ctx.consumers() == null) { reset(); return; }

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min((now - lastNanos) / 1_000_000_000f, 0.05f);
        lastNanos = now;

        ItemEntity target = pickTarget(mc);
        if (target != null) {
            if (target != shown) { shown = target; previous = null; }
            appear = Math.min(1f, appear + dt / OPEN_SECONDS);
        } else {
            appear = Math.max(0f, appear - dt / CLOSE_SECONDS);
        }
        if (shown == null || appear <= 0f) { if (appear <= 0f) shown = null; return; }
        if (!shown.isAlive() || !BundleRitual.isBundle(shown.getItem())) { reset(); return; }

        float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        Vec3 cam = ctx.camera().getPosition();
        Vec3 anchor = shown.getPosition(partial).add(0, HEIGHT_ABOVE, 0);
        double dist = cam.distanceTo(anchor);
        if (dist > MAX_DISTANCE) return;

        long nowMs = System.currentTimeMillis();
        BundleContents contents = shown.getItem().getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        readContents(contents, nowMs);
        float fullness = Mth.clamp(contents.weight().floatValue(), 0f, 1f);

        float s = PIXEL_SIZE * Mth.clamp((float) dist / 2.5f, 0.7f, 3f);
        PoseStack ps = ctx.matrixStack();
        ps.pushPose();
        ps.translate(anchor.x - cam.x, anchor.y - cam.y, anchor.z - cam.z);
        ps.mulPose(ctx.camera().rotation());
        ps.scale(s, -s, s);
        draw(mc, ps, ctx.consumers(), nowMs, fullness, shown.getItem().getHoverName());
        ps.popPose();
    }

    private static ItemEntity pickTarget(Minecraft mc) {
        if (cfg().requireShift && !isShiftDown(mc)) return null;
        ItemEntity dragged = ItemDragHandler.getDraggedItem();
        if (dragged != null) return BundleRitual.isBundle(dragged.getItem()) ? dragged : null;
        if (!cfg().showOnHover) return null;
        ItemEntity hovered = ItemDragHandler.getHoveredItem();
        return hovered != null && BundleRitual.isBundle(hovered.getItem()) ? hovered : null;
    }

    // ── Drawing ───────────────────────────────────────────────────────────

    /** Origin = the middle of the panel's BOTTOM edge, so it grows upward like the chest panel. */
    private static void draw(Minecraft mc, PoseStack ps, MultiBufferSource buffers, long nowMs,
                             float fullness, Component title) {
        Font font = mc.font;
        Matrix4f m = ps.last().pose();

        float lineT = Mth.clamp(appear / 0.4f, 0f, 1f);
        float openT = Mth.clamp((appear - 0.4f) / 0.6f, 0f, 1f);
        openT = openT * openT * (3f - 2f * openT);
        int a = Math.round(255f * openT);
        int lineAlpha = Math.round(255f * Math.min(1f, lineT * 1.5f));

        int rows = Math.max(1, (current.size() + COLUMNS - 1) / COLUMNS);
        int panelH = PAD + TITLE_H + rows * CELL + BAR_GAP + BAR_H + PAD;
        float left = -PANEL_W / 2f;
        float top = -panelH;

        // Never keep a VertexConsumer across a call that asks for another RenderType
        // (it ends the open builder: "Not building!"). Fresh buffer every time.
        float halfW = (PANEL_W / 2f + DIVIDER_EXTRA_W) * lineT;
        TooltipFrameRenderer.fadeBar(buffers.getBuffer(RenderType.gui()), m, 0f, halfW,
                top - DIVIDER_GAP - 1, top - DIVIDER_GAP, Z_CELL, 0xFFFFFF, lineAlpha);
        TooltipFrameRenderer.fadeBar(buffers.getBuffer(RenderType.gui()), m, 0f, halfW,
                DIVIDER_GAP, DIVIDER_GAP + 1, Z_CELL, 0xFFFFFF, lineAlpha);
        if (a < 8) return;

        // Panel: background and a 1px two-colour border
        rect(buffers, m, left, top, left + PANEL_W, top + panelH, Z_PANEL, withAlpha(BG, a));
        rect(buffers, m, left, top, left + PANEL_W, top + 1, Z_CELL, withAlpha(BORDER_TOP, a));
        rect(buffers, m, left, top + panelH - 1, left + PANEL_W, top + panelH, Z_CELL, withAlpha(BORDER_BOTTOM, a));
        rect(buffers, m, left, top + 1, left + 1, top + panelH - 1, Z_CELL, withAlpha(BORDER_TOP, a));
        rect(buffers, m, left + PANEL_W - 1, top + 1, left + PANEL_W, top + panelH - 1, Z_CELL, withAlpha(BORDER_BOTTOM, a));

        font.drawInBatch(title, left + PAD, top + PAD - 1, (a << 24) | 0xFFFFFF, false,
                new Matrix4f(m).translate(0f, 0f, Z_TEXT), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);

        float gridTop = top + PAD + TITLE_H;
        if (current.isEmpty() && a >= 120) {
            font.drawInBatch("Empty", left + PAD, gridTop + 4, (a << 24) | 0x909090, false,
                    new Matrix4f(m).translate(0f, 0f, Z_TEXT), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }

        float itemScale = 0.5f + 0.5f * openT;
        for (int i = 0; i < current.size(); i++) {
            float cx = left + PAD + (i % COLUMNS) * CELL;
            float cy = gridTop + (i / COLUMNS) * CELL;
            rect(buffers, m, cx, cy, cx + CELL - 1, cy + CELL - 1, Z_CELL, withAlpha(CELL_FILL, a));

            long flashAt = i < FLASH.size() ? FLASH.get(i) : 0L;
            long age = flashAt == 0L ? Long.MAX_VALUE : nowMs - flashAt;
            if (age < FLASH_MS) {
                float f = 1f - age / (float) FLASH_MS;
                rect(buffers, m, cx, cy, cx + CELL - 1, cy + CELL - 1, Z_FLASH,
                        (Math.round(0xA0 * f * openT) << 24) | 0xFFFFFF);
            }

            ItemStack stack = current.get(i);
            if (stack.isEmpty() || openT < 0.3f) continue;

            ps.pushPose();
            ps.translate(cx + 8.5f, cy + 8.5f, Z_ITEM);
            ps.scale(16f * itemScale, -16f * itemScale, 0.05f * itemScale);
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI, 0xF000F0,
                    OverlayTexture.NO_OVERLAY, ps, buffers, mc.level, i);
            ps.popPose();

            if (stack.getCount() > 1 && a >= 120) {
                String count = Integer.toString(stack.getCount());
                font.drawInBatch(count, cx + 17 - font.width(count), cy + 9, (a << 24) | 0xFFFFFF, true,
                        new Matrix4f(m).translate(0f, 0f, Z_TEXT), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            }
        }

        // Fullness bar
        float barY = gridTop + rows * CELL + BAR_GAP;
        float barW = PANEL_W - PAD * 2;
        rect(buffers, m, left + PAD, barY, left + PAD + barW, barY + BAR_H, Z_CELL, withAlpha(BAR_BACK, a));
        if (fullness > 0f) {
            int fill = fullness >= 1f ? BAR_FULL : BAR_FILL;
            rect(buffers, m, left + PAD, barY, left + PAD + barW * fullness, barY + BAR_H, Z_FLASH, withAlpha(fill, a));
        }
    }

    // ── Contents and change flash ─────────────────────────────────────────

    private static void readContents(BundleContents contents, long nowMs) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack s : contents.items()) list.add(s.copy());

        // Inserting shifts every cell by one, so match stacks by content, not by index:
        // a stack that was already there keeps its running flash, a new one starts flashing.
        List<Long> oldFlash = new ArrayList<>(FLASH);
        boolean[] used = previous == null ? new boolean[0] : new boolean[previous.size()];
        FLASH.clear();
        for (ItemStack s : list) {
            long flashAt = 0L;
            if (previous != null) {
                int found = -1;
                for (int j = 0; j < previous.size(); j++) {
                    if (!used[j] && sameStack(previous.get(j), s)) { found = j; break; }
                }
                if (found >= 0) {
                    used[found] = true;
                    flashAt = found < oldFlash.size() ? oldFlash.get(found) : 0L;
                } else {
                    flashAt = nowMs; // something new or changed
                }
            }
            FLASH.add(flashAt);
        }
        current = list;
        previous = new ArrayList<>(list);
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b) && a.getCount() == b.getCount();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static int withAlpha(int argb, int alpha) {
        int baseA = (argb >>> 24) & 0xFF;
        return (Math.round(baseA * (alpha / 255f)) << 24) | (argb & 0xFFFFFF);
    }

    private static void rect(MultiBufferSource buffers, Matrix4f m, float x1, float y1, float x2, float y2, float z, int argb) {
        VertexConsumer vc = buffers.getBuffer(RenderType.gui());
        vc.addVertex(m, x1, y1, z).setColor(argb);
        vc.addVertex(m, x1, y2, z).setColor(argb);
        vc.addVertex(m, x2, y2, z).setColor(argb);
        vc.addVertex(m, x2, y1, z).setColor(argb);
    }

    private static DragThingsConfig.ContainerPreviewConfig cfg() {
        return DragThingsConfig.get().containerPreview;
    }

    private static boolean isShiftDown(Minecraft mc) {
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static void reset() {
        shown = null;
        appear = 0f;
        previous = null;
        lastNanos = 0L;
        current = new ArrayList<>();
        FLASH.clear();
    }
}