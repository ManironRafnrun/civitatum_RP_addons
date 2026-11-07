package team.civitatum.hud.client;

public final class InventoryPanel {
    public static final int LEFT = 1, RIGHT = 2, TOP = 4, BOTTOM = 8;
    /** Height of the title bar a window is dragged by. */
    private static final int TITLE_HEIGHT = 18;
    /** Width of the border that resizes a window. */
    private static final int EDGE = 4;
    /** Size of the resize grip in the lower right corner. */
    private static final int GRIP = 10;
    /** Free space between a window and the screen edge. */
    public static final int MARGIN = 4;
    /** Screen space kept free at the top for the button row. */
    public static final int TOP_BAR = 25;
    public final String id;
    public final int minWidth, minHeight;
    private final int defaultX, defaultY, defaultWidth, defaultHeight;
    public int x, y, width, height;
    public boolean visible = true;

    public InventoryPanel(String id, int x, int y, int width, int height, int minWidth, int minHeight) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        defaultX = x;
        defaultY = y;
        defaultWidth = width;
        defaultHeight = height;
    }

    public boolean contains(double mx, double my) {
        return visible && mx >= x && my >= y && mx < x + width && my < y + height;
    }

    public boolean titleContains(double mx, double my) {
        return contains(mx, my) && my < y + TITLE_HEIGHT;
    }

    public boolean gripContains(double mx, double my) {
        return contains(mx, my) && mx >= x + width - GRIP && my >= y + height - GRIP;
    }

    public int resizeEdges(double mx, double my) {
        if (!contains(mx, my)) return 0;
        int edge = 0;
        if (mx < x + EDGE) edge |= LEFT;
        if (mx >= x + width - EDGE) edge |= RIGHT;
        if (my < y + EDGE) edge |= TOP;
        if (my >= y + height - EDGE) edge |= BOTTOM;
        if (gripContains(mx, my)) edge |= RIGHT | BOTTOM;
        return edge;
    }

    public void constrain(int screenWidth, int screenHeight) {
        width = Math.min(screenWidth - 2 * MARGIN, Math.max(minWidth, width));
        height = Math.min(screenHeight - TOP_BAR - MARGIN, Math.max(minHeight, height));
        x = Math.max(MARGIN, Math.min(screenWidth - width - MARGIN, x));
        y = Math.max(TOP_BAR, Math.min(screenHeight - height - MARGIN, y));
    }

    public void restore(InventoryPreferences.SavedPanel saved, int sw, int sh) {
        if (saved == null || !Float.isFinite(saved.x()) || !Float.isFinite(saved.y())) return;
        x = Math.round(saved.x() * sw);
        y = Math.round(saved.y() * sh);
        width = saved.width();
        height = saved.height();
        constrain(sw, sh);
    }

    public InventoryPreferences.SavedPanel save(int sw, int sh) {
        return new InventoryPreferences.SavedPanel((float) x / sw, (float) y / sh, width, height);
    }

    public void reset(int sw, int sh) {
        x = defaultX;
        y = defaultY;
        width = defaultWidth;
        height = defaultHeight;
        constrain(sw, sh);
    }
}
