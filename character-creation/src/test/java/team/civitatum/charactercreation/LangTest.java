package team.civitatum.charactercreation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

class LangTest {
    private static final List<String> RACE_FIELDS =
        List.of("name", "description", "positive", "negative", "ability", "ability_name");

    private static JsonObject lang(String name) throws Exception {
        try (var in = LangTest.class.getResourceAsStream("/assets/civitatum/lang/" + name + ".json")) {
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
    }

    @Test void bothLanguagesHaveTheSameKeysExceptCatalogTexts() throws Exception {
        var ru = lang("ru_ru").keySet();
        var en = new HashSet<>(lang("en_us").keySet());
        // Catalog labels are authored in Russian; only English needs entries for them.
        en.removeIf(key -> key.startsWith("appearance.civitatum.") && !ru.contains(key));
        assertEquals(new TreeSet<>(ru), new TreeSet<>(en));
    }

    @Test void everyRaceAndBirthsignHasItsTexts() throws Exception {
        for (var file : List.of(lang("ru_ru"), lang("en_us"))) {
            for (var race : RaceCatalog.all()) {
                for (String field : RACE_FIELDS) assertTrue(file.has(race.key(field)), race.key(field));
                if (race.hasAbility()) assertFalse(file.get(race.key("ability_name")).getAsString().isBlank(), race.id);
            }
            for (var sign : BirthsignCatalog.all()) {
                for (String field : List.of("name", "description", "effect")) assertTrue(file.has(sign.key(field)));
            }
        }
    }

    /** Minecraft formats every translation: a literal percent sign must be written as "%%". */
    @Test void noLonePercentSign() throws Exception {
        for (var file : List.of(lang("ru_ru"), lang("en_us"))) {
            for (var entry : file.entrySet()) {
                String rest = entry.getValue().getAsString().replaceAll("%%|%(\\d+\\$)?[sd]", "");
                assertFalse(rest.contains("%"), entry.getKey());
            }
        }
    }
}
