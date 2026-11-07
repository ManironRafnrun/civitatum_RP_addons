package team.civitatum.hud.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Label colours drawn light on the restyled screens, and those left alone. */
class LabelColoursTest {
    @Test
    void vanillaLabelGreyIsDark() {
        assertTrue(LabelColours.dark(0x404040));
    }

    @Test
    void blackAndTheGreysModsCopyAreDark() {
        assertTrue(LabelColours.dark(0x000000));
        assertTrue(LabelColours.dark(0x3F3F3F));
        assertTrue(LabelColours.dark(0x555555));
    }

    @Test
    void createsLighterGreysAreDark() {
        assertTrue(LabelColours.dark(0x656565));
        assertTrue(LabelColours.dark(0x7A7A7A));
    }

    @Test
    void darkHueIsDark() {
        assertTrue(LabelColours.dark(0x592424));
    }

    @Test
    void enchantingBrownOnParchmentStays() {
        assertFalse(LabelColours.dark(0x685E4A));
    }

    @Test
    void hintGreyAndLightTextStay() {
        assertFalse(LabelColours.dark(0x808080));
        assertFalse(LabelColours.dark(0xA0A0A0));
        assertFalse(LabelColours.dark(0xFFFFFF));
    }

    @Test
    void alphaIsIgnored() {
        assertTrue(LabelColours.dark(0xFF404040));
    }
}
