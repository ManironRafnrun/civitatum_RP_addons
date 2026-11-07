package team.civitatum.starclock.tools.art;

/** Pillow 11.3.0 filters on one-band images: BoxBlur.c (GaussianBlur) and RankFilter.c (MaxFilter). */
public final class Filters {
    /** Pillow approximates the Gaussian with this many box blurs. */
    private static final int PASSES = 3;
    /** Box weights are kept in 24-bit fixed point. */
    private static final int WEIGHT_BITS = 24;

    /** {@code ImageFilter.GaussianBlur(radius)}: three extended box blurs along x, then along y. */
    public static Raster gaussianBlur(Raster in, float radius) {
        if (in.bands != 1) throw new IllegalArgumentException("one band expected");
        float boxRadius = boxRadius(radius);
        var out = in.copy();
        if (boxRadius != 0) for (int i = 0; i < PASSES; i++) horizontalBoxBlur(out, boxRadius);
        if (boxRadius != 0) {
            var transposed = out.transpose();
            for (int i = 0; i < PASSES; i++) horizontalBoxBlur(transposed, boxRadius);
            out = transposed.transpose();
        }
        return out;
    }

    /** {@code ImageFilter.MaxFilter(size)}: the brightest pixel of the window, edges repeated outwards. */
    public static Raster maxFilter(Raster in, int size) {
        int margin = size / 2;
        var out = new Raster(in.width, in.height, in.bands);
        for (int y = 0; y < in.height; y++)
            for (int x = 0; x < in.width; x++)
                for (int band = 0; band < in.bands; band++) {
                    int max = 0;
                    for (int dy = -margin; dy <= margin; dy++)
                        for (int dx = -margin; dx <= margin; dx++)
                            max = Math.max(max, in.get(Math.clamp(x + dx, 0, in.width - 1),
                                Math.clamp(y + dy, 0, in.height - 1), band));
                    out.set(x, y, band, max);
                }
        return out;
    }

    /**
     * {@code _gaussian_blur_radius}: the box radius whose {@value #PASSES} passes have the Gaussian's variance
     * (Gwosdek et al., "Theoretical foundations of Gaussian convolution by extended box filtering"). Single precision
     * as in Pillow.
     */
    static float boxRadius(float radius) {
        float sigma2 = radius * radius / PASSES;
        float boxLength = (float) Math.sqrt(12.0 * sigma2 + 1.0);
        float l = (float) Math.floor((boxLength - 1.0) / 2.0);
        float a = (2 * l + 1) * (l * (l + 1) - 3 * sigma2);
        a /= 6 * (sigma2 - (l + 1) * (l + 1));
        return l + a;
    }

    /** {@code ImagingHorizontalBoxBlur} in place: a box of {@code 2r + 1} pixels plus fractional end pixels. */
    private static void horizontalBoxBlur(Raster image, float floatRadius) {
        int radius = (int) floatRadius;
        long ww = (long) ((float) (1 << WEIGHT_BITS) / (floatRadius * 2 + 1)) & 0xFFFFFFFFL;
        long fw = (((1L << WEIGHT_BITS) - (radius * 2L + 1) * ww) / 2) & 0xFFFFFFFFL;
        int lastx = image.width - 1;
        int edgeA = Math.min(radius + 1, image.width);
        int edgeB = Math.max(image.width - radius - 1, 0);
        int[] line = new int[image.width];
        int[] out = new int[image.width];
        for (int y = 0; y < image.height; y++) {
            System.arraycopy(image.data, y * image.width, line, 0, image.width);
            blurLine(out, line, lastx, radius, edgeA, edgeB, ww, fw);
            System.arraycopy(out, 0, image.data, y * image.width, image.width);
        }
    }

    /** {@code ImagingLineBoxBlur8}; the accumulators are unsigned 32-bit in Pillow. */
    private static void blurLine(int[] out, int[] in, int lastx, int radius, int edgeA, int edgeB, long ww, long fw) {
        long acc = (long) in[0] * (radius + 1);
        for (int x = 0; x < edgeA - 1; x++) acc += in[x];
        acc += (long) in[lastx] * (radius - edgeA + 1);
        acc &= 0xFFFFFFFFL;
        if (edgeA <= edgeB) {
            for (int x = 0; x < edgeA; x++) acc = step(out, in, x, acc, 0, x + radius, 0, x + radius + 1, ww, fw);
            for (int x = edgeA; x < edgeB; x++)
                acc = step(out, in, x, acc, x - radius - 1, x + radius, x - radius - 1, x + radius + 1, ww, fw);
            for (int x = edgeB; x <= lastx; x++)
                acc = step(out, in, x, acc, x - radius - 1, lastx, x - radius - 1, lastx, ww, fw);
        } else {
            for (int x = 0; x < edgeB; x++) acc = step(out, in, x, acc, 0, x + radius, 0, x + radius + 1, ww, fw);
            for (int x = edgeB; x < edgeA; x++) acc = step(out, in, x, acc, 0, lastx, 0, lastx, ww, fw);
            for (int x = edgeA; x <= lastx; x++)
                acc = step(out, in, x, acc, x - radius - 1, lastx, x - radius - 1, lastx, ww, fw);
        }
    }

    /** MOVE_ACC, ADD_FAR and SAVE of one output pixel; returns the moved accumulator. */
    private static long step(int[] out, int[] in, int x, long acc, int subtract, int add, int left, int right,
                             long ww, long fw) {
        acc = (acc + in[add] - in[subtract]) & 0xFFFFFFFFL;
        long bulk = (acc * ww + (long) (in[left] + in[right]) * fw) & 0xFFFFFFFFL;
        out[x] = (int) (((bulk + (1L << 23)) & 0xFFFFFFFFL) >>> WEIGHT_BITS) & 0xFF;
        return acc;
    }

    private Filters() {}
}
