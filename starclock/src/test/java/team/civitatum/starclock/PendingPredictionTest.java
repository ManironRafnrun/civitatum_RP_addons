package team.civitatum.starclock;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import team.civitatum.starclock.data.PendingPrediction;
import team.civitatum.starclock.prediction.*;
import static org.junit.jupiter.api.Assertions.*;

class PendingPredictionTest {
    private PendingPrediction pending() throws Exception {
        var plan = PredictionCatalog.parse(PredictionDefinitionsTest.resources()).get("starclock:beast_upright", 0);
        return new PendingPrediction(UUID.fromString("684a801b-1c54-45f0-89d2-ce7045da3dda"),
                UUID.fromString("7b3f1c0f-1d94-4f92-bbd2-fd3af56346d8"),
                "minecraft:overworld", -23, 81, 413, 50000, 3,
                PredictionBonuses.append(plan), false, 0, 0, 0);
    }

    @Test
    void roundTripPreservesTheSelectedEventAndBonus() throws Exception {
        var value = pending().waiting().advance().retry(60000);
        assertEquals(value, PendingPrediction.fromJson(value.toJson()));
        assertEquals(value.plan().actions(), PendingPrediction.fromJson(value.toJson()).plan().actions());
    }

    @Test
    void overdueOfflinePredictionRemainsDueAfterRestart() throws Exception {
        var restored = PendingPrediction.fromJson(pending().waiting().toJson());
        assertTrue(restored.waited());
        assertFalse(restored.isDue(49999));
        assertTrue(restored.isDue(50000));
        assertTrue(restored.isDue(2000000));
    }

    @Test
    void retryDelaysDoNotRerollOrRepeatCompletedActions() throws Exception {
        var value = pending().advance().retry(60000);
        assertEquals(1, value.nextAction());
        assertFalse(value.isDue(60000));
        assertTrue(value.isDue(value.retryAfter()));
        assertEquals(pending().plan(), value.plan());
    }

    @Test
    void actionParametersCannotBeChangedThroughThePublicView() throws Exception {
        var value = pending();
        var copy = value.plan().actions().getFirst().params();
        copy.addProperty("count", 1000);
        assertNotEquals(1000, value.plan().actions().getFirst().params().get("count").getAsInt());
    }
}
