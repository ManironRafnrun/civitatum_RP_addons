package team.civitatum.starclock.tools.art;

import java.util.ArrayList;
import java.util.List;

/**
 * Pillow 11.3.0 libImaging/Draw.c ellipses: the integer quarter tracer, thick ellipses from two of them and the
 * half-plane clipping tree that cuts arcs and pie slices out of them. Angles are single precision as in C;
 * trigonometry goes through fdlibm so that it does not depend on the JVM.
 */
final class EllipseShapes {
    /** Traces a quarter ellipse on the grid {(2x + x0, 2y + y0)} that contains (a, b). */
    private static final class Quarter {
        int cx, cy, ex, ey;
        long a2, b2, a2b2;
        boolean finished;
        int retX, retY;

        Quarter(int a, int b) {
            if (a < 0 || b < 0) {
                finished = true;
                return;
            }
            cx = a;
            cy = b % 2;
            ex = a % 2;
            ey = b;
            a2 = a * a;
            b2 = b * b;
            a2b2 = a2 * b2;
        }

        private long delta(long x, long y) {
            return Math.abs(a2 * y * y + b2 * x * x - a2b2);
        }

        /** {@code quarter_next}; the point lands in {@link #retX}, {@link #retY}. */
        boolean next() {
            if (finished) return false;
            retX = cx;
            retY = cy;
            if (cx == ex && cy == ey) {
                finished = true;
            } else {
                int nx = cx, ny = cy + 2;
                long ndelta = delta(nx, ny);
                if (nx > 1) {
                    long newdelta = delta(cx - 2, cy + 2);
                    if (ndelta > newdelta) {
                        nx = cx - 2;
                        ny = cy + 2;
                        ndelta = newdelta;
                    }
                    newdelta = delta(cx - 2, cy);
                    if (ndelta > newdelta) {
                        nx = cx - 2;
                        ny = cy;
                    }
                }
                cx = nx;
                cy = ny;
            }
            return true;
        }
    }

    /** Horizontal segments of an ellipse {@code w} pixels thick, still on the doubled grid. */
    private static final class Ellipse {
        final Quarter outer;
        Quarter inner;
        int py, pl, pr;
        final int[] cy = new int[4], cl = new int[4], cr = new int[4];
        int bufcnt;
        boolean finished;
        final int leftmost;
        int retX0, retY, retX1;

        Ellipse(int a, int b, int w) {
            leftmost = a % 2;
            outer = new Quarter(a, b);
            if (w < 1 || !outer.next()) {
                finished = true;
            } else {
                pr = outer.retX;
                py = outer.retY;
                inner = new Quarter(a - 2 * (w - 1), b - 2 * (w - 1));
                pl = leftmost;
            }
        }

        boolean next() {
            if (bufcnt == 0) {
                if (finished) return false;
                int y = py, l = pl, r = pr;
                boolean more;
                while ((more = outer.next()) && outer.retY <= y) {}
                if (!more) {
                    finished = true;
                } else {
                    pr = outer.retX;
                    py = outer.retY;
                }
                while ((more = inner.next()) && inner.retY <= y) l = inner.retX;
                pl = more ? inner.retX : leftmost;
                if ((l > 0 || l < r) && y > 0) push(l == 0 ? 2 : l, y, r);
                if (y > 0) push(-r, y, -l);
                if (l > 0 || l < r) push(l == 0 ? 2 : l, -y, r);
                push(-r, -y, -l);
            }
            --bufcnt;
            retX0 = cl[bufcnt];
            retY = cy[bufcnt];
            retX1 = cr[bufcnt];
            return true;
        }

        private void push(int left, int y, int right) {
            cl[bufcnt] = left;
            cy[bufcnt] = y;
            cr[bufcnt] = right;
            ++bufcnt;
        }
    }

    private enum Type { AND, OR, CLIP }

    private static final class Node {
        Type type;
        double a, b, c;
        Node l, r;

        Node(Type type) {
            this.type = type;
        }

        static Node clip(double a, double b, double c) {
            var node = new Node(Type.CLIP);
            node.a = a;
            node.b = b;
            node.c = c;
            return node;
        }

        static Node combine(Type type, Node l, Node r) {
            var node = new Node(type);
            node.l = l;
            node.r = r;
            return node;
        }
    }

    /** End point of a clipped segment: {@code type} 1 opens it, -1 closes it. */
    private record Event(int x, int type) {}

    /** {@code ImagingDrawEllipse}; a filled ellipse is one as thick as it is big. */
    static void ellipse(Raster im, int x0, int y0, int x1, int y1, int[] ink, boolean fill, int width) {
        int a = x1 - x0, b = y1 - y0;
        if (a < 0 || b < 0) return;
        if (fill) width = a + b;
        var st = new Ellipse(a, b, width);
        while (st.next())
            Shapes.hline(im, x0 + (st.retX0 + a) / 2, y0 + (st.retY + b) / 2, x0 + (st.retX1 + a) / 2, ink, null);
    }

    /** {@code ImagingDrawArc}. */
    static void arc(Raster im, int x0, int y0, int x1, int y1, float start, float end, int[] ink, int width) {
        float[] angles = normalizeAngles(start, end);
        start = angles[0];
        end = angles[1];
        if (start + 360 == end) {
            ellipse(im, x0, y0, x1, y1, ink, false, width);
            return;
        }
        if (start == end) return;
        int a = x1 - x0, b = y1 - y0;
        if (a < 0 || b < 0) return;
        var clip = arcTree(a, b, start, end);
        clipped(im, x0, y0, a, b, new Ellipse(a, b, width), clip, ink);
    }

    /** {@code ImagingDrawPieslice}, filled only: Pillow's rounded rectangle needs no outlined slices. */
    static void filledPieslice(Raster im, int x0, int y0, int x1, int y1, float start, float end, int[] ink) {
        float[] angles = normalizeAngles(start, end);
        start = angles[0];
        end = angles[1];
        if (start + 360 == end) {
            ellipse(im, x0, y0, x1, y1, ink, true, 0);
            return;
        }
        if (start == end) return;
        int a = x1 - x0, b = y1 - y0;
        if (a < 0 || b < 0) return;
        clipped(im, x0, y0, a, b, new Ellipse(a, b, x1 + y1 - x0 - y0), pieTree(a, b, start, end), ink);
    }

    /** {@code clipEllipseNew}: every segment of the ellipse cut by the tree, mapped back to pixels. */
    private static void clipped(Raster im, int x0, int y0, int a, int b, Ellipse st, Node root, int[] ink) {
        while (st.next()) {
            var events = clip(root, st.retX0, st.retY, st.retX1);
            for (int i = 0; i + 1 < events.size(); i += 2)
                Shapes.hline(im, x0 + (events.get(i).x() + a) / 2, y0 + (st.retY + b) / 2,
                    x0 + (events.get(i + 1).x() + a) / 2, ink, null);
        }
    }

    /** {@code normalize_angles}: afterwards {@code 0 <= start < 360} and {@code start <= end <= start + 360}. */
    static float[] normalizeAngles(float al, float ar) {
        if (ar - al >= 360) return new float[] {0, 360};
        al = (float) ((al < 0 ? 360 - (-al % 360.0) : al) % 360.0);
        ar = (float) (al + (ar < al ? 360 - (double) (al - ar) % 360.0 : (double) (ar - al)) % 360.0);
        return new float[] {al, ar};
    }

    /** {@code arc_init}: an arc with caps orthogonal to the curve; tall ellipses are handled transposed. */
    private static Node arcTree(int a, int b, float al, float ar) {
        if (a < b) {
            var root = arcTree(b, a, 90 - ar, 90 - al);
            transpose(root);
            return root;
        }
        float[] angles = normalizeAngles(al, ar);
        al = angles[0];
        ar = angles[1];
        if (ar == al + 360) return null;
        var lc = Node.clip(-a * StrictMath.sin(al * Math.PI / 180.0), b * StrictMath.cos(al * Math.PI / 180.0),
            (a * a - b * b) * StrictMath.sin(al * Math.PI / 90.0) / 2.0);
        var rc = Node.clip(a * StrictMath.sin(ar * Math.PI / 180.0), -b * StrictMath.cos(ar * Math.PI / 180.0),
            (b * b - a * a) * StrictMath.sin(ar * Math.PI / 90.0) / 2.0);
        if (al % 180.0 == 0 || ar % 180.0 == 0) return Node.combine(ar - al < 180 ? Type.AND : Type.OR, lc, rc);
        if (((int) (al / 180) + (int) (ar / 180)) % 2 == 1) {
            var left = Node.combine(Type.AND, Node.clip(0, (int) (al / 180) % 2 == 0 ? 1 : -1, 0), lc);
            var right = Node.combine(Type.AND, Node.clip(0, (int) (ar / 180) % 2 == 0 ? 1 : -1, 0), rc);
            return Node.combine(Type.OR, left, right);
        }
        Type type = ar - al < 180 ? Type.AND : Type.OR;
        return Node.combine(type, Node.combine(type, lc, rc), Node.clip(0, ar < 180 || ar > 540 ? 1 : -1, 0));
    }

    /** {@code pie_init}: a pie slice, with an extra half plane against spikes on narrow slices. */
    private static Node pieTree(int a, int b, float al, float ar) {
        double xl = a * StrictMath.cos(al * Math.PI / 180.0), xr = a * StrictMath.cos(ar * Math.PI / 180.0);
        double yl = b * StrictMath.sin(al * Math.PI / 180.0), yr = b * StrictMath.sin(ar * Math.PI / 180.0);
        var root = Node.combine(ar - al < 180 ? Type.AND : Type.OR, Node.clip(-yl, xl, 0), Node.clip(yr, -xr, 0));
        if (ar - al < 90) root = Node.combine(Type.AND, root, Node.clip((xl + xr) / 2.0, (yl + yr) / 2.0, 0));
        return root;
    }

    /** Mirrors the clipping half planes over y = x. */
    private static void transpose(Node root) {
        if (root == null) return;
        if (root.type == Type.CLIP) {
            double t = root.a;
            root.a = root.b;
            root.b = t;
        }
        transpose(root.l);
        transpose(root.r);
    }

    /** {@code clip_tree_do_clip}: the parts of segment {@code [x0, x1]} on row {@code y} inside the tree. */
    private static List<Event> clip(Node root, int x0, int y, int x1) {
        var out = new ArrayList<Event>();
        if (root == null) {
            out.add(new Event(x0, 1));
            out.add(new Event(x1, -1));
            return out;
        }
        if (root.type == Type.CLIP) {
            final double eps = 1e-9;
            double a = root.a, b = root.b, c = root.c;
            if (Math.abs(a) < eps) {
                if (b * y + c < -eps) {
                    x0 = 1;
                    x1 = 0;
                }
            } else {
                double ix = -(b * y + c) / a;
                if (a * x0 + b * y + c < eps) x0 = (int) lround(Math.max(x0, ix));
                if (a * x1 + b * y + c < eps) x1 = (int) lround(Math.min(x1, ix));
            }
            if (x0 <= x1) {
                out.add(new Event(x0, 1));
                out.add(new Event(x1, -1));
            }
            return out;
        }
        var l1 = clip(root.l, x0, y, x1);
        var l2 = clip(root.r, x0, y, x1);
        int i1 = 0, i2 = 0, k1 = 0, k2 = 0;
        Event tail = null;
        while (i1 < l1.size() || i2 < l2.size()) {
            Event t;
            Event e1 = i1 < l1.size() ? l1.get(i1) : null, e2 = i2 < l2.size() ? l2.get(i2) : null;
            if (e2 == null || (e1 != null && (e1.x() < e2.x() || (e1.x() == e2.x() && e1.type() > e2.type())))) {
                t = l1.get(i1++);
                k1 += t.type();
            } else {
                t = l2.get(i2++);
                k2 += t.type();
            }
            boolean keep = root.type == Type.OR
                ? (t.type() == 1 && (tail == null || tail.type() == -1)) || (t.type() == -1 && k1 == 0 && k2 == 0)
                : (t.type() == 1 && (tail == null || tail.type() == -1) && k1 > 0 && k2 > 0)
                    || (t.type() == -1 && tail != null && tail.type() == 1 && (k1 == 0 || k2 == 0));
            if (keep) {
                out.add(t);
                tail = t;
            }
        }
        return out;
    }

    /** C {@code lround}: half away from zero, exact for every double. */
    static long lround(double x) {
        double a = Math.abs(x);
        double t = Math.floor(a);
        long v = (long) t + (a - t >= 0.5 ? 1 : 0);
        return x < 0 ? -v : v;
    }

    private EllipseShapes() {}
}
