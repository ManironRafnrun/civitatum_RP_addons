package team.civitatum.starclock.tools.art;

import java.util.Arrays;

/**
 * Pillow 11.3.0 {@code ImageDraw.Draw} on an RGBA image with RGBA inks, or on a one-band mask: the drawing calls
 * the generator uses, with the same coordinate truncation, end points and outline rules. Inks are pixels of the
 * image's band count; {@code null} means "not drawn", as {@code None} does in Python.
 */
public final class Draw {
    private final Raster im;

    public Draw(Raster im) {
        this.im = im;
    }

    public void point(double x, double y, int[] ink) {
        Shapes.point(im, (int) x, (int) y, ink);
    }

    /**
     * {@code line(xy, fill, width)} through points {@code x0, y0, x1, y1, ...}. Thin lines also get their very last
     * point; thick ones are separate quadrilaterals.
     */
    public void line(double[] xy, int[] ink, int width) {
        int n = xy.length / 2;
        if (width <= 1) {
            for (int i = 0; i < n - 1; i++)
                Shapes.line(im, (int) xy[i * 2], (int) xy[i * 2 + 1], (int) xy[i * 2 + 2], (int) xy[i * 2 + 3], ink);
            if (n > 1) Shapes.point(im, (int) xy[n * 2 - 2], (int) xy[n * 2 - 1], ink);
        } else {
            for (int i = 0; i < n - 1; i++)
                Shapes.wideLine(im, (int) xy[i * 2], (int) xy[i * 2 + 1], (int) xy[i * 2 + 2], (int) xy[i * 2 + 3],
                    ink, width, null);
        }
    }

    public void rectangle(double x0, double y0, double x1, double y1, int[] fill, int[] outline, int width) {
        checkOrder(x0, y0, x1, y1);
        if (fill != null) Shapes.rectangle(im, (int) x0, (int) y0, (int) x1, (int) y1, fill, true, 0);
        if (outline != null && !Arrays.equals(outline, fill) && width != 0)
            Shapes.rectangle(im, (int) x0, (int) y0, (int) x1, (int) y1, outline, false, width);
    }

    /**
     * {@code polygon(xy, fill, outline, width)}. A thick outline is drawn twice as wide and clipped by the filled
     * polygon, so that it grows inwards only.
     */
    public void polygon(double[] xy, int[] fill, int[] outline, int width) {
        int[] ixy = new int[xy.length];
        for (int i = 0; i < xy.length; i++) ixy[i] = (int) xy[i];
        if (fill != null) Shapes.polygon(im, ixy, fill, true, 0, null);
        if (outline == null || Arrays.equals(outline, fill) || width == 0) return;
        if (width == 1) {
            Shapes.polygon(im, ixy, outline, false, 1, null);
        } else {
            var mask = new Raster(im.width, im.height, 1);
            Shapes.polygon(mask, ixy, new int[] {1}, true, 0, null);
            Shapes.polygon(im, ixy, outline, false, width * 2 - 1, mask);
        }
    }

    public void ellipse(double x0, double y0, double x1, double y1, int[] fill, int[] outline, int width) {
        checkOrder(x0, y0, x1, y1);
        if (fill != null) EllipseShapes.ellipse(im, (int) x0, (int) y0, (int) x1, (int) y1, fill, true, 0);
        if (outline != null && !Arrays.equals(outline, fill) && width != 0)
            EllipseShapes.ellipse(im, (int) x0, (int) y0, (int) x1, (int) y1, outline, false, width);
    }

    /**
     * {@code rounded_rectangle(xy, radius, fill, outline, width)} with all four corners, for boxes wider and taller
     * than the rounding: pie slices and arcs in the corners, rectangles between them.
     */
    public void roundedRectangle(int x0, int y0, int x1, int y1, int radius, int[] fill, int[] outline, int width) {
        checkOrder(x0, y0, x1, y1);
        int d = radius * 2;
        if (d >= x1 - x0 - 1 || d >= y1 - y0 - 1)
            throw new UnsupportedOperationException("joined corners are not ported");
        if (d == 0) {
            rectangle(x0, y0, x1, y1, fill, outline, width);
            return;
        }
        int r = d / 2;
        int[][] corners = {{x0, y0, x0 + d, y0 + d, 180, 270}, {x1 - d, y0, x1, y0 + d, 270, 360},
            {x1 - d, y1 - d, x1, y1, 0, 90}, {x0, y1 - d, x0 + d, y1, 90, 180}};
        if (fill != null) {
            for (int[] c : corners) EllipseShapes.filledPieslice(im, c[0], c[1], c[2], c[3], c[4], c[5], fill);
            if (x1 - r - 1 > x0 + r + 1) Shapes.rectangle(im, x0 + r + 1, y0, x1 - r - 1, y1, fill, true, 0);
            Shapes.rectangle(im, x0, y0 + r + 1, x0 + r, y1 - r - 1, fill, true, 0);
            Shapes.rectangle(im, x1 - r, y0 + r + 1, x1, y1 - r - 1, fill, true, 0);
        }
        if (outline != null && !Arrays.equals(outline, fill) && width != 0) {
            for (int[] c : corners) EllipseShapes.arc(im, c[0], c[1], c[2], c[3], c[4], c[5], outline, width);
            Shapes.rectangle(im, x0 + r + 1, y0, x1 - r - 1, y0 + width - 1, outline, true, 0);
            Shapes.rectangle(im, x0 + r + 1, y1 - width + 1, x1 - r - 1, y1, outline, true, 0);
            Shapes.rectangle(im, x0, y0 + r + 1, x0 + width - 1, y1 - r - 1, outline, true, 0);
            Shapes.rectangle(im, x1 - width + 1, y0 + r + 1, x1, y1 - r - 1, outline, true, 0);
        }
    }

    /** Pillow refuses boxes given right to left or bottom to top. */
    private static void checkOrder(double x0, double y0, double x1, double y1) {
        if (x1 < x0) throw new IllegalArgumentException("x1 must be greater than or equal to x0");
        if (y1 < y0) throw new IllegalArgumentException("y1 must be greater than or equal to y0");
    }
}
