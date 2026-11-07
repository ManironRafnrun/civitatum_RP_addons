package team.civitatum.starclock.tools.art;

import static team.civitatum.starclock.tools.art.Palette.AMETHYST;
import static team.civitatum.starclock.tools.art.Palette.GOLD;
import static team.civitatum.starclock.tools.art.Palette.NIGHT;
import static team.civitatum.starclock.tools.art.Palette.PARCHMENT;
import static team.civitatum.starclock.tools.art.Palette.opaque;
import static team.civitatum.starclock.tools.art.Palette.withAlpha;

/** The original Starclock GUI art: star field, card faces, the spark particle and the small icons. */
public final class Ornaments {
    public static final int CARD_WIDTH = 256;
    public static final int CARD_HEIGHT = 384;
    /** Stars of the field; their places come from a formula, so the field is the same on every build. */
    private static final int STARS = 370;
    /** Every 13th star is a bright one with a cross. */
    private static final int BRIGHT_EVERY = 13;
    /** Where the serpent's figure sits on its card, and its size. */
    private static final int SERPENT_X = 16;
    private static final int SERPENT_Y = 72;
    private static final int SERPENT_SIZE = 224;

    public static Raster starfield() {
        var image = Raster.filled(ConstellationFigure.SIZE, ConstellationFigure.SIZE, opaque(NIGHT));
        var draw = new Draw(image);
        for (int index = 0; index < STARS; index++) {
            int x = (index * 173 + index * index * 31 + 19) % ConstellationFigure.SIZE;
            int y = (index * 257 + index * index * 7 + 43) % ConstellationFigure.SIZE;
            boolean bright = index % BRIGHT_EVERY == 0;
            draw.point(x, y, bright ? withAlpha(PARCHMENT, 155) : withAlpha(AMETHYST, 55 + index % 65));
            if (bright) {
                draw.line(new double[] {x - 2, y, x + 2, y}, withAlpha(GOLD, 100), 0);
                draw.line(new double[] {x, y - 2, x, y + 2}, withAlpha(GOLD, 100), 0);
            }
        }
        return image;
    }

    /** A card face, or with {@code back} its back: frame, corner marks and the rose of the back. */
    public static Raster card(boolean back) {
        var image = new Raster(CARD_WIDTH, CARD_HEIGHT, 4);
        var draw = new Draw(image);
        draw.roundedRectangle(1, 1, 254, 382, 13, opaque(PARCHMENT), opaque(GOLD), 3);
        draw.roundedRectangle(8, 8, 247, 375, 8, opaque(NIGHT), opaque(GOLD), 2);
        draw.rectangle(15, 15, 240, 368, null, opaque(AMETHYST), 1);
        int[][] corners = {{20, 20, 1, 1}, {235, 20, -1, 1}, {20, 363, 1, -1}, {235, 363, -1, -1}};
        for (int[] c : corners) {
            int x = c[0], y = c[1], dx = c[2], dy = c[3];
            draw.line(new double[] {x, y + 22 * dy, x, y, x + 22 * dx, y}, opaque(GOLD), 2);
            double[] diamond = {
                x + 3 * dx, y + 8 * dy, x + 8 * dx, y + 3 * dy, x + 13 * dx, y + 8 * dy, x + 8 * dx, y + 13 * dy};
            draw.polygon(diamond, null, opaque(GOLD), 1);
        }
        draw.line(new double[] {32, 63, 223, 63}, opaque(GOLD), 0);
        draw.line(new double[] {32, 302, 223, 302}, opaque(GOLD), 0);
        if (back) {
            for (int radius : new int[] {48, 76, 91})
                draw.ellipse(128 - radius, 192 - radius, 128 + radius, 192 + radius, null, opaque(GOLD), 1);
            for (int index = 0; index < 12; index++) {
                double angle = index * (2 * Math.PI) / 12;
                double x = 128 + StrictMath.cos(angle) * 83, y = 192 + StrictMath.sin(angle) * 83;
                draw.ellipse(x - 2, y - 2, x + 2, y + 2, opaque(PARCHMENT), null, 1);
            }
            draw.polygon(new double[] {128, 102, 159, 192, 128, 282, 97, 192}, null, opaque(AMETHYST), 3);
            draw.line(new double[] {49, 192, 207, 192}, opaque(GOLD), 0);
            draw.line(new double[] {128, 113, 128, 271}, opaque(GOLD), 0);
            draw.ellipse(117, 181, 139, 203, opaque(GOLD), null, 1);
            draw.ellipse(122, 186, 134, 198, opaque(NIGHT), null, 1);
        }
        return image;
    }

    /** The serpent has no upright or reversed reading, so its card is drawn once with the figure on it. */
    public static Raster serpentCard(Raster front, Raster serpent) {
        var card = front.copy();
        var sign = Resampling.resize(serpent, SERPENT_SIZE, SERPENT_SIZE, Resampling.Filter.LANCZOS);
        Compositing.alphaCompositeAt(card, sign, SERPENT_X, SERPENT_Y);
        return card;
    }

    public static Raster particle() {
        var image = new Raster(8, 8, 4);
        var draw = new Draw(image);
        draw.line(new double[] {3, 0, 3, 7}, withAlpha(GOLD, 100), 0);
        draw.line(new double[] {0, 3, 7, 3}, withAlpha(GOLD, 100), 0);
        draw.rectangle(2, 2, 4, 4, opaque(GOLD), null, 1);
        draw.point(3, 3, opaque(PARCHMENT));
        return image;
    }

    /** The 16×16 icons: "upright" and "reversed" arrows, "sky" star and "timer" clock. */
    public static Raster icon(String name) {
        var image = new Raster(16, 16, 4);
        var draw = new Draw(image);
        switch (name) {
            case "upright", "reversed" -> {
                draw.polygon(new double[] {8, 2, 13, 8, 10, 8, 10, 13, 6, 13, 6, 8, 3, 8}, opaque(GOLD), null, 1);
                if (name.equals("reversed")) image = image.rotate180();
            }
            case "sky" -> {
                double[] star = {8, 1, 10, 6, 15, 8, 10, 10, 8, 15, 6, 10, 1, 8, 6, 6};
                draw.polygon(star, opaque(GOLD), null, 1);
                draw.point(8, 8, opaque(PARCHMENT));
            }
            case "timer" -> {
                draw.ellipse(2, 2, 13, 13, null, opaque(GOLD), 2);
                draw.line(new double[] {8, 4, 8, 8, 11, 9}, opaque(PARCHMENT), 1);
            }
            default -> throw new IllegalArgumentException("unknown icon " + name);
        }
        return image;
    }

    private Ornaments() {}
}
