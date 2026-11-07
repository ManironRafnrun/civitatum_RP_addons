package team.civitatum.hud.client;

/**
 * Pixels of the round frame: a 1 px outline on the outside, then a 1 px band lit on the upper left half and shaded on
 * the lower right one, like the square frame's gold and shadow edges.
 */
final class FrameRing {
    static final int NONE = 0;
    static final int OUTLINE = 1;
    static final int LIT = 2;
    static final int SHADE = 3;

    private FrameRing() {
    }

    /** The part of the ring at an offset from its centre (of a pixel's centre). */
    static int at(double dx, double dy, double radius) {
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance > radius || distance <= radius - 2) return NONE;
        if (distance > radius - 1) return OUTLINE;
        return dx + dy < 0 ? LIT : SHADE;
    }
}
