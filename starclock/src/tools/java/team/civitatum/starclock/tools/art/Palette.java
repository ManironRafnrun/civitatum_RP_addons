package team.civitatum.starclock.tools.art;

/** Colours of the Starclock art, shared with the hand-drawn sprites of generate_textures.py. */
public final class Palette {
    public static final int[] NIGHT = {11, 16, 32};
    public static final int[] GOLD = {227, 192, 123};
    public static final int[] PARCHMENT = {232, 220, 192};
    public static final int[] AMETHYST = {142, 111, 179};

    /** The colour as an RGBA ink; Pillow makes an RGB tuple opaque. */
    public static int[] opaque(int[] rgb) {
        return withAlpha(rgb, 255);
    }

    public static int[] withAlpha(int[] rgb, int alpha) {
        return new int[] {rgb[0], rgb[1], rgb[2], alpha};
    }

    private Palette() {}
}
