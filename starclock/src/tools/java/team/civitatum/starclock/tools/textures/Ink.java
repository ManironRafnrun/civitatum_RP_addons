package team.civitatum.starclock.tools.textures;

import team.civitatum.starclock.tools.art.Palette;

/** Opaque RGBA inks of the sprites, written as in generate_textures.py. */
final class Ink {
    static final int[] NIGHT = Palette.opaque(Palette.NIGHT);
    static final int[] GOLD = Palette.opaque(Palette.GOLD);
    static final int[] PARCHMENT = Palette.opaque(Palette.PARCHMENT);
    static final int[] AMETHYST = Palette.opaque(Palette.AMETHYST);
    static final int[] INK = hex("#2A2118");
    /** Pillow writes a transparent ink as is: it cuts a hole instead of blending. */
    static final int[] CLEAR = {0, 0, 0, 0};

    /** {@code ImageColor.getcolor("#RRGGBB", "RGBA")}: opaque. */
    static int[] hex(String color) {
        int rgb = Integer.parseInt(color.substring(1), 16);
        return new int[] {rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255};
    }

    private Ink() {}
}
