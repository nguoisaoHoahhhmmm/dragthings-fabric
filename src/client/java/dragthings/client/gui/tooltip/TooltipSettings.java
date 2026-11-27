package dragthings.client.gui.tooltip;

import dragthings.client.DragThingsConfig;

/** Tooltip visual settings, read live from the Cloth config (tooltip section). */
public final class TooltipSettings {
    private TooltipSettings() {}

    public static TooltipMode mode() { return DragThingsConfig.get().tooltip.mode; }

    /** Gap in GUI pixels between the item and the panel (SIDE). */
    public static int sideGap() { return DragThingsConfig.get().tooltip.sideGap; }

    /** Distance in blocks from the item to the panel (BILLBOARD). */
    public static float billboardGap() { return DragThingsConfig.get().tooltip.billboardDistance / 100f; }

    /** World size of one GUI pixel at 2.5 blocks away (BILLBOARD). */
    public static float billboardPixelSize() { return 0.011f * DragThingsConfig.get().tooltip.billboardSize / 100f; }
}