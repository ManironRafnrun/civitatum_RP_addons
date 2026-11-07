package team.civitatum.starclock;

import java.util.Random;
import org.junit.jupiter.api.Test;
import team.civitatum.starclock.prediction.PredictionTimer;
import static org.junit.jupiter.api.Assertions.*;

class PredictionTimerTest {
    @Test
    void generatedTimersRespectBothTierRanges() {
        Random random = new Random(4712);
        for (int tier = 1; tier <= 3; tier++) {
            for (int i = 0; i < 10000; i++) {
                long value = PredictionTimer.choose(random, tier, 6000, 96000, 72000);
                assertTrue(value >= 6000 && value <= (tier == 3 ? 72000 : 96000));
            }
        }
    }

    @Test
    void singleValueIntervalsIncludeTheirOnlyValue() {
        assertEquals(6000, PredictionTimer.choose(new Random(1), 1, 6000, 6000, 6000));
        assertEquals(72000, PredictionTimer.choose(new Random(1), 3, 72000, 96000, 72000));
    }

    @Test
    void incompatibleBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PredictionTimer.validate(80000, 96000, 72000));
        assertThrows(IllegalArgumentException.class, () -> PredictionTimer.validate(0, 96000, 72000));
        assertThrows(IllegalArgumentException.class, () -> PredictionTimer.validate(100, 50, 100));
    }
}
