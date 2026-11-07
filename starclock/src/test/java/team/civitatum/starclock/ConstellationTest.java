package team.civitatum.starclock;

import java.util.EnumMap;
import java.util.Random;
import org.junit.jupiter.api.Test;
import team.civitatum.starclock.prediction.Constellation;
import team.civitatum.starclock.prediction.Orientation;
import static org.junit.jupiter.api.Assertions.*;

class ConstellationTest {
    @Test
    void serpentNeverHasAnOrientation() {
        Random random = new Random(178);
        for (int i = 0; i < 1000; i++) {
            assertEquals(Orientation.NONE, Constellation.SERPENT.drawOrientation(random));
        }
    }

    @Test
    void ordinarySignsUseBothOrientationsAndNeverNone() {
        for (Constellation sign : Constellation.values()) {
            if (sign == Constellation.SERPENT) continue;
            Random random = new Random(741);
            int upright = 0;
            for (int i = 0; i < 1000; i++) {
                Orientation orientation = sign.drawOrientation(random);
                assertNotEquals(Orientation.NONE, orientation);
                if (orientation == Orientation.UPRIGHT) upright++;
            }
            assertTrue(upright > 400 && upright < 600);
        }
    }

    @Test
    void allThirteenSignsParticipateInTheSameDraw() {
        assertEquals(13, Constellation.values().length);
        var counts = new EnumMap<Constellation, Integer>(Constellation.class);
        Random random = new Random(4532);
        for (int i = 0; i < 13000; i++) counts.merge(Constellation.draw(random), 1, Integer::sum);
        assertEquals(13, counts.size());
        for (int count : counts.values()) assertTrue(count > 750 && count < 1250);
    }
}
