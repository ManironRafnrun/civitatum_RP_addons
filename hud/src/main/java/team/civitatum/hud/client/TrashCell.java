package team.civitatum.hud.client;

/**
 * Where our inventory shows TrashSlot's cell ({@link TrashSlotBridge}), for TrashSlot to take the clicks on it and JEI
 * to keep clear of it. Empty while the cell is not shown, so no click lands on it.
 */
public final class TrashCell {
    private static int cellX, cellY, cellSize;

    private TrashCell() {}

    static void show(int x, int y, int size) {
        cellX = x;
        cellY = y;
        cellSize = size;
    }

    static void hide() {
        cellSize = 0;
    }

    /** Whether a point is on the cell: TrashSlot takes the click there for its own, and nothing under it does. */
    public static boolean inside(int x, int y) {
        return x >= cellX && y >= cellY && x < cellX + cellSize && y < cellY + cellSize;
    }

    /** The cell as x, y, width, height. */
    public static int[] rectangle() {
        return new int[]{cellX, cellY, cellSize, cellSize};
    }
}
