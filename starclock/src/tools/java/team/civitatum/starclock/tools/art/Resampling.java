package team.civitatum.starclock.tools.art;

import java.util.function.DoubleUnaryOperator;

/**
 * Pillow 11.3.0 resizing (libImaging/Resample.c, Geometry.c and Image.resize/thumbnail), ported operation by
 * operation so that the result matches Pillow's bit for bit.
 */
public final class Resampling {
    public enum Filter {
        BOX(0.5, x -> x > -0.5 && x <= 0.5 ? 1.0 : 0.0),
        LANCZOS(3.0, x -> -3.0 <= x && x < 3.0 ? sinc(x) * sinc(x / 3) : 0.0);

        final double support;
        final DoubleUnaryOperator function;

        Filter(double support, DoubleUnaryOperator function) {
            this.support = support;
            this.function = function;
        }
    }

    /** Fixed point of the 8-bit passes: 8 bits of result and 2 bits of headroom for negative lobes, in 32. */
    private static final int PRECISION_BITS = 32 - 8 - 2;

    /**
     * {@code Image.resize(size, filter)} with Pillow's default box and no {@code reducing_gap}.
     * Four-band images are treated as "RGBA": premultiplied for the filter and restored after it.
     */
    public static Raster resize(Raster in, int width, int height, Filter filter) {
        if (in.width == width && in.height == height) return in.copy();
        if (in.bands == 4)
            return Compositing.unpremultiply(resample(Compositing.premultiply(in), width, height, filter));
        return resample(in, width, height, filter);
    }

    /**
     * {@code Image.thumbnail((size, size), LANCZOS)} with its default {@code reducing_gap = 2.0} on an in-memory image,
     * which has no draft mode. Pillow would reduce first when the image is two or more times bigger than the gap;
     * that path is not ported and fails loudly instead.
     */
    public static Raster thumbnail(Raster in, int size) {
        int[] target = thumbnailSize(in.width, in.height, size, size);
        if (target == null || (target[0] == in.width && target[1] == in.height)) return in;
        final double reducingGap = 2.0;
        if ((int) (in.width / (double) target[0] / reducingGap) > 1
            || (int) (in.height / (double) target[1] / reducingGap) > 1)
            throw new UnsupportedOperationException("Image.reduce is not ported: " + in.width + "x" + in.height);
        return resize(in, target[0], target[1], Filter.LANCZOS);
    }

    /** {@code Image.resize(size, NEAREST)}: Pillow's affine scaling with pixel centres. */
    public static Raster nearest(Raster in, int width, int height) {
        var out = new Raster(width, height, in.bands);
        double ax = (double) (float) in.width / width;
        double ay = (double) (float) in.height / height;
        int[] xin = new int[width];
        double xo = ax * 0.5;
        int xmin = width, xmax = 0;
        for (int x = 0; x < width; x++) {
            int v = coord(xo);
            if (v >= 0 && v < in.width) {
                xmax = x + 1;
                if (x < xmin) xmin = x;
                xin[x] = v;
            }
            xo += ax;
        }
        double yo = ay * 0.5;
        for (int y = 0; y < height; y++) {
            int yi = coord(yo);
            if (yi >= 0 && yi < in.height)
                for (int x = xmin; x < xmax; x++)
                    System.arraycopy(in.data, (yi * in.width + xin[x]) * in.bands, out.data, (y * width + x) * in.bands,
                        in.bands);
            yo += ay;
        }
        return out;
    }

    /** {@code preserve_aspect_ratio} of {@code Image.thumbnail}; {@code null} when the image already fits. */
    static int[] thumbnailSize(int width, int height, int maxX, int maxY) {
        int x = maxX, y = maxY;
        if (x >= width && y >= height) return null;
        double aspect = (double) width / height;
        if ((double) x / y >= aspect) {
            double number = y * aspect;
            final int fy = y;
            x = roundAspect(number, n -> Math.abs(aspect - (double) n / fy));
        } else {
            double number = x / aspect;
            final int fx = x;
            y = roundAspect(number, n -> n == 0 ? 0 : Math.abs(aspect - (double) fx / n));
        }
        return new int[] {x, y};
    }

    /** {@code max(min(floor(n), ceil(n), key=key), 1)}: on a tie Python's min keeps the first, the floor. */
    private static int roundAspect(double number, java.util.function.IntToDoubleFunction key) {
        int lo = (int) Math.floor(number), hi = (int) Math.ceil(number);
        int best = key.applyAsDouble(hi) < key.applyAsDouble(lo) ? hi : lo;
        return Math.max(best, 1);
    }

    private static Raster resample(Raster in, int width, int height, Filter filter) {
        float[] box = {0, 0, in.width, in.height};
        boolean needHorizontal = width != in.width || box[0] != 0 || box[2] != width;
        boolean needVertical = height != in.height || box[1] != 0 || box[3] != height;
        var horizontal = new Coefficients(in.width, box[0], box[2], width, filter);
        var vertical = new Coefficients(in.height, box[1], box[3], height, filter);
        int yFirst = vertical.bounds[0];
        int yLast = vertical.bounds[height * 2 - 2] + vertical.bounds[height * 2 - 1];
        Raster current = in;
        Raster out = null;
        if (needHorizontal) {
            for (int i = 0; i < height; i++) vertical.bounds[i * 2] -= yFirst;
            var temp = new Raster(width, yLast - yFirst, in.bands);
            horizontalPass(temp, in, yFirst, horizontal);
            current = out = temp;
        }
        if (needVertical) {
            out = new Raster(current.width, height, current.bands);
            verticalPass(out, current, vertical);
        }
        return out == null ? current.copy() : out;
    }

    private static void horizontalPass(Raster out, Raster in, int offset, Coefficients c) {
        int[] k = c.normalized();
        for (int yy = 0; yy < out.height; yy++)
            for (int xx = 0; xx < out.width; xx++) {
                int xmin = c.bounds[xx * 2], xmax = c.bounds[xx * 2 + 1];
                for (int band = 0; band < in.bands; band++) {
                    int ss = 1 << (PRECISION_BITS - 1);
                    for (int x = 0; x < xmax; x++) ss += in.get(x + xmin, yy + offset, band) * k[xx * c.ksize + x];
                    out.set(xx, yy, band, clip8(ss));
                }
            }
    }

    private static void verticalPass(Raster out, Raster in, Coefficients c) {
        int[] k = c.normalized();
        for (int yy = 0; yy < out.height; yy++) {
            int ymin = c.bounds[yy * 2], ymax = c.bounds[yy * 2 + 1];
            for (int xx = 0; xx < out.width; xx++)
                for (int band = 0; band < in.bands; band++) {
                    int ss = 1 << (PRECISION_BITS - 1);
                    for (int y = 0; y < ymax; y++) ss += in.get(xx, y + ymin, band) * k[yy * c.ksize + y];
                    out.set(xx, yy, band, clip8(ss));
                }
        }
    }

    /** {@code precompute_coeffs}: per output pixel, the first source pixel, their count and the weights. */
    private static final class Coefficients {
        final int ksize;
        final int[] bounds;
        final double[] kk;

        Coefficients(int inSize, float in0, float in1, int outSize, Filter filter) {
            double scale = (double) (in1 - in0) / outSize;
            double filterscale = Math.max(scale, 1.0);
            double support = filter.support * filterscale;
            ksize = (int) Math.ceil(support) * 2 + 1;
            kk = new double[outSize * ksize];
            bounds = new int[outSize * 2];
            for (int xx = 0; xx < outSize; xx++) {
                double center = in0 + (xx + 0.5) * scale;
                double ww = 0.0;
                double ss = 1.0 / filterscale;
                int xmin = Math.max((int) (center - support + 0.5), 0);
                int xmax = Math.min((int) (center + support + 0.5), inSize) - xmin;
                for (int x = 0; x < xmax; x++) {
                    double w = filter.function.applyAsDouble((x + xmin - center + 0.5) * ss);
                    kk[xx * ksize + x] = w;
                    ww += w;
                }
                for (int x = 0; x < xmax; x++) if (ww != 0.0) kk[xx * ksize + x] /= ww;
                bounds[xx * 2] = xmin;
                bounds[xx * 2 + 1] = xmax;
            }
        }

        /** {@code normalize_coeffs_8bpc}: weights in fixed point, rounded away from zero. */
        int[] normalized() {
            int[] out = new int[kk.length];
            for (int i = 0; i < kk.length; i++)
                out[i] = kk[i] < 0
                    ? (int) (-0.5 + kk[i] * (1 << PRECISION_BITS)) : (int) (0.5 + kk[i] * (1 << PRECISION_BITS));
            return out;
        }
    }

    private static int clip8(int value) {
        return Math.clamp(value >> PRECISION_BITS, 0, 255);
    }

    /** {@code COORD} of Geometry.c: truncation, with every negative coordinate mapped to -1. */
    private static int coord(double v) {
        return v < 0.0 ? -1 : (int) v;
    }

    private static double sinc(double x) {
        if (x == 0.0) return 1.0;
        x = x * Math.PI;
        // fdlibm, so that the weights do not depend on the JVM's intrinsics
        return StrictMath.sin(x) / x;
    }

    private Resampling() {}
}
