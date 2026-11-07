package team.civitatum.hud.client;

/**
 * The frame around Xaero's minimap as drawn in the current frame, in GUI pixels. Xaero's HUD layer runs right above
 * the camera overlays, before the vanilla effects layer, so the effects of a frame see the minimap of the same frame.
 * Render thread only.
 */
public final class MinimapBounds {
    private static boolean drawn;
    private static int x;
    private static int y;
    private static int size;

    private MinimapBounds() {
    }

    static void set(int left, int top, int side) {
        drawn = true;
        x = left;
        y = top;
        size = side;
    }

    /** Called before the GUI layers of every frame: a minimap not drawn in this frame has no bounds. */
    static void clear() {
        drawn = false;
    }

    static boolean isDrawn() {
        return drawn;
    }

    static int x() {
        return x;
    }

    static int y() {
        return y;
    }

    static int size() {
        return size;
    }
}
