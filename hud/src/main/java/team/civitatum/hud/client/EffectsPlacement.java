package team.civitatum.hud.client;

/**
 * Where the status effect icons go, in GUI pixels. The icons are vanilla's at 75%: the 24 px frame becomes 18 px,
 * the 1 px space between icons stays, and the 2 px between vanilla's two rows (26 - 24) stays.
 * Rows hold up to 7 icons and grow away from the minimap: to the left of it, or to the right when the left edge of the
 * screen would cut them (a minimap moved to the left side).
 */
final class EffectsPlacement {
    /** Vanilla's icon size scaled to 75%. */
    static final float SCALE = 0.75F;
    /** Vanilla's 24 px icon frame at 75%. */
    static final int SIZE = 18;
    /** {@link #SIZE} and vanilla's 1 px between icons. */
    static final int STEP = 19;
    /** {@link #SIZE} and vanilla's 2 px between rows. */
    static final int ROW_STEP = 20;
    static final int PER_ROW = 7;
    /** Space between the effects and the minimap frame. */
    static final int GAP = 2;
    /** Without a minimap the block keeps vanilla's corner: 1 px from the right and top edges of the screen. */
    static final int SCREEN_MARGIN = 1;
    /** Vanilla moves the effects down by 15 px in the demo, under the demo timer. */
    static final int DEMO_SHIFT = 15;

    private EffectsPlacement() {
    }

    /** Rows taken by {@code count} effects of one kind. */
    static int rows(int count) {
        return (count + PER_ROW - 1) / PER_ROW;
    }

    /** Width of the widest row when the longer kind has {@code count} effects. */
    static int blockWidth(int count) {
        int columns = Math.min(count, PER_ROW);
        return columns == 0 ? 0 : columns * STEP - (STEP - SIZE);
    }

    /** @param scale the effects size setting, applied to the whole block */
    static boolean fitsLeftOf(int mapX, int widestCount, float scale) {
        return mapX - GAP - blockWidth(widestCount) * scale >= 0;
    }

    /**
     * @param edge the right edge of the block for a leftward block, its left edge otherwise
     * @param index position of the effect within its kind, in display order
     */
    static int x(boolean leftward, int edge, int index) {
        int column = index % PER_ROW;
        return leftward ? edge - SIZE - column * STEP : edge + column * STEP;
    }

    /** @param firstRow the row the kind starts on: harmful effects start below the beneficial ones */
    static int y(int top, int firstRow, int index) {
        return top + (firstRow + index / PER_ROW) * ROW_STEP;
    }
}
