package team.civitatum.hud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The frame starts on the whole pixels the map covers, so a map edge between pixels never leaves a gap. */
class MinimapFrameTest {
    @Test
    void wholePixelEdgesStayPut() {
        assertEquals(300, MinimapFrame.innerStart(300.0F));
        assertEquals(300, MinimapFrame.innerEnd(300.0F));
    }

    @Test
    void floatNoiseDoesNotMoveAnEdge() {
        assertEquals(300, MinimapFrame.innerStart(300.0001F));
        assertEquals(300, MinimapFrame.innerEnd(299.9999F));
    }

    @Test
    void edgesBetweenPixelsMoveOntoTheMap() {
        // a map from 300.33 to 400.67: the frame's inner edges at 301 and 400 overlap it by under a pixel
        assertEquals(301, MinimapFrame.innerStart(300.33F));
        assertEquals(400, MinimapFrame.innerEnd(400.67F));
    }
}
