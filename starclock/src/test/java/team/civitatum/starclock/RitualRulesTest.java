package team.civitatum.starclock;

import org.junit.jupiter.api.Test;
import team.civitatum.starclock.ritual.RitualRules;
import static org.junit.jupiter.api.Assertions.*;

class RitualRulesTest {
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
