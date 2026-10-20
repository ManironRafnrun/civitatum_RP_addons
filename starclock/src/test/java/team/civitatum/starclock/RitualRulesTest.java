package team.civitatum.starclock;

import org.junit.jupiter.api.Test;
import team.civitatum.starclock.ritual.RitualRules;
import static org.junit.jupiter.api.Assertions.*;

class RitualRulesTest {
    @Test
    void freshWorldDoesNotHaveACooldown() {
        assertEquals(0, RitualRules.remainingCooldown(-1, 0));
        assertEquals(0, RitualRules.remainingCooldown(-1, 14000));
    }

    @Test
    void cooldownExpiresAtExactlyEightDays() {
        long last = 15000;
        assertEquals(192000, RitualRules.remainingCooldown(last, last));
        assertEquals(1, RitualRules.remainingCooldown(last, last + 191999));
        assertEquals(0, RitualRules.remainingCooldown(last, last + 192000));
        assertEquals(0, RitualRules.remainingCooldown(last, last + 500000));
    }

    @Test
    void earlierClockDoesNotBypassCooldown() {
        assertEquals(192000, RitualRules.remainingCooldown(20000, 100));
    }

    @Test
    void nightHasInclusiveBoundariesAndRepeatsEveryDay() {
        assertFalse(RitualRules.isNight(12999));
        assertTrue(RitualRules.isNight(13000));
        assertTrue(RitualRules.isNight(23000));
        assertFalse(RitualRules.isNight(23001));
        assertTrue(RitualRules.isNight(13000 + 24000L * 1000));
        assertFalse(RitualRules.isNight(12000 + 24000L * 1000));
    }
}
