package team.civitatum.starclock.tools.art;

import java.util.Arrays;

/**
 * An 8-bit image laid out like Pillow's: {@code bands} samples per pixel, rows top to bottom.
 * One band stands for Pillow's "L" and "1" modes, four for "RGBA" and "RGBa".
 */
public final class Raster {
    public final int width;
    public final int height;
    public final int bands;
    public final int[] data;

    public Raster(int width, int height, int bands) {
        this.width = width;
        this.height = height;
        this.bands = bands;
        this.data = new int[width * height * bands];
    }

    /** {@code Image.new(mode, size, color)}. */
    public static Raster filled(int width, int height, int... color) {
        var raster = new Raster(width, height, color.length);
        for (int i = 0; i < raster.data.length; i += color.length)
            System.arraycopy(color, 0, raster.data, i, color.length);
        return raster;
    }

    public int get(int x, int y, int band) {
        return data[(y * width + x) * bands + band];
    }

    public void set(int x, int y, int band, int value) {
        data[(y * width + x) * bands + band] = value;
    }

    /** Writes the whole pixel, as Pillow's draw does on images it does not blend into. */
    public void setPixel(int x, int y, int[] ink) {
        System.arraycopy(ink, 0, data, (y * width + x) * bands, bands);
    }

    public Raster copy() {
        var copy = new Raster(width, height, bands);
        System.arraycopy(data, 0, copy.data, 0, data.length);
        return copy;
    }

    /** {@code Image.crop}; the box lies inside the image here, so no padding is needed. */
    public Raster crop(int x0, int y0, int x1, int y1) {
        if (x0 < 0 || y0 < 0 || x1 > width || y1 > height) throw new IllegalArgumentException("crop outside the image");
        var out = new Raster(x1 - x0, y1 - y0, bands);
        for (int y = 0; y < out.height; y++)
            System.arraycopy(data, ((y + y0) * width + x0) * bands, out.data, y * out.width * bands, out.width * bands);
        return out;
    }

    /** {@code Image.paste(image, (x, y))} without a mask: a plain copy, clipped to this image. */
    public void paste(Raster image, int x, int y) {
        if (image.bands != bands) throw new IllegalArgumentException("band mismatch");
        for (int sy = 0; sy < image.height; sy++) {
            int ty = sy + y;
            if (ty < 0 || ty >= height) continue;
            int sx0 = Math.max(0, -x);
            int sx1 = Math.min(image.width, width - x);
            if (sx0 >= sx1) continue;
            System.arraycopy(image.data, (sy * image.width + sx0) * bands, data, (ty * width + x + sx0) * bands,
                (sx1 - sx0) * bands);
        }
    }

    /** {@code Image.putalpha}: replaces the last band with a one-band image of the same size. */
    public void putAlpha(Raster alpha) {
        if (alpha.bands != 1 || alpha.width != width || alpha.height != height) throw new IllegalArgumentException();
        for (int i = 0; i < width * height; i++) data[i * bands + bands - 1] = alpha.data[i];
    }

    /** {@code Image.point} with a per-value function, as Pillow builds a lookup table from it. */
    public Raster point(java.util.function.IntUnaryOperator function) {
        int[] table = new int[256];
        for (int v = 0; v < 256; v++) table[v] = Math.clamp(function.applyAsInt(v), 0, 255);
        var out = new Raster(width, height, bands);
        for (int i = 0; i < data.length; i++) out.data[i] = table[data[i]];
        return out;
    }

    /** {@code Image.transpose(Transpose.ROTATE_180)}. */
    public Raster rotate180() {
        var out = new Raster(width, height, bands);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                System.arraycopy(data, (y * width + x) * bands, out.data,
                    ((height - 1 - y) * width + width - 1 - x) * bands, bands);
        return out;
    }

    /** Swaps rows and columns, as Pillow's ImagingTranspose does for the vertical blur pass. */
    Raster transpose() {
        var out = new Raster(height, width, bands);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                System.arraycopy(data, (y * width + x) * bands, out.data, (x * height + y) * bands, bands);
        return out;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Raster r && r.width == width && r.height == height && r.bands == bands
            && Arrays.equals(r.data, data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }
}
