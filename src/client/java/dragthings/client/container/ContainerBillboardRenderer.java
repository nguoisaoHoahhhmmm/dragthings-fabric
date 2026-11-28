package dragthings.client.container;

import com.mojang.blaze3d.vertex.PoseStack;
import dragthings.ContainerUtil;
import dragthings.client.DragThingsConfig;
import dragthings.client.ItemDragHandler;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;

/**
 * A billboard above a Chest / Trapped Chest / Barrel / Shulker Box item (and a dragged Ender Chest) that
 * shows what is inside, as a 3x9 slot grid with item icons and counts.
 *
 * Shown for the container you are dragging (so you watch it fill up while it
 * loots) and for one you are hovering. A slot flashes when its contents change.
 * This class decides WHEN and WHERE; ContainerPanelDrawer draws the panel.
 *
 * Needs the server to sync the stack, so ChestLootRitual must call
 * entity.setItem(copy) after changing the CONTAINER component.
 */
public final class ContainerBillboardRenderer {

    // ── Tuning (the on/off switches are in the Cloth config: containerPreview) ──
    private static final double MAX_DISTANCE = 16.0;
    /** Blocks between the item and the bottom of the panel. */
    private static final float HEIGHT_ABOVE = 0.5f;
    /** World size of one GUI pixel at 2.5 blocks away. */
    private static final float PIXEL_SIZE = 0.006f;
    private static final float OPEN_SECONDS = 0.35f;
    private static final float CLOSE_SECONDS = 0.2f;

    private static final ResourceLocation CHEST_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation SHULKER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/shulker_box.png");

    private static ItemEntity shown = null;
    private static float appear = 0f;
    private static long lastNanos = 0L;

    private static final long[] FLASH_AT = new long[ContainerPanelDrawer.SLOTS];
    private static NonNullList<ItemStack> current = emptySlots();
    private static NonNullList<ItemStack> previous = null;

    private ContainerBillboardRenderer() {}

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(ContainerBillboardRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!cfg().enabled || mc.level == null || mc.player == null || ctx.consumers() == null) { reset(); return; }

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min((now - lastNanos) / 1_000_000_000f, 0.05f);
        lastNanos = now;

        ItemEntity target = pickTarget(mc);
        if (target != null) {
            if (target != shown) { shown = target; previous = null; java.util.Arrays.fill(FLASH_AT, 0L); }
            appear = Math.min(1f, appear + dt / OPEN_SECONDS);
        } else {
            appear = Math.max(0f, appear - dt / CLOSE_SECONDS);
        }
        if (shown == null || appear <= 0f) { if (appear <= 0f) shown = null; return; }
        if (!shown.isAlive() || !isPreviewable(shown.getItem())) { reset(); return; }

        float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        Vec3 cam = ctx.camera().getPosition();
        Vec3 anchor = shown.getPosition(partial).add(0, HEIGHT_ABOVE, 0);
        double dist = cam.distanceTo(anchor);
        if (dist > MAX_DISTANCE) return;

        readContents(shown.getItem(), System.currentTimeMillis());

        float s = PIXEL_SIZE * Mth.clamp((float) dist / 2.5f, 0.7f, 3f);
        PoseStack ps = ctx.matrixStack();
        ps.pushPose();
        ps.translate(anchor.x - cam.x, anchor.y - cam.y, anchor.z - cam.z);
        ps.mulPose(ctx.camera().rotation());   // face the camera
        ps.scale(s, -s, s);                    // one unit = one GUI pixel, y down
        boolean shulker = shown.getItem().getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
        ContainerPanelDrawer.draw(mc, ps, ctx.consumers(), current, FLASH_AT,
                System.currentTimeMillis(), appear, shown.getItem().getHoverName(),
                shulker ? SHULKER_TEXTURE : CHEST_TEXTURE, shulker ? 167 : 222);
        ps.popPose();
    }

    /** The container being dragged, else (optionally) the one being hovered; none unless Shift is held. */
    private static ItemEntity pickTarget(Minecraft mc) {
        if (cfg().requireShift && !isShiftDown(mc)) return null;
        ItemEntity dragged = ItemDragHandler.getDraggedItem();
        if (dragged != null) return isPreviewable(dragged.getItem()) ? dragged : null;
        if (!cfg().showOnHover) return null;
        ItemEntity hovered = ItemDragHandler.getHoveredItem();
        return hovered != null && ContainerUtil.isContainerItem(hovered.getItem()) ? hovered : null;
    }

    /** Chests, barrels and shulker boxes; also a dragged Ender Chest (contents come from the server). */
    private static boolean isPreviewable(ItemStack stack) {
        return ContainerUtil.isContainerItem(stack) || EnderChestBillboardRenderer.isEnderChest(stack);
    }

    private static DragThingsConfig.ContainerPreviewConfig cfg() {
        return DragThingsConfig.get().containerPreview;
    }

    private static boolean isShiftDown(Minecraft mc) {
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static void readContents(ItemStack container, long nowMs) {
        NonNullList<ItemStack> slots = emptySlots();
        if (EnderChestBillboardRenderer.isEnderChest(container)) {
            List<ItemStack> ender = EnderChestBillboardRenderer.contents();
            if (ender != null) {
                for (int i = 0; i < slots.size() && i < ender.size(); i++) slots.set(i, ender.get(i));
            }
        } else {
            ItemContainerContents contents = container.get(DataComponents.CONTAINER);
            if (contents != null) contents.copyInto(slots);
        }

        if (previous != null) {
            for (int i = 0; i < slots.size(); i++) {
                ItemStack a = previous.get(i), b = slots.get(i);
                boolean same = ItemStack.isSameItemSameComponents(a, b) && a.getCount() == b.getCount();
                if (!same) FLASH_AT[i] = nowMs;
            }
        }
        current = slots;
        previous = copyOf(slots); // what the next frame compares against
    }

    private static NonNullList<ItemStack> copyOf(NonNullList<ItemStack> src) {
        NonNullList<ItemStack> out = emptySlots();
        for (int i = 0; i < src.size(); i++) out.set(i, src.get(i).copy());
        return out;
    }

    private static NonNullList<ItemStack> emptySlots() {
        return NonNullList.withSize(ContainerPanelDrawer.SLOTS, ItemStack.EMPTY);
    }

    private static void reset() {
        shown = null;
        appear = 0f;
        previous = null;
        lastNanos = 0L;
        java.util.Arrays.fill(FLASH_AT, 0L);
    }
}