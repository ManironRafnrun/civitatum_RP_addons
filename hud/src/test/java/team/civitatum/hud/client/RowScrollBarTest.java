package team.civitatum.hud.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RowScrollBarTest {
    @Test
    void nothingToScrollDrawsNothing() {
        assertFalse(new RowScrollBar(0, 0, 54, 0, 3, 3).scrollable());
        assertFalse(new RowScrollBar(0, 0, 54, 0, 4, 3).scrollable());
        assertTrue(new RowScrollBar(0, 0, 54, 0, 3, 4).scrollable());
    }

    @Test
    void thumbMovesFromTopToBottom() {
        // 3 of 9 rows visible on a 54 px track: a third of the track, three positions apart
        assertEquals(18, new RowScrollBar(0, 0, 54, 0, 3, 9).thumb());
        assertEquals(0, new RowScrollBar(0, 0, 54, 0, 3, 9).offset());
        assertEquals(18, new RowScrollBar(0, 0, 54, 3, 3, 9).offset());
        assertEquals(36, new RowScrollBar(0, 0, 54, 6, 3, 9).offset());
    }

    @Test
    void everyRowStepMovesTheThumb() {
        int previous = -1;
        for (int first = 0; first <= 27 - 3; first++) {
            int offset = new RowScrollBar(0, 0, 54, first, 3, 27).offset();
            assertTrue(offset > previous, "row " + first);
            previous = offset;
        }
    }

    @Test
    void longListsKeepAVisibleThumb() {
        assertEquals(4, new RowScrollBar(0, 0, 54, 0, 1, 100).thumb());
        assertEquals(50, new RowScrollBar(0, 0, 54, 99, 1, 100).offset());
    }
}
