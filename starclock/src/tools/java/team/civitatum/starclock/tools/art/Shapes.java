package team.civitatum.starclock.tools.art;

import java.util.Arrays;

/**
 * Pillow 11.3.0 libImaging/Draw.c: points, lines, rectangles and polygons on an image Pillow does not blend into
 * (RGBA drawn with an RGBA ink, or a one-band mask), so every pixel is overwritten with the ink.
 * Floating point follows the C types: the polygon scanline is single precision.
 */
final class Shapes {
    /** Edge of the polygon scanline engine. */
    private static final class Edge {
        int x0, y0, xmin, ymin, xmax, ymax;
        float dx;
    }

    static void point(Raster im, int x, int y, int[] ink) {
        if (x >= 0 && x < im.width && y >= 0 && y < im.height) im.setPixel(x, y, ink);
    }

    /** One scanline from {@code x0} to {@code x1} inclusive, clipped; with a mask only where the mask is set. */
    static void hline(Raster im, int x0, int y0, int x1, int[] ink, Raster mask) {
        if (y0 < 0 || y0 >= im.height) return;
        if (x0 < 0) x0 = 0;
        else if (x0 >= im.width) return;
        if (x1 < 0) return;
        else if (x1 >= im.width) x1 = im.width - 1;
        for (; x0 <= x1; x0++) if (mask == null || mask.get(x0, y0, 0) != 0) im.setPixel(x0, y0, ink);
    }

    /** Bresenham line; the end point itself is not drawn. */
    static void line(Raster im, int x0, int y0, int x1, int y1, int[] ink) {
        int dx = x1 - x0, dy = y1 - y0, xs = 1, ys = 1;
        if (dx < 0) {
            dx = -dx;
            xs = -1;
        }
        if (dy < 0) {
            dy = -dy;
            ys = -1;
        }
        if (dx == 0) {
            for (int i = 0; i < dy; i++, y0 += ys) point(im, x0, y0, ink);
        } else if (dy == 0) {
            for (int i = 0; i < dx; i++, x0 += xs) point(im, x0, y0, ink);
        } else if (dx > dy) {
            int n = dx;
            dy += dy;
            int e = dy - dx;
            dx += dx;
            for (int i = 0; i < n; i++) {
                point(im, x0, y0, ink);
                if (e >= 0) {
                    y0 += ys;
                    e -= dx;
                }
                e += dy;
                x0 += xs;
            }
        } else {
            int n = dy;
            dx += dx;
            int e = dx - dy;
            dy += dy;
            for (int i = 0; i < n; i++) {
                point(im, x0, y0, ink);
                if (e >= 0) {
                    x0 += xs;
                    e -= dy;
                }
                e += dx;
                y0 += ys;
            }
        }
    }

    /** {@code ImagingDrawWideLine}: a line {@code width} pixels thick, drawn as a filled quadrilateral. */
    static void wideLine(Raster im, int x0, int y0, int x1, int y1, int[] ink, int width, Raster mask) {
        int dx = x1 - x0, dy = y1 - y0;
        if (dx == 0 && dy == 0) {
            point(im, x0, y0, ink);
            return;
        }
        double bigHypotenuse = Math.hypot(dx, dy);
        double smallHypotenuse = (width - 1) / 2.0;
        double ratioMax = roundUp(smallHypotenuse) / bigHypotenuse;
        double ratioMin = roundDown(smallHypotenuse) / bigHypotenuse;
        int dxmin = roundDown(ratioMin * dy), dxmax = roundDown(ratioMax * dy);
        int dymin = roundDown(ratioMin * dx), dymax = roundDown(ratioMax * dx);
        int[][] v = {
            {x0 - dxmin, y0 + dymax}, {x1 - dxmin, y1 + dymax}, {x1 + dxmax, y1 - dymin}, {x0 + dxmax, y0 - dymin}};
        var edges = new Edge[4];
        for (int i = 0; i < 4; i++) edges[i] = edge(v[i][0], v[i][1], v[(i + 1) % 4][0], v[(i + 1) % 4][1]);
        polygonGeneric(im, edges, ink, mask);
    }

    /** {@code ImagingDrawRectangle}; the outline grows inwards. */
    static void rectangle(Raster im, int x0, int y0, int x1, int y1, int[] ink, boolean fill, int width) {
        if (y0 > y1) {
            int t = y0;
            y0 = y1;
            y1 = t;
        }
        if (fill) {
            if (y0 < 0) y0 = 0;
            else if (y0 >= im.height) return;
            if (y1 < 0) return;
            else if (y1 > im.height) y1 = im.height;
            for (int y = y0; y <= y1; y++) hline(im, x0, y, x1, ink, null);
        } else {
            if (width == 0) width = 1;
            for (int i = 0; i < width; i++) {
                hline(im, x0, y0 + i, x1, ink, null);
                hline(im, x0, y1 - i, x1, ink, null);
                line(im, x1 - i, y0 + width, x1 - i, y1 - width + 1, ink);
                line(im, x0 + i, y0 + width, x0 + i, y1 - width + 1, ink);
            }
        }
    }

    /** {@code ImagingDrawPolygon} with vertices {@code x0, y0, x1, y1, ...}. */
    static void polygon(Raster im, int[] xy, int[] ink, boolean fill, int width, Raster mask) {
        int count = xy.length / 2;
        if (count <= 0) return;
        if (fill) {
            var edges = new Edge[count];
            int n = 0, i;
            for (i = 0; i < count - 1; i++) {
                int x0 = xy[i * 2], y0 = xy[i * 2 + 1], x1 = xy[i * 2 + 2], y1 = xy[i * 2 + 3];
                if (y0 == y1 && i != 0 && y0 == xy[i * 2 - 1]) {
                    // a horizontal line right after another one, going the same way, extends it
                    var last = edges[n - 1];
                    if (x1 > x0 && x0 > xy[i * 2 - 2]) {
                        last.xmax = x1;
                        continue;
                    } else if (x1 < x0 && x0 < xy[i * 2 - 2]) {
                        last.xmin = x1;
                        continue;
                    }
                }
                edges[n++] = edge(x0, y0, x1, y1);
            }
            if (xy[i * 2] != xy[0] || xy[i * 2 + 1] != xy[1]) edges[n++] = edge(xy[i * 2], xy[i * 2 + 1], xy[0], xy[1]);
            polygonGeneric(im, Arrays.copyOf(edges, n), ink, mask);
        } else if (width == 1) {
            int i;
            for (i = 0; i < count - 1; i++) line(im, xy[i * 2], xy[i * 2 + 1], xy[i * 2 + 2], xy[i * 2 + 3], ink);
            line(im, xy[i * 2], xy[i * 2 + 1], xy[0], xy[1], ink);
        } else {
            int i;
            for (i = 0; i < count - 1; i++)
                wideLine(im, xy[i * 2], xy[i * 2 + 1], xy[i * 2 + 2], xy[i * 2 + 3], ink, width, mask);
            wideLine(im, xy[i * 2], xy[i * 2 + 1], xy[0], xy[1], ink, width, mask);
        }
    }

    private static Edge edge(int x0, int y0, int x1, int y1) {
        var e = new Edge();
        e.xmin = Math.min(x0, x1);
        e.xmax = Math.max(x0, x1);
        e.ymin = Math.min(y0, y1);
        e.ymax = Math.max(y0, y1);
        e.dx = y0 == y1 ? 0.0F : ((float) (x1 - x0)) / (y1 - y0);
        e.x0 = x0;
        e.y0 = y0;
        return e;
    }

    /** {@code polygon_generic}: scanline fill, for the non-blending case only. */
    private static void polygonGeneric(Raster im, Edge[] e, int[] ink, Raster mask) {
        int n = e.length;
        if (n <= 0) return;
        var table = new Edge[n];
        int edgeCount = 0;
        int ymin = im.height - 1, ymax = 0;
        for (var edge : e) {
            if (ymin > edge.ymin) ymin = edge.ymin;
            if (ymax < edge.ymax) ymax = edge.ymax;
            if (edge.ymin == edge.ymax) {
                hline(im, edge.xmin, edge.ymin, edge.xmax, ink, mask);
                continue;
            }
            table[edgeCount++] = edge;
        }
        if (ymin < 0) ymin = 0;
        if (ymax > im.height) ymax = im.height;
        float[] xx = new float[edgeCount * 2];
        for (; ymin <= ymax; ymin++) {
            int j = 0;
            for (int i = 0; i < edgeCount; i++) {
                var current = table[i];
                if (ymin < current.ymin || ymin > current.ymax) continue;
                xx[j++] = (ymin - current.y0) * current.dx + current.x0;
                if (ymin == current.ymax && ymin < ymax) {
                    // needed to draw consistent polygons
                    xx[j] = xx[j - 1];
                    j++;
                } else if ((ymin == current.ymin || ymin == current.ymax) && current.dx != 0) {
                    // connect discontiguous corners
                    for (int k = 0; k < i; k++) {
                        var other = table[k];
                        if ((ymin != other.ymin && ymin != other.ymax) || other.dx == 0) continue;
                        if (roundf(xx[j - 1]) != roundf((ymin - other.y0) * other.dx + other.x0)) continue;
                        int offset = ymin == current.ymax ? -1 : 1;
                        float adjacent = (ymin + offset - current.y0) * current.dx + current.x0;
                        if (ymin + offset >= other.ymin && ymin + offset <= other.ymax) {
                            float adjacentOther = (ymin + offset - other.y0) * other.dx + other.x0;
                            if (xx[j - 1] > adjacent + 1 && xx[j - 1] > adjacentOther + 1)
                                xx[j - 1] = roundf(Math.max(adjacent, adjacentOther)) + 1;
                            else if (xx[j - 1] < adjacent - 1 && xx[j - 1] < adjacentOther - 1)
                                xx[j - 1] = roundf(Math.min(adjacent, adjacentOther)) - 1;
                            break;
                        }
                    }
                }
            }
            Arrays.sort(xx, 0, j);
            for (int i = 1; i < j; i += 2) hline(im, roundUp(xx[i - 1]), ymin, roundDown(xx[i]), ink, mask);
        }
    }

    /** {@code ROUND_UP} on a float: half away from zero, the positive half added in single precision. */
    static int roundUp(float f) {
        return f >= 0.0 ? (int) Math.floor(f + 0.5F) : (int) -Math.floor(Math.abs((double) f) + 0.5F);
    }

    /** {@code ROUND_DOWN} on a float: half towards zero, the positive half subtracted in single precision. */
    static int roundDown(float f) {
        return f >= 0.0 ? (int) Math.ceil(f - 0.5F) : (int) -Math.ceil(Math.abs((double) f) - 0.5F);
    }

    static int roundUp(double f) {
        return f >= 0.0 ? (int) Math.floor(f + 0.5F) : (int) -Math.floor(Math.abs(f) + 0.5F);
    }

    static int roundDown(double f) {
        return f >= 0.0 ? (int) Math.ceil(f - 0.5F) : (int) -Math.ceil(Math.abs(f) - 0.5F);
    }

    /** C {@code roundf}: half away from zero, exact for every float. */
    static float roundf(float f) {
        double a = Math.floor(Math.abs((double) f) + 0.5);
        return (float) Math.copySign(a, f);
    }

    private Shapes() {}
}
