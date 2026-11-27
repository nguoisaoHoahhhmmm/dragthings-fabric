package dragthings.client.gui.hud;

import dragthings.client.ItemEnchantRitual;
import dragthings.client.gui.tooltip.TooltipAnimator;
import dragthings.client.gui.tooltip.TooltipFrameRenderer;
import dragthings.ritual.EnchantRitual;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Enchant ritual HUD. Uses the same animated dividers as the item tooltip:
 * two lines draw out above the crosshair and open to show
 *
 *     [x] Item    [x] Lapis    [x] 3 Lv
 *     status text
 *
 * with a progress bar under the frame while the orbs are flying, and an
 * "Enchanted!" flash when it finishes.
 */
public final class EnchantRitualHud {

    private static final int GREEN = 0x55FF55;
    private static final int RED   = 0xFF5555;
    private static final int GOLD  = 0xFFD455;
    private static final int GRAY  = 0xAAAAAA;
    private static final int BAR_W = 90;
    private static final int BAR_H = 3;
    private static final int OFFSET_ABOVE_CENTER = 46;

    private static final TooltipAnimator ANIM = new TooltipAnimator();

    private EnchantRitualHud() {}

    public static void init() {
        HudRenderCallback.EVENT.register(EnchantRitualHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) { ANIM.reset(); return; }

        boolean open = ItemEnchantRitual.isActive();
        ANIM.tick(open);
        if (!ANIM.isVisible()) return;

        int flags = ItemEnchantRitual.flags();
        int cost = ItemEnchantRitual.levelCost();
        boolean doneFlash = ItemEnchantRitual.doneTicks() > 0;

        Component chips = Component.empty()
                .append(chip("Item", (flags & EnchantRitual.FLAG_TARGET) != 0))
                .append(Component.literal("   "))
                .append(chip("Lapis", (flags & EnchantRitual.FLAG_LAPIS) != 0))
                .append(Component.literal("   "))
                .append(chip(cost + " Lv", (flags & EnchantRitual.FLAG_XP) != 0));

        Component status;
        if (doneFlash) {
            status = Component.literal("Enchanted!").withColor(GOLD);
        } else if (ItemEnchantRitual.isChanneling()) {
            status = Component.literal("Enchanting...").withColor(GOLD);
        } else if ((flags & EnchantRitual.FLAGS_READY) == EnchantRitual.FLAGS_READY) {
            status = Component.literal("Ready").withColor(GREEN);
        } else if ((flags & EnchantRitual.FLAG_XP) == 0) {
            status = Component.literal("Not enough levels").withColor(RED);
        } else {
            status = Component.literal("Drop an item and lapis nearby").withColor(GRAY);
        }

        int top = mc.getWindow().getGuiScaledHeight() / 2 - OFFSET_ABOVE_CENTER;
        int below = TooltipFrameRenderer.render(g, mc, ANIM, List.of(chips, status), top);

        // Progress bar under the frame, only once the frame is open
        if (ANIM.openT() > 0.95f && (ItemEnchantRitual.isChanneling() || doneFlash)) {
            float pt = delta.getGameTimeDeltaPartialTick(false);
            float p = doneFlash ? 1f
                    : Mth.clamp((ItemEnchantRitual.channelTicks() + pt) / EnchantRitual.CHANNEL_TICKS, 0f, 1f);
            int cx = mc.getWindow().getGuiScaledWidth() / 2;
            int x1 = cx - BAR_W / 2;
            int y = below + 4;
            int fill = doneFlash ? 0xFFFFD455 : 0xFF7CFC3C;
            g.fill(x1, y, x1 + BAR_W, y + BAR_H, 0x80000000);
            g.fill(x1, y, x1 + Math.round(BAR_W * p), y + BAR_H, fill);
        }
    }

    private static Component chip(String label, boolean ok) {
        return Component.literal((ok ? "✔ " : "✘ ") + label).withColor(ok ? GREEN : RED);
    }
}