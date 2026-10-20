package team.civitatum.starclock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SoundResourcesTest {
    private static final Path STATIC = Path.of(System.getProperty("starclock.projectDir")).resolve("src/main/resources");
    private static final Path GENERATED = Path.of(System.getProperty("starclock.generatedSounds"));
    private static final List<String> NAMES = List.of("shuffle", "sextant_click", "water_pour", "ring_hum",
            "stars_reveal", "card_flip", "omen_good", "omen_bad");

    @Test
    void soundDefinitionsResolveToVorbisAndExistingSubtitles() throws Exception {
        JsonObject definitions = JsonParser.parseString(Files.readString(
                STATIC.resolve("assets/civitatum_starclock/sounds.json"))).getAsJsonObject();
        assertEquals(NAMES.size(), definitions.size());
        for (String lang : List.of("ru_ru", "en_us")) {
            var translations = JsonParser.parseString(Files.readString(
                    STATIC.resolve("assets/civitatum_starclock/lang/" + lang + ".json"))).getAsJsonObject();
            for (String name : NAMES) {
                var definition = definitions.getAsJsonObject(name);
                assertTrue(translations.has(definition.get("subtitle").getAsString()));
                String id = definition.getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString();
                String[] parts = id.split(":", 2);
                byte[] data = Files.readAllBytes(GENERATED.resolve("assets/" + parts[0] + "/sounds/" + parts[1] + ".ogg"));
                assertEquals("OggS", new String(data, 0, 4, StandardCharsets.US_ASCII), name);
                int packet = 27 + Byte.toUnsignedInt(data[26]);
                assertEquals(1, data[packet], name);
                assertEquals("vorbis", new String(data, packet + 1, 6, StandardCharsets.US_ASCII), name);
                assertEquals(1, data[packet + 11], name);
                assertEquals(44100, ByteBuffer.wrap(data, packet + 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt(), name);
            }
        }
    }

    @Test
    void allAnimationSoundEffectsResolveToRegisteredDefinitions() throws Exception {
        var definitions = JsonParser.parseString(Files.readString(
                STATIC.resolve("assets/civitatum_starclock/sounds.json"))).getAsJsonObject();
        for (String kind : List.of("deck", "sextant", "bowl", "ring")) {
            var file = JsonParser.parseString(Files.readString(
                    STATIC.resolve("assets/starclock/animations/" + kind + ".animation.json"))).getAsJsonObject();
            for (var animation : file.getAsJsonObject("animations").entrySet()) {
                for (var sound : animation.getValue().getAsJsonObject().getAsJsonObject("sound_effects").entrySet()) {
                    String id = sound.getValue().getAsJsonObject().get("effect").getAsString();
                    assertTrue(id.startsWith("civitatum_starclock:"), id);
                    assertTrue(definitions.has(id.substring(id.indexOf(':') + 1)), id);
                }
            }
        }
    }
}
