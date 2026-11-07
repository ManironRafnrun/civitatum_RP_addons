package team.civitatum.hud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InventoryPanelTest {
    /** A window of its usual height at 1080p and the automatic GUI scale, 480 by 270 units. */
    private static InventoryPanel usual() {
        return new InventoryPanel("items", 100, 28, 214, 216, 186, 164);
    }

    @Test
    void movesVerticallyOnASmallScreen() {
        var panel = usual();
        panel.y = 1000;
        panel.constrain(480, 270);
        assertEquals(270 - 216 - InventoryPanel.MARGIN, panel.y);
        panel.y = 0;
        panel.constrain(480, 270);
        assertEquals(InventoryPanel.TOP_BAR, panel.y);
    }
}
