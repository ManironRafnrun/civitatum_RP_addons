package team.civitatum.hud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Icons of 18 px, 19 px apart, rows of 7 growing away from the minimap. */
class EffectsPlacementTest {
    /** Right edge of a block left of a minimap frame at x = 300. */
    private static final int EDGE = 300 - EffectsPlacement.GAP;

    @Test
    void firstIconEndsTwoPixelsLeftOfTheMap() {
        assertEquals(300 - EffectsPlacement.GAP, EffectsPlacement.x(true, EDGE, 0) + EffectsPlacement.SIZE);
    }

    @Test
    void iconsGrowFromRightToLeftWithOnePixelBetween() {
        int first = EffectsPlacement.x(true, EDGE, 0);
        int second = EffectsPlacement.x(true, EDGE, 1);
        assertEquals(1, first - (second + EffectsPlacement.SIZE));
    }

    @Test
    void eighthIconStartsTheNextRowUnderTheFirst() {
        assertEquals(EffectsPlacement.x(true, EDGE, 0), EffectsPlacement.x(true, EDGE, 7));
        assertEquals(EffectsPlacement.y(10, 0, 0) + EffectsPlacement.ROW_STEP, EffectsPlacement.y(10, 0, 7));
        assertEquals(EffectsPlacement.y(10, 0, 0), EffectsPlacement.y(10, 0, 6));
    }

    @Test
    void harmfulEffectsStartBelowAllBeneficialRows() {
        assertEquals(0, EffectsPlacement.rows(0));
        assertEquals(1, EffectsPlacement.rows(7));
        assertEquals(2, EffectsPlacement.rows(8));
        assertEquals(10 + 2 * EffectsPlacement.ROW_STEP, EffectsPlacement.y(10, EffectsPlacement.rows(8), 0));
    }

    @Test
    void blockIsAtMostSevenIconsWide() {
        assertEquals(0, EffectsPlacement.blockWidth(0));
        assertEquals(18, EffectsPlacement.blockWidth(1));
        assertEquals(7 * 19 - 1, EffectsPlacement.blockWidth(7));
        assertEquals(EffectsPlacement.blockWidth(7), EffectsPlacement.blockWidth(20));
    }

    @Test
    void blockThatExactlyFitsStaysOnTheLeft() {
        int mapX = EffectsPlacement.GAP + EffectsPlacement.blockWidth(3);
        assertTrue(EffectsPlacement.fitsLeftOf(mapX, 3, 1));
        assertEquals(0, EffectsPlacement.x(true, mapX - EffectsPlacement.GAP, 2));
        assertFalse(EffectsPlacement.fitsLeftOf(mapX - 1, 3, 1));
    }

    @Test
    void scaledBlockNeedsScaledRoom() {
        int width = EffectsPlacement.blockWidth(3);
        int mapX = EffectsPlacement.GAP + 2 * width;
        assertTrue(EffectsPlacement.fitsLeftOf(mapX, 3, 2));
        assertFalse(EffectsPlacement.fitsLeftOf(mapX - 1, 3, 2));
        assertTrue(EffectsPlacement.fitsLeftOf(EffectsPlacement.GAP + width / 2, 3, 0.5F));
    }

    @Test
    void onTheRightTheRowsGrowAwayFromTheMap() {
        int edge = 130 + EffectsPlacement.GAP;
        assertEquals(edge, EffectsPlacement.x(false, edge, 0));
        assertEquals(edge + EffectsPlacement.STEP, EffectsPlacement.x(false, edge, 1));
    }
}
