package team.civitatum.hud.client;

/**
 * Where the FTB Library sidebar stands: one row in the lower right corner, at the place of the former sliders. It
 * belongs to the HUD's layout, so it is fixed in code, whatever FTB Library's own settings say.
 */
public final class SidebarRow {
    /** Free space to the right of the row; the inventory's "Reset" button uses it too, so their edges line up. */
    public static final int RIGHT_MARGIN = 6;
    private static final int BOTTOM_MARGIN = 6;
    /** FTB Library draws a 16 unit icon in a 17 unit cell, the last column's spare unit stays outside the margin. */
    public static final int CELL = 17;
    private static final int ICON = 16;

    private SidebarRow() {
    }

    public static int left(int screenWidth, int columns) {
        return screenWidth - RIGHT_MARGIN - width(columns);
    }

    public static int top(int screenHeight) {
        return screenHeight - BOTTOM_MARGIN - ICON;
    }

    public static int width(int columns) {
        return columns * CELL - (CELL - ICON);
    }
}
