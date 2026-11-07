package team.civitatum.hud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SidebarRowTest {
    @Test
    void lastIconEndsAtTheResetButtonsEdge() {
        int screen = 480, columns = 8;
        int lastIconRight = SidebarRow.left(screen, columns) + (columns - 1) * SidebarRow.CELL + 16;
        assertEquals(screen - SidebarRow.RIGHT_MARGIN, lastIconRight);
    }

    @Test
    void rowSitsAboveTheBottomEdge() {
        assertEquals(270 - 6 - 16, SidebarRow.top(270));
    }
}
