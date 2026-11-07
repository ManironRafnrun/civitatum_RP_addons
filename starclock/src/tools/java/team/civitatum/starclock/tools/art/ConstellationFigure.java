package team.civitatum.starclock.tools.art;

import java.util.ArrayDeque;

/**
 * One constellation of the supplied originals, redrawn in the mod's palette. The blue lines of the original are kept
 * as they are, the background and stray specks are dropped, and the figure is centred on a 512×512 canvas.
 */
public final class ConstellationFigure {
    public static final int SIZE = 512;
    /** The figure fits this box, leaving a margin for its glow. */
    private static final int FIGURE_SIZE = 432;
    /** Coarse grid on which nearby strokes merge into one component; about 8 source pixels per cell. */
    private static final int GRID_WIDTH = 222;
    private static final int GRID_HEIGHT = 111;
    /** A grid cell belongs to a stroke when its average line strength is above this. */
    private static final int CELL_THRESHOLD = 14;
    /** Strokes this close (in cells) join into one component. */
    private static final int MERGE_SIZE = 5;
    /** Components smaller than this many cells are specks, not part of the figure. */
    private static final int MIN_COMPONENT = 100;
    private static final float GLOW_RADIUS = 3;

    /** The redrawn figure and the crop of the original it came from: x0, y0, x1, y1. */
    public record Result(Raster image, int[] bounds) {}

    public static Result of(Raster source) {
        var mask = lineStrength(source);
        var grid = Filters.maxFilter(Resampling.resize(mask, GRID_WIDTH, GRID_HEIGHT, Resampling.Filter.BOX)
            .point(v -> v > CELL_THRESHOLD ? 255 : 0), MERGE_SIZE);
        var regions = new Raster(GRID_WIDTH, GRID_HEIGHT, 1);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        var visited = new boolean[GRID_WIDTH * GRID_HEIGHT];
        for (int y = 0; y < GRID_HEIGHT; y++)
            for (int x = 0; x < GRID_WIDTH; x++) {
                if (grid.get(x, y, 0) == 0 || visited[y * GRID_WIDTH + x]) continue;
                var component = component(grid, visited, x, y);
                if (component.length < MIN_COMPONENT) continue;
                for (int cell : component) {
                    int cx = cell % GRID_WIDTH, cy = cell / GRID_WIDTH;
                    regions.set(cx, cy, 0, 255);
                    minX = Math.min(minX, cx);
                    minY = Math.min(minY, cy);
                    maxX = Math.max(maxX, cx);
                    maxY = Math.max(maxY, cy);
                }
            }
        if (maxX < 0) throw new IllegalStateException("В исходнике не найдена фигура");
        double sx = (double) source.width / GRID_WIDTH, sy = (double) source.height / GRID_HEIGHT;
        int[] bounds = {
            Math.max(0, (int) Math.floor(minX * sx)),
            Math.max(0, (int) Math.floor(minY * sy)),
            Math.min(source.width, (int) Math.ceil((maxX + 1) * sx)),
            Math.min(source.height, (int) Math.ceil((maxY + 1) * sy))};
        var kept = Compositing.multiply(mask, Resampling.nearest(regions, source.width, source.height))
            .crop(bounds[0], bounds[1], bounds[2], bounds[3]);
        var lines = Resampling.thumbnail(kept, FIGURE_SIZE);
        var alpha = new Raster(SIZE, SIZE, 1);
        alpha.paste(lines, (SIZE - lines.width) / 2, (SIZE - lines.height) / 2);
        var glow = Filters.gaussianBlur(alpha, GLOW_RADIUS).point(v -> v / 2);
        var result = Raster.filled(SIZE, SIZE, Palette.withAlpha(Palette.AMETHYST, 0));
        result.putAlpha(glow);
        var gold = Raster.filled(SIZE, SIZE, Palette.withAlpha(Palette.GOLD, 0));
        gold.putAlpha(alpha);
        return new Result(Compositing.alphaComposite(result, gold), bounds);
    }

    /**
     * How cyan each pixel is: green and blue above red, the blue excess weighted 0.8. Faint tints below 4 are paper,
     * the rest is stretched five times. Rounded half to even, as Python's {@code round}.
     */
    static Raster lineStrength(Raster source) {
        var mask = new Raster(source.width, source.height, 1);
        for (int y = 0; y < source.height; y++)
            for (int x = 0; x < source.width; x++) {
                int red = source.get(x, y, 0), green = source.get(x, y, 1), blue = source.get(x, y, 2);
                double cyan = Math.min(green - red, (blue - red) * 0.8);
                mask.set(x, y, 0, (int) Math.rint(Math.max(0, Math.min(255, (cyan - 4) * 5))));
            }
        return mask;
    }

    /** The 4-connected component of set cells around (x, y), as cell indices. */
    private static int[] component(Raster grid, boolean[] visited, int x, int y) {
        var todo = new ArrayDeque<Integer>();
        var cells = new java.util.ArrayList<Integer>();
        visited[y * GRID_WIDTH + x] = true;
        todo.push(y * GRID_WIDTH + x);
        while (!todo.isEmpty()) {
            int cell = todo.pop();
            cells.add(cell);
            int px = cell % GRID_WIDTH, py = cell / GRID_WIDTH;
            int[][] near = {{px - 1, py}, {px + 1, py}, {px, py - 1}, {px, py + 1}};
            for (int[] n : near) {
                if (n[0] < 0 || n[0] >= GRID_WIDTH || n[1] < 0 || n[1] >= GRID_HEIGHT) continue;
                int index = n[1] * GRID_WIDTH + n[0];
                if (grid.get(n[0], n[1], 0) != 0 && !visited[index]) {
                    visited[index] = true;
                    todo.push(index);
                }
            }
        }
        return cells.stream().mapToInt(Integer::intValue).toArray();
    }

    private ConstellationFigure() {}
}
