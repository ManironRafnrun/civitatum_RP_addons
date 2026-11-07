package team.civitatum.hud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WheelStepsTest {
    @Test
    void wholeNotchesMoveAtOnce() {
        var wheel = new WheelSteps();
        assertEquals(1, wheel.rows(1));
        assertEquals(-2, wheel.rows(-2));
        assertEquals(3, wheel.rows(3.4));
    }

    @Test
    void fractionsAddUpToARow() {
        var wheel = new WheelSteps();
        assertEquals(0, wheel.rows(0.4));
        assertEquals(0, wheel.rows(0.4));
        assertEquals(1, wheel.rows(0.4));
        assertEquals(0, wheel.rows(-0.4));
        assertEquals(-1, wheel.rows(-0.7));
    }

    @Test
    void turningBackDropsTheOtherWaysRemainder() {
        var wheel = new WheelSteps();
        assertEquals(0, wheel.rows(0.9));
        assertEquals(-1, wheel.rows(-1));
    }
}
