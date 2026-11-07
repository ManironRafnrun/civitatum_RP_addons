package team.civitatum.hud.client;

/**
 * Moves ParCool's stamina icons into this HUD's palette. ParCool draws each state as one hue from dark to nearly
 * white: yellow, red when exhausted, blue under the Inexhaustible effect. A pixel keeps its alpha and its lightness
 * ({@code (max + min) / 2} of its channels) and takes its colour from a ramp of this HUD's colours at that lightness,
 * so the shading of the icons stays and only the hue changes.
 */
final class EnergyRecolor {
    enum Ramp {
        /** The gold of {@link MorrowindSkin#frame}: shadow, gold and lit gold. */
        GOLD(0xFF120E0A, 0xFF54422C, 0xFFB29A6B, 0xFFE2C18A, 0xFFF6EEDB),
        /** The health bar's red. */
        RED(0xFF150A08, 0xFF5A2219, 0xFFAE4236, 0xFFD4806C, 0xFFF2DDD6),
        /** The mana bar's blue. */
        BLUE(0xFF0B0E16, 0xFF25365A, 0xFF496FAD, 0xFF86A3D2, 0xFFEEF2FA);

        /**
         * Lightness of the stops: ParCool's outline is 0x60 in one channel, its plain colour 0xFE in one channel
         * (lightness = channel / 2 / 255); its highlights lighten from there towards white.
         */
        private static final float[] STOPS = {0, 0x60 / 510F, 0xFE / 510F, 0.72F, 1};
        private final int[] colors;

        Ramp(int... colors) {
            this.colors = colors;
        }

        /** The ramp for a ParCool sprite, by its name ({@code hud/stamina_exhausted_full} and the like). */
        static Ramp of(String sprite) {
            if (sprite.contains("inexhaustible")) return BLUE;
            if (sprite.contains("exhausted")) return RED;
            return GOLD;
        }
    }

    private EnergyRecolor() {
    }

    /** @param argb a pixel of ParCool's icon; fully transparent pixels are returned as they are */
    static int recolor(int argb, Ramp ramp) {
        int alpha = argb >>> 24;
        if (alpha == 0) return argb;
        int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
        float lightness = (Math.max(r, Math.max(g, b)) + Math.min(r, Math.min(g, b))) / (2 * 255F);
        return alpha << 24 | at(ramp, lightness) & 0xFFFFFF;
    }

    private static int at(Ramp ramp, float lightness) {
        float[] stops = Ramp.STOPS;
        int i = 1;
        while (i < stops.length - 1 && lightness > stops[i]) i++;
        float t = Math.clamp((lightness - stops[i - 1]) / (stops[i] - stops[i - 1]), 0, 1);
        int from = ramp.colors[i - 1], to = ramp.colors[i];
        return channel(from, to, t, 16) | channel(from, to, t, 8) | channel(from, to, t, 0);
    }

    private static int channel(int from, int to, float t, int shift) {
        int a = from >> shift & 0xFF, b = to >> shift & 0xFF;
        return Math.round(a + (b - a) * t) << shift;
    }
}
