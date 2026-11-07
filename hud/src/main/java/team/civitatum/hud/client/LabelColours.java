package team.civitatum.hud.client;

/** Which text colours of a restyled screen are labels too dark for its stone and are drawn light instead. */
final class LabelColours {
    /**
     * The brightest channel of a label colour taken for dark: vanilla's {@code 0x404040}, the {@code 0x3F3F3F},
     * {@code 0x555555} or black that mods copy, and dark hues such as Create's {@code 0x592424}. All of them are lost
     * on stone. Greys count up to a lighter shade ({@code 0x7A7A7A} of Create's screens); a hue that light stays, as
     * the brown {@code 0x685E4A} of the enchanting table, written on its parchment.
     */
    private static final int DARKEST_LABEL = 0x60, DARKEST_GREY_LABEL = 0x7A, GREY_SPREAD = 0x10;

    /** Whether a colour, alpha aside, is one of those dark label colours. */
    static boolean dark(int color) {
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return max <= DARKEST_LABEL || max <= DARKEST_GREY_LABEL && max - min <= GREY_SPREAD;
    }

    private LabelColours() {}
}
