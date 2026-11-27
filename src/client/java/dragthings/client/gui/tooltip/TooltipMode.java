package dragthings.client.gui.tooltip;

/** Where the hovered-item tooltip is drawn. */
public enum TooltipMode {
    /** 2D, centered under the crosshair (the original look). */
    CENTER,
    /** 2D, beside the item: follows the item's position on screen, flips side near the edge. */
    SIDE,
    /** 3D panel floating next to the item in the world, turned toward the camera, shrinks with distance. */
    BILLBOARD
}