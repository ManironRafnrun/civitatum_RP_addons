package team.civitatum.starclock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeoResourcesTest {
    private static final Path ASSETS = Path.of(System.getProperty("starclock.projectDir"))
            .resolve("src/main/resources/assets/starclock");
    private static final Path TEXTURES = Path.of(System.getProperty("starclock.generatedResources"))
            .resolve("assets/starclock/textures/model");
    private static final List<String> TOOLS = List.of("deck", "sextant", "bowl", "ring");

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test
    void modelsHaveConnectedBonesValidFacesAndRealAtlases() throws IOException {
        for (String kind : TOOLS) {
            var image = ImageIO.read(TEXTURES.resolve(kind + ".png").toFile());
            assertNotNull(image, kind);
            assertEquals(64, image.getWidth(), kind);
            assertEquals(64, image.getHeight(), kind);
            var geometry = json(ASSETS.resolve("geo/" + kind + ".geo.json"))
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            var description = geometry.getAsJsonObject("description");
            assertEquals(64, description.get("texture_width").getAsInt());
            assertEquals(64, description.get("texture_height").getAsInt());
            Set<String> names = new HashSet<>();
            for (JsonElement entry : geometry.getAsJsonArray("bones")) {
                var bone = entry.getAsJsonObject();
                String name = bone.get("name").getAsString();
                assertTrue(names.add(name), kind + ": повтор кости " + name);
                if (bone.has("parent")) assertTrue(names.contains(bone.get("parent").getAsString()), name);
                if (!bone.has("cubes")) continue;
                for (JsonElement cubeEntry : bone.getAsJsonArray("cubes")) {
                    var cube = cubeEntry.getAsJsonObject();
                    assertEquals(6, cube.getAsJsonObject("uv").size(), name);
                    for (var face : cube.getAsJsonObject("uv").entrySet()) {
                        JsonArray uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                        JsonArray size = face.getValue().getAsJsonObject().getAsJsonArray("uv_size");
                        for (int axis = 0; axis < 2; axis++) {
                            double start = uv.get(axis).getAsDouble();
                            double end = start + size.get(axis).getAsDouble();
                            assertTrue(start >= 0 && start < 64 && end > start && end <= 64,
                                    kind + "/" + name + "/" + face.getKey());
                        }
                    }
                }
            }
            assertTrue(names.containsAll(Set.of("hand", "hand_left", "hand_right")));
        }
    }

    @Test
    void everyAnimationUsesExistingBonesAndFortyTickTimelines() throws IOException {
        for (String kind : TOOLS) {
            var geometry = json(ASSETS.resolve("geo/" + kind + ".geo.json"))
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            Set<String> names = new HashSet<>();
            geometry.getAsJsonArray("bones").forEach(entry ->
                    names.add(entry.getAsJsonObject().get("name").getAsString()));
            var file = json(ASSETS.resolve("animations/" + kind + ".animation.json"));
            assertEquals("1.8.0", file.get("format_version").getAsString());
            var animation = file.getAsJsonObject("animations").getAsJsonObject("animation." + kind + ".ritual");
            assertEquals(2.0, animation.get("animation_length").getAsDouble());
            assertEquals("hold_on_last_frame", animation.get("loop").getAsString());
            for (var bone : animation.getAsJsonObject("bones").entrySet()) {
                assertTrue(names.contains(bone.getKey()), kind + "/" + bone.getKey());
                for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                    double previous = -1;
                    for (var frame : channel.getValue().getAsJsonObject().entrySet()) {
                        double time = Double.parseDouble(frame.getKey());
                        assertTrue(time > previous && time >= 0 && time <= 2, kind + "/" + frame.getKey());
                        previous = time;
                        var vector = frame.getValue().getAsJsonObject().getAsJsonArray("vector");
                        assertEquals(3, vector.size());
                        vector.forEach(value -> assertTrue(Double.isFinite(value.getAsDouble())));
                    }
                    assertEquals(2.0, previous);
                }
            }
            assertFalse(animation.getAsJsonObject("sound_effects").isEmpty());
            assertFalse(animation.getAsJsonObject("particle_effects").isEmpty());
            for (var particle : animation.getAsJsonObject("particle_effects").entrySet()) {
                assertTrue(names.contains(particle.getValue().getAsJsonObject().get("locator").getAsString()));
            }
        }
    }
}
