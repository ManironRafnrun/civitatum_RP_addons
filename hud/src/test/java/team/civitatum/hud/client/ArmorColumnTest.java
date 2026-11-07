package team.civitatum.hud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArmorColumnTest {
    @Test
    void tallWindowKeepsTheStepAndCentresTheColumn() {
        // the span at a window height of 224
        int span = 82;
        assertEquals(22, ArmorColumn.offset(1, span) - ArmorColumn.offset(0, span));
        assertEquals(22, ArmorColumn.offset(3, span) - ArmorColumn.offset(2, span));
        assertEquals(span - ArmorColumn.offset(3, span), ArmorColumn.offset(0, span));
    }

    @Test
    void smallWindowFitsTheSlotsIntoItsSpan() {
        int span = 54;
        assertEquals(0, ArmorColumn.offset(0, span));
        assertEquals(span, ArmorColumn.offset(3, span));
    }
}
