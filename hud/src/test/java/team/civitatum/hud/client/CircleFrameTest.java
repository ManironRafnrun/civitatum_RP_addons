package team.civitatum.hud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The round frame: a 1 px outline outside a 1 px lit or shaded band, nothing inside or outside the ring. */
class CircleFrameTest {
    private static final double RADIUS = 52;

    @Test
    void ringIsTwoPixelsWide() {
        assertEquals(FrameRing.NONE, FrameRing.at(RADIUS + 0.5, 0, RADIUS), "outside");
        assertEquals(FrameRing.OUTLINE, FrameRing.at(RADIUS - 0.5, 0, RADIUS));
        assertEquals(FrameRing.SHADE, FrameRing.at(RADIUS - 1.5, 0, RADIUS));
        assertEquals(FrameRing.NONE, FrameRing.at(RADIUS - 2.5, 0, RADIUS), "map inside");
    }

    @Test
    void upperLeftIsLitAndLowerRightShaded() {
        double d = (RADIUS - 1.5) / Math.sqrt(2);
        assertEquals(FrameRing.LIT, FrameRing.at(-d, -d, RADIUS));
        assertEquals(FrameRing.SHADE, FrameRing.at(d, d, RADIUS));
        assertEquals(FrameRing.LIT, FrameRing.at(0, -(RADIUS - 1.5), RADIUS), "top");
        assertEquals(FrameRing.LIT, FrameRing.at(-(RADIUS - 1.5), 0, RADIUS), "left");
        assertEquals(FrameRing.SHADE, FrameRing.at(0, RADIUS - 1.5, RADIUS), "bottom");
    }
}
