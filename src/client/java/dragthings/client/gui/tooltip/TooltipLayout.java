package dragthings.client.gui.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Arranges the body lines so they never run off the screen: when they do not
 * fit in the given height they are split into columns, filled top to bottom
 * then left to right, with the rows balanced between the columns.
 */
public final class TooltipLayout {

    public static final int PADDING_X = 12;
    public static final int PADDING_Y = 6;
    private static final int COLUMN_GAP = 18;

    public final List<List<Component>> columns;
    /** X of each column's left edge, from the panel's left edge. */
    public final int[] columnX;
    public final int lineHeight;
    public final int width;
    public final int height;

    private TooltipLayout(List<List<Component>> columns, int[] columnX, int lineHeight, int width, int height) {
        this.columns = columns;
        this.columnX = columnX;
        this.lineHeight = lineHeight;
        this.width = width;
        this.height = height;
    }

    public boolean isSingleColumn() { return columns.size() <= 1; }

    public static TooltipLayout of(Font font, List<Component> body, int maxHeight) {
        int lineHeight = font.lineHeight + 2;
        int maxRows = Math.max(1, (maxHeight - PADDING_Y * 2 + 2) / lineHeight);
        int n = body.size();
        int cols = Math.max(1, (int) Math.ceil(n / (double) maxRows));
        int rows = Math.max(1, (int) Math.ceil(n / (double) cols));

        List<List<Component>> columns = new ArrayList<>();
        for (int c = 0; c < cols; c++) {
            int from = c * rows;
            if (from >= n) break;
            columns.add(new ArrayList<>(body.subList(from, Math.min(n, from + rows))));
        }

        int[] columnX = new int[columns.size()];
        int x = PADDING_X;
        for (int c = 0; c < columns.size(); c++) {
            int colW = 0;
            for (Component line : columns.get(c)) colW = Math.max(colW, font.width(line));
            columnX[c] = x;
            x += colW + (c < columns.size() - 1 ? COLUMN_GAP : 0);
        }
        int width = x + PADDING_X;
        int height = Math.min(n, rows) * lineHeight - 2 + PADDING_Y * 2;
        return new TooltipLayout(columns, columnX, lineHeight, width, height);
    }
}