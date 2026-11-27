package dragthings.client.gui.tooltip;

import net.minecraft.util.Mth;

/**
 * Time state of the tooltip animation. Knows nothing about what is shown or
 * how it is drawn; it only answers "how far along is everything".
 *
 * One progress value goes 0 -> 1 when opening and 1 -> 0 when closing. The
 * first part of it draws the two lines out from the center ({@link #lineT});
 * the rest opens them apart to reveal the text ({@link #openT}). Because
 * closing is the same progress in reverse, the tooltip first folds shut and
 * only then do the lines retract.
 *
 * The box size is smoothed here too, so the frame glides when the content
 * changes (Shift pressed, looking at another item).
 */
public final class TooltipAnimator {

    // ── Tuning (visual only) ──────────────────────────────────────────────
    private static final float OPEN_SECONDS = 0.38f;
    private static final float CLOSE_SECONDS = 0.26f;
    /** Share of the progress spent drawing the lines before the tooltip opens. */
    private static final float LINE_PORTION = 0.35f;
    /** How quickly the box size follows its target (1/s). */
    private static final float SIZE_RATE = 14f;

    private float progress;
    private float width;
    private float height;
    private boolean sized;
    private float lastDt;
    private long lastNanos;

    /** Advances time by one frame. Call once per frame. */
    public void tick(boolean open) {
        long now = System.nanoTime();
        lastDt = lastNanos == 0L ? 0f : Math.min((now - lastNanos) / 1_000_000_000f, 0.05f);
        lastNanos = now;

        progress = Mth.clamp(progress + (open ? lastDt / OPEN_SECONDS : -lastDt / CLOSE_SECONDS), 0f, 1f);
        if (progress <= 0f) sized = false; // next opening starts at the right size
    }

    /** Sets the size the box should have; the shown size eases toward it. */
    public void setTargetSize(float targetWidth, float targetHeight) {
        if (!sized) {
            width = targetWidth;
            height = targetHeight;
            sized = true;
            return;
        }
        float k = 1f - (float) Math.exp(-SIZE_RATE * lastDt);
        width += (targetWidth - width) * k;
        height += (targetHeight - height) * k;
    }

    /** Back to fully closed, with no animation. */
    public void reset() {
        progress = 0f;
        sized = false;
        lastNanos = 0L;
    }

    public boolean isVisible() { return progress > 0.001f; }

    public boolean isFullyOpen() { return progress >= 0.999f; }

    /** 0..1: how far the two lines have drawn out from the center. */
    public float lineT() {
        float t = Mth.clamp(progress / LINE_PORTION, 0f, 1f);
        float u = 1f - t;
        return 1f - u * u * u; // ease-out cubic
    }

    /** 0..1: how far the lines have moved apart to reveal the tooltip. */
    public float openT() {
        float t = Mth.clamp((progress - LINE_PORTION) / (1f - LINE_PORTION), 0f, 1f);
        return t * t * (3f - 2f * t); // smoothstep
    }

    public float width() { return width; }

    public float height() { return height; }
}