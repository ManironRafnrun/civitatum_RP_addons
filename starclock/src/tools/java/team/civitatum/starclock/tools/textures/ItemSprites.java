package team.civitatum.starclock.tools.textures;

import static team.civitatum.starclock.tools.textures.Ink.AMETHYST;
import static team.civitatum.starclock.tools.textures.Ink.CLEAR;
import static team.civitatum.starclock.tools.textures.Ink.GOLD;
import static team.civitatum.starclock.tools.textures.Ink.INK;
import static team.civitatum.starclock.tools.textures.Ink.NIGHT;
import static team.civitatum.starclock.tools.textures.Ink.PARCHMENT;
import static team.civitatum.starclock.tools.textures.Ink.hex;

import team.civitatum.starclock.tools.art.Draw;
import team.civitatum.starclock.tools.art.Raster;

/** The 16×16 inventory sprites: four tools in three tiers and the tower key. */
final class ItemSprites {
    static final int SIZE = 16;
    static final String[] TOOLS = {"deck", "sextant", "bowl", "ring"};

    /** Edge, light and shade of a tier: bronze, silver, gold. */
    private record Material(int[] edge, int[] light, int[] shade) {}

    private static Material material(int tier) {
        return switch (tier) {
            case 1 -> new Material(hex("#A16D46"), hex("#D79B63"), hex("#614332"));
            case 2 -> new Material(hex("#A7B4C5"), hex("#E4E8EE"), hex("#586479"));
            case 3 -> new Material(GOLD, hex("#FFF0BB"), hex("#80623D"));
            default -> throw new IllegalArgumentException("tier " + tier);
        };
    }

    static Raster tool(String kind, int tier) {
        var image = new Raster(SIZE, SIZE, 4);
        var draw = new Draw(image);
        var m = material(tier);
        switch (kind) {
            case "deck" -> deck(draw, m, tier);
            case "sextant" -> sextant(draw, m, tier);
            case "bowl" -> bowl(draw, m, tier);
            case "ring" -> ring(draw, m, tier);
            default -> throw new IllegalArgumentException("unknown tool " + kind);
        }
        return image;
    }

    static Raster towerKey() {
        var image = new Raster(SIZE, SIZE, 4);
        var draw = new Draw(image);
        draw.ellipse(2, 1, 9, 8, GOLD, INK, 1);
        draw.ellipse(4, 3, 7, 6, CLEAR, INK, 1);
        draw.rectangle(5, 7, 7, 14, INK, null, 1);
        draw.line(new double[] {6, 7, 6, 13}, GOLD, 0);
        draw.rectangle(7, 10, 10, 11, INK, null, 1);
        draw.line(new double[] {7, 10, 9, 10}, GOLD, 0);
        draw.point(3, 3, PARCHMENT);
        draw.point(6, 2, AMETHYST);
        return image;
    }

    private static void deck(Draw draw, Material m, int tier) {
        draw.rectangle(5, 2, 13, 12, m.shade(), INK, 1);
        draw.rectangle(3, 3, 11, 13, PARCHMENT, INK, 1);
        draw.rectangle(2, 4, 10, 14, m.edge(), INK, 1);
        draw.rectangle(3, 5, 9, 13, NIGHT, null, 1);
        draw.line(new double[] {4, 6, 8, 6}, m.light(), 0);
        draw.line(new double[] {4, 12, 8, 12}, m.light(), 0);
        draw.point(6, 8, PARCHMENT);
        draw.line(new double[] {5, 9, 7, 9}, AMETHYST, 0);
        draw.point(6, 10, GOLD);
        if (tier >= 2) {
            draw.point(4, 7, GOLD);
            draw.point(8, 11, GOLD);
        }
        if (tier == 3) {
            draw.line(new double[] {11, 3, 12, 3}, AMETHYST, 0);
            draw.point(12, 4, PARCHMENT);
        }
    }

    private static void sextant(Draw draw, Material m, int tier) {
        draw.line(new double[] {3, 3, 3, 12, 12, 12}, INK, 3);
        draw.line(new double[] {3, 3, 3, 12, 12, 12}, m.edge(), 1);
        draw.line(new double[] {4, 2, 8, 3, 11, 6, 13, 10, 13, 12}, m.shade(), 2);
        draw.line(new double[] {4, 2, 8, 3, 11, 6, 13, 10}, m.light(), 0);
        draw.line(new double[] {3, 12, 11, 4}, INK, 3);
        draw.line(new double[] {3, 12, 11, 4}, m.edge(), 0);
        draw.rectangle(8, 3, 12, 5, m.shade(), INK, 1);
        draw.point(12, 4, AMETHYST);
        draw.point(4, 11, m.light());
        draw.point(6, 4, INK);
        draw.point(10, 8, INK);
        if (tier == 3) {
            draw.point(9, 10, AMETHYST);
            draw.point(10, 10, PARCHMENT);
        }
    }

    private static void bowl(Draw draw, Material m, int tier) {
        draw.polygon(new double[] {1, 6, 14, 6, 12, 11, 10, 13, 5, 13, 3, 11}, INK, null, 1);
        draw.polygon(new double[] {2, 7, 13, 7, 11, 11, 9, 12, 6, 12, 4, 10}, m.edge(), null, 1);
        draw.line(new double[] {5, 12, 10, 12}, m.shade(), 0);
        draw.ellipse(1, 3, 14, 8, m.light(), INK, 1);
        draw.ellipse(3, 4, 12, 7, NIGHT, null, 1);
        draw.line(new double[] {4, 5, 8, 5}, hex("#456184"), 0);
        draw.point(10, 5, PARCHMENT);
        draw.point(8, 6, AMETHYST);
        if (tier >= 2) {
            draw.rectangle(6, 9, 9, 10, m.shade(), null, 1);
            draw.point(7, 9, AMETHYST);
        }
        if (tier == 3) draw.point(5, 5, GOLD);
    }

    private static void ring(Draw draw, Material m, int tier) {
        draw.ellipse(2, 2, 13, 13, INK, null, 1);
        draw.ellipse(3, 3, 12, 12, m.edge(), null, 1);
        draw.ellipse(5, 5, 10, 10, INK, null, 1);
        draw.ellipse(6, 6, 9, 9, CLEAR, null, 1);
        draw.line(new double[] {4, 4, 7, 3}, m.light(), 0);
        draw.line(new double[] {10, 12, 12, 10}, m.shade(), 0);
        int[][] studs = {{7, 3}, {10, 4}, {12, 7}, {11, 10}, {8, 12}, {5, 11}, {3, 8}};
        for (int[] p : studs) draw.point(p[0], p[1], INK);
        if (tier >= 2) {
            draw.rectangle(9, 2, 11, 4, AMETHYST, INK, 1);
            draw.point(10, 2, PARCHMENT);
        }
        if (tier == 3) {
            draw.point(3, 5, PARCHMENT);
            draw.point(11, 11, PARCHMENT);
        }
    }

    private ItemSprites() {}
}
