package team.civitatum.hud.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TrashCellTest {
    @AfterEach
    void hide() {
        TrashCell.hide();
    }

    @Test
    void theCellTakesItsOwnSquare() {
        TrashCell.show(100, 50, 18);
        assertTrue(TrashCell.inside(100, 50));
        assertTrue(TrashCell.inside(117, 67));
        assertFalse(TrashCell.inside(118, 60));
        assertFalse(TrashCell.inside(110, 49));
        assertArrayEquals(new int[]{100, 50, 18, 18}, TrashCell.rectangle());
    }

    @Test
    void aHiddenCellTakesNoClick() {
        TrashCell.hide();
        assertFalse(TrashCell.inside(0, 0));
    }
}
