package team.civitatum.starclock.tools.textures;

import static team.civitatum.starclock.tools.textures.Ink.AMETHYST;
import static team.civitatum.starclock.tools.textures.Ink.GOLD;
import static team.civitatum.starclock.tools.textures.Ink.INK;
import static team.civitatum.starclock.tools.textures.Ink.NIGHT;
import static team.civitatum.starclock.tools.textures.Ink.PARCHMENT;
import static team.civitatum.starclock.tools.textures.Ink.hex;

import team.civitatum.starclock.tools.art.Draw;
import team.civitatum.starclock.tools.art.Raster;

/** 64×64 atlases of the GeckoLib tool models: four materials on top, ornament and reflection below. */
final class ModelAtlases {
    static final int SIZE = 64;
    /** Side of one material tile in the top row. */
    private static final int TILE = 16;
    /** One pixel in this many of a tile is a grain speck. */
    private static final int GRAIN_PERIOD = 41;

    static Raster atlas(String kind) {
        var image = Raster.filled(SIZE, SIZE, INK);
        var draw = new Draw(image);
        int[][][] bases = {
            {GOLD, hex("#B48C52"), hex("#FFF0BB")}, {NIGHT, hex("#172541"), AMETHYST},
            {PARCHMENT, hex("#C6B78D"), hex("#FFF3D5")}, {AMETHYST, hex("#583D78"), hex("#C7A7ED")}};
        for (int tile = 0; tile < bases.length; tile++) {
            int[] base = bases[tile][0], shade = bases[tile][1], light = bases[tile][2];
            int left = tile * TILE;
            draw.rectangle(left, 0, left + 15, 15, base, null, 1);
            for (int y = 0; y < TILE; y++)
                for (int x = 0; x < TILE; x++) {
                    int grain = (x * 17 + y * 11 + x * y * 3) % GRAIN_PERIOD;
                    if (grain == 0) draw.point(left + x, y, shade);
                    else if (grain == 1) draw.point(left + x, y, light);
                }
            draw.line(new double[] {left, 0, left + 15, 0}, light, 0);
            draw.line(new double[] {left, 15, left + 15, 15}, shade, 0);
        }
        draw.rectangle(0, 16, 23, 55, NIGHT, GOLD, 1);
        draw.rectangle(2, 18, 21, 53, null, hex("#80623D"), 1);
        draw.rectangle(32, 16, 55, 55, PARCHMENT, GOLD, 1);
        draw.rectangle(34, 18, 53, 53, null, INK, 1);
        int[][] stars = {{4, 21}, {18, 24}, {6, 45}, {17, 49}, {4, 34}, {19, 37}};
        for (int[] p : stars) draw.point(p[0], p[1], PARCHMENT);
        draw.ellipse(5, 27, 18, 44, null, AMETHYST, 1);
        draw.line(new double[] {11, 24, 11, 47}, GOLD, 0);
        draw.line(new double[] {4, 35, 19, 35}, GOLD, 0);
        draw.polygon(new double[] {11, 29, 15, 35, 11, 42, 7, 35}, null, PARCHMENT, 1);
        constellation(draw, new int[][] {{39, 40}, {42, 28}, {47, 37}, {50, 25}}, INK, AMETHYST);
        switch (kind) {
            case "sextant" -> {
                for (int x = 1; x < 16; x += 3) draw.line(new double[] {x, 5, x, x % 2 != 0 ? 10 : 8}, INK, 0);
                draw.ellipse(50, 2, 61, 13, hex("#65549B"), hex("#D2C4F0"), 1);
                draw.line(new double[] {52, 4, 56, 4}, PARCHMENT, 0);
                draw.point(53, 5, PARCHMENT);
            }
            case "bowl" -> {
                draw.rectangle(0, 16, 23, 55, NIGHT, GOLD, 1);
                for (int radius : new int[] {4, 8, 11})
                    draw.ellipse(11 - radius, 35 - radius * 2, 11 + radius, 35 + radius * 2, null, hex("#30466C"), 1);
                constellation(draw, new int[][] {{4, 42}, {9, 30}, {14, 39}, {19, 26}}, AMETHYST, PARCHMENT);
            }
            case "ring" -> {
                for (int x = 2; x < 15; x += 4) {
                    draw.line(new double[] {x, 3, x, 12}, INK, 0);
                    draw.point(x + 1, 5, PARCHMENT);
                }
                draw.polygon(new double[] {56, 1, 63, 7, 56, 14, 49, 7}, hex("#70549D"), hex("#D6BEF0"), 1);
                draw.line(new double[] {51, 6, 56, 3, 60, 7}, PARCHMENT, 0);
            }
            default -> {}
        }
        for (int y = 57; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++) draw.point(x, y, (x + y * 3) % 13 == 0 ? GOLD : NIGHT);
        return image;
    }

    /** A path through the stars, then a 3×3 square on each star. */
    private static void constellation(Draw draw, int[][] stars, int[] line, int[] star) {
        double[] path = new double[stars.length * 2];
        for (int i = 0; i < stars.length; i++) {
            path[i * 2] = stars[i][0];
            path[i * 2 + 1] = stars[i][1];
        }
        draw.line(path, line, 0);
        for (int[] p : stars) draw.rectangle(p[0] - 1, p[1] - 1, p[0] + 1, p[1] + 1, star, null, 1);
    }

    private ModelAtlases() {}
}
