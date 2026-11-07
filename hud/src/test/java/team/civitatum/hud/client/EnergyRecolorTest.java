package team.civitatum.hud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** ParCool's icon colours, sampled from ParCool 1.21.1-4.0.1.2, land on the stops of the HUD ramps. */
class EnergyRecolorTest {
    @Test
    void outlineBecomesTheFrameShadow() {
        assertEquals(0xFF54422C, EnergyRecolor.recolor(0xFF606000, EnergyRecolor.Ramp.GOLD));
    }

    @Test
    void plainColourBecomesTheRampColour() {
        assertEquals(0xFFB29A6B, EnergyRecolor.recolor(0xFFFEFE00, EnergyRecolor.Ramp.GOLD));
        assertEquals(0xFFAE4236, EnergyRecolor.recolor(0xFFFE0000, EnergyRecolor.Ramp.RED));
        // ParCool's blue is 0x0080FF: half a step lighter than the stop.
        assertNear(0xFF496FAD, EnergyRecolor.recolor(0xFF0080FF, EnergyRecolor.Ramp.BLUE));
    }

    private static void assertNear(int expected, int actual) {
        for (int shift = 0; shift < 32; shift += 8) {
            int e = expected >> shift & 0xFF, a = actual >> shift & 0xFF;
            if (Math.abs(e - a) > 2) assertEquals(Integer.toHexString(expected), Integer.toHexString(actual));
        }
    }

    @Test
    void whiteStaysTheLightestStop() {
        assertEquals(0xFFEEF2FA, EnergyRecolor.recolor(0xFFFFFFFF, EnergyRecolor.Ramp.BLUE));
    }

    @Test
    void alphaIsKept() {
        assertEquals(0x00000000, EnergyRecolor.recolor(0x00000000, EnergyRecolor.Ramp.GOLD));
        assertEquals(0x80, EnergyRecolor.recolor(0x80FEFE00, EnergyRecolor.Ramp.GOLD) >>> 24);
    }

    @Test
    void stateIsReadFromTheSpriteName() {
        assertEquals(EnergyRecolor.Ramp.GOLD, EnergyRecolor.Ramp.of("hud/stamina_full"));
        assertEquals(EnergyRecolor.Ramp.GOLD, EnergyRecolor.Ramp.of("hud/stamina_flush"));
        assertEquals(EnergyRecolor.Ramp.RED, EnergyRecolor.Ramp.of("hud/stamina_exhausted_half"));
        assertEquals(EnergyRecolor.Ramp.BLUE, EnergyRecolor.Ramp.of("hud/stamina_inexhaustible_empty"));
    }
}
