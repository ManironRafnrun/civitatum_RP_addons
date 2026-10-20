package team.civitatum.starclock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceReferencesTest {
    private static final Path ROOT = Path.of(System.getProperty("starclock.projectDir"));
    private static final Path STATIC = ROOT.resolve("src/main/resources");
    private static final Path GENERATED = Path.of(System.getProperty("starclock.generatedResources"));

    private static JsonObject json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static Path resource(String directory, String id, String extension) {
        String[] parts = id.split(":", 2);
        String namespace = parts.length == 2 ? parts[0] : "minecraft";
        String path = parts.length == 2 ? parts[1] : parts[0];
        return Path.of("assets", namespace, directory, path + extension);
    }

    @Test
    void allItemModelsResolveParentsAndTextures() throws IOException {
        Path models = STATIC.resolve("assets/civitatum_starclock/models/item");
        try (var files = Files.list(models)) {
            List<Path> entries = files.filter(path -> path.toString().endsWith(".json")).toList();
            assertEquals(13, entries.size());
            for (Path file : entries) {
                String parent = json(file).get("parent").getAsString();
                Path model = STATIC.resolve(resource("models", parent, ".json"));
                assertTrue(Files.isRegularFile(model), model.toString());
                var textureEntries = json(model).getAsJsonObject("textures").entrySet();
                assertFalse(textureEntries.isEmpty());
                for (var texture : textureEntries) {
                    Path relative = resource("textures", texture.getValue().getAsString(), ".png");
                    Path imageFile = Files.isRegularFile(STATIC.resolve(relative))
                            ? STATIC.resolve(relative) : GENERATED.resolve(relative);
                    assertTrue(Files.isRegularFile(imageFile), imageFile.toString());
                    BufferedImage image = ImageIO.read(imageFile.toFile());
                    assertNotNull(image);
                    assertEquals(16, image.getWidth());
                    assertEquals(16, image.getHeight());
                    assertTrue(image.getColorModel().hasAlpha());
                    int visiblePixels = 0;
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            if ((image.getRGB(x, y) >>> 24) != 0) visiblePixels++;
                        }
                    }
                    assertTrue(visiblePixels >= 24 && visiblePixels < 256, imageFile.toString());
                }
            }
        }
    }

    @Test
    void translationsHaveTheSameKeys() throws IOException {
        JsonObject ru = json(STATIC.resolve("assets/civitatum_starclock/lang/ru_ru.json"));
        JsonObject en = json(STATIC.resolve("assets/civitatum_starclock/lang/en_us.json"));
        assertEquals(ru.keySet(), en.keySet());
        for (var entry : ru.entrySet()) assertFalse(entry.getValue().getAsString().isBlank());
        for (var entry : en.entrySet()) assertFalse(entry.getValue().getAsString().isBlank());
    }

    @Test
    void recipesIncludeEveryTierAndUseEchoShardsAsTemplates() throws IOException {
        for (String kind : List.of("deck", "sextant", "bowl", "ring")) {
            for (int tier = 1; tier <= 3; tier++) {
                JsonObject recipe = json(STATIC.resolve("data/civitatum_starclock/recipe/"
                        + kind + "_" + tier + ".json"));
                assertEquals("civitatum_starclock:" + kind + "_" + tier,
                        recipe.getAsJsonObject("result").get("id").getAsString());
                if (tier == 3) {
                    assertEquals("minecraft:echo_shard",
                            recipe.getAsJsonObject("template").get("item").getAsString());
                    assertEquals("minecraft:netherite_ingot",
                            recipe.getAsJsonObject("addition").get("item").getAsString());
                }
            }
        }
    }
}
