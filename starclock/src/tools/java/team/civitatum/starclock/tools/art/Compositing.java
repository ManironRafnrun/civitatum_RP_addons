package team.civitatum.starclock.tools.art;

/** Pillow 11.3.0 band arithmetic: AlphaComposite.c, Chops.c and the RGBA/RGBa conversions of Convert.c. */
public final class Compositing {
    /** Extra bits kept by ImagingAlphaComposite; more would overflow 32 bits. */
    private static final int PRECISION_BITS = 7;

    /** {@code Image.alpha_composite(dst, src)}: "over" with Pillow's integer rounding. */
    public static Raster alphaComposite(Raster dst, Raster src) {
        if (dst.bands != 4 || src.bands != 4 || dst.width != src.width || dst.height != src.height)
            throw new IllegalArgumentException("two RGBA images of one size expected");
        var out = new Raster(dst.width, dst.height, 4);
        for (int i = 0; i < dst.data.length; i += 4) {
            int sa = src.data[i + 3];
            if (sa == 0) {
                System.arraycopy(dst.data, i, out.data, i, 4);
                continue;
            }
            long blend = (long) dst.data[i + 3] * (255 - sa);
            long outa255 = sa * 255L + blend;
            long coef1 = sa * 255L * 255L * (1 << PRECISION_BITS) / outa255;
            long coef2 = 255L * (1 << PRECISION_BITS) - coef1;
            for (int c = 0; c < 3; c++) {
                long tmp = src.data[i + c] * coef1 + dst.data[i + c] * coef2;
                out.data[i + c] = (int) (shiftForDiv255(tmp + (0x80L << PRECISION_BITS)) >> PRECISION_BITS);
            }
            out.data[i + 3] = (int) shiftForDiv255(outa255 + 0x80);
        }
        return out;
    }

    /**
     * {@code Image.alpha_composite(src, (x, y))}: composites onto the covered part of {@code dst} in place.
     * The overlay lies inside {@code dst} here.
     */
    public static void alphaCompositeAt(Raster dst, Raster src, int x, int y) {
        var background = dst.crop(x, y, x + src.width, y + src.height);
        dst.paste(alphaComposite(background, src), x, y);
    }

    /** {@code ImageChops.multiply}: {@code a * b / 255}, truncated. */
    public static Raster multiply(Raster a, Raster b) {
        if (a.bands != b.bands || a.width != b.width || a.height != b.height) throw new IllegalArgumentException();
        var out = new Raster(a.width, a.height, a.bands);
        for (int i = 0; i < a.data.length; i++) out.data[i] = Math.clamp(a.data[i] * b.data[i] / 255, 0, 255);
        return out;
    }

    /** RGBA to RGBa: colour multiplied by alpha with {@code MULDIV255}. */
    static Raster premultiply(Raster in) {
        var out = new Raster(in.width, in.height, 4);
        for (int i = 0; i < in.data.length; i += 4) {
            int alpha = in.data[i + 3];
            for (int c = 0; c < 3; c++) out.data[i + c] = (int) shiftForDiv255((long) in.data[i + c] * alpha + 128);
            out.data[i + 3] = alpha;
        }
        return out;
    }

    /** RGBa back to RGBA; fully opaque and fully transparent pixels keep their colour. */
    static Raster unpremultiply(Raster in) {
        var out = new Raster(in.width, in.height, 4);
        for (int i = 0; i < in.data.length; i += 4) {
            int alpha = in.data[i + 3];
            for (int c = 0; c < 3; c++)
                out.data[i + c] = alpha == 255 || alpha == 0
                    ? in.data[i + c] : Math.clamp(255 * in.data[i + c] / alpha, 0, 255);
            out.data[i + 3] = alpha;
        }
        return out;
    }

    /** {@code SHIFTFORDIV255}: division by 255 with shifts, exact for the ranges Pillow uses it on. */
    static long shiftForDiv255(long a) {
        return ((a >> 8) + a) >> 8;
    }

    private Compositing() {}
}
