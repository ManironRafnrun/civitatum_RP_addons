package team.civitatum.hud.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Where a scrolled grid stands: a track beside it and a thumb as tall as the visible part. Without it an empty or
 * evenly filled grid looks the same before and after a wheel turn.
 *
 * @param x       left edge of the track
 * @param top     top of the grid
 * @param height  height of the visible rows
 * @param first   first visible row
 * @param visible rows on screen
 * @param total   rows in all
 */
record RowScrollBar(int x, int top, int height, int first, int visible, int total) {
    static final int WIDTH = 2;
    /** The thumb stays visible however many rows there are. */
    private static final int MIN_THUMB = 4;
    private static final int TRACK = 0x60130E09, THUMB = 0xFFD3B478;

    /** Drawn only when there is something to scroll. */
    void render(GuiGraphics g) {
        if (!scrollable()) return;
        int thumb = thumb(), offset = offset();
        GuiBatch.fills(g, () -> {
            g.fill(x, top, x + WIDTH, top + height, TRACK);
            g.fill(x, top + offset, x + WIDTH, top + offset + thumb, THUMB);
        });
    }

    boolean scrollable() {
        return total > visible && height > 0;
    }

    int thumb() {
        return Math.min(height, Math.max(MIN_THUMB, height * visible / total));
    }

    /** From 0 on the first page to the bottom of the track on the last one. */
    int offset() {
        return (height - thumb()) * first / (total - visible);
    }
}
