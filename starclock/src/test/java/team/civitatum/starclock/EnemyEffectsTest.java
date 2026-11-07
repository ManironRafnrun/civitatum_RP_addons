package team.civitatum.starclock;

import org.junit.jupiter.api.Test;
import team.civitatum.starclock.prediction.PredictionCatalog;
import static org.junit.jupiter.api.Assertions.*;

class EnemyEffectsTest {
    private static final String POISON = "minecraft:poison";

    /** Нежить невосприимчива к яду, поэтому вариант с ядом по врагам ночью не делает ничего. */
    @Test
    void effectsOnEnemiesAreNotPoison() throws Exception {
        for (var plan : PredictionCatalog.parse(PredictionDefinitionsTest.resources()).allPlans()) {
            for (var action : plan.actions()) {
                var params = action.params();
                if (!params.has("target") || "player".equals(params.get("target").getAsString())
                        || !params.has("effects")) continue;
                for (var effect : params.getAsJsonArray("effects")) {
                    assertNotEquals(POISON, effect.getAsJsonObject().get("id").getAsString(), plan.eventId());
                }
            }
        }
    }
}
