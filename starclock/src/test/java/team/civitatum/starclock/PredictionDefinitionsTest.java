package team.civitatum.starclock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import team.civitatum.starclock.prediction.*;
import static org.junit.jupiter.api.Assertions.*;

class PredictionDefinitionsTest {
    static Map<String, JsonObject> resources() throws Exception {
        Map<String, JsonObject> result = new HashMap<>();
        Path root = Path.of(System.getProperty("starclock.projectDir"), "src/main/resources/data/starclock/predictions");
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                result.put("starclock:" + file.getFileName().toString().replace(".json", ""), json);
            }
        }
        return result;
    }

    @Test
    void everyDocumentedVariantIsCompleteAndValid() throws Exception {
        var catalog = PredictionCatalog.parse(resources());
        assertEquals(32, catalog.ids().size());
        assertEquals(88, catalog.allPlans().size());
        for (var plan : catalog.allPlans()) {
            plan.actions().forEach(ActionValidator::validate);
            assertFalse(plan.actions().stream().anyMatch(action -> action.type().equals("block")));
            var expanded = PredictionBonuses.append(plan);
            assertEquals(plan.actions().size() + 1, expanded.actions().size());
            expanded.actions().forEach(ActionValidator::validate);
            var bonus = expanded.actions().getLast();
            assertFalse(bonus.params().get("scale").getAsBoolean());
        }
    }

    @Test
    void ordinarySignsNeverLeakIntoTheOtherOrientation() throws Exception {
        var catalog = PredictionCatalog.parse(resources());
        Random random = new Random(784);
        for (var sign : Constellation.values()) {
            if (sign == Constellation.SERPENT) continue;
            for (var orientation : java.util.List.of(Orientation.UPRIGHT, Orientation.REVERSED)) {
                for (int i = 0; i < 100; i++) {
                    var plan = catalog.draw(sign, orientation, random);
                    assertEquals(sign, plan.sign());
                    assertEquals(orientation, plan.orientation());
                }
            }
        }
    }

    @Test
    void serpentUsesOneBalancedPoolOfEightRows() throws Exception {
        var catalog = PredictionCatalog.parse(resources());
        Map<String, Integer> counts = new HashMap<>();
        int positive = 0;
        Random random = new Random(536);
        for (int i = 0; i < 16000; i++) {
            var plan = catalog.draw(Constellation.SERPENT, Orientation.NONE, random);
            assertEquals(Orientation.NONE, plan.orientation());
            counts.merge(plan.eventId(), 1, Integer::sum);
            if (plan.positive()) positive++;
        }
        assertEquals(8, counts.size());
        for (int count : counts.values()) assertTrue(count > 1700 && count < 2300);
        assertTrue(positive > 7400 && positive < 8600);
    }

    @Test
    void incompleteTablesCannotReplaceTheCatalog() throws Exception {
        var map = resources();
        map.remove("starclock:spear_upright");
        assertThrows(IllegalArgumentException.class, () -> PredictionCatalog.parse(map));
    }

    @Test
    void towerKeyVariantGrantsAnAbilityInsteadOfAnItem() throws Exception {
        var plan = PredictionCatalog.parse(resources()).get("starclock:tower_upright", 1);
        assertEquals(1, plan.actions().size());
        assertEquals("effect", plan.actions().getFirst().type());
        assertEquals("tower_key", plan.actions().getFirst().params().get("ability").getAsString());
    }

    @Test
    void allThirteenLootTablesExistAndUseValidJson() throws Exception {
        Path root = Path.of(System.getProperty("starclock.projectDir"), "src/main/resources/data/starclock/loot_table/sign");
        try (var files = Files.list(root)) {
            var entries = files.filter(path -> path.toString().endsWith(".json")).toList();
            assertEquals(13, entries.size());
            for (Path entry : entries) {
                assertFalse(JsonParser.parseString(Files.readString(entry)).getAsJsonObject()
                        .getAsJsonArray("pools").isEmpty());
            }
        }
    }
}
