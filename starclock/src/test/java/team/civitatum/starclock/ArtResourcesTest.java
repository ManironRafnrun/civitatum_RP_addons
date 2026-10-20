package team.civitatum.starclock;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import team.civitatum.starclock.prediction.Constellation;
import static org.junit.jupiter.api.Assertions.*;

class ArtResourcesTest {
    private static final Path ROOT = Path.of(System.getProperty("starclock.generatedResources"));
    private static final Path TEXTURES = ROOT.resolve("assets/starclock/textures");

    private void dimensions(String name, int width, int height) throws IOException {
        var image = ImageIO.read(TEXTURES.resolve(name + ".png").toFile());
        assertNotNull(image, name);
        assertEquals(width, image.getWidth(), name);
        assertEquals(height, image.getHeight(), name);
        assertTrue(image.getColorModel().hasAlpha(), name);
    }

    @Test
    void everySignHasTransparentArtworkAndAnOriginalSource() throws IOException {
        var report = JsonParser.parseString(Files.readString(ROOT.resolve("starclock-art.json"))).getAsJsonObject();
        var originals = report.getAsJsonObject("figures");
        assertEquals(13, originals.size());
        var hashes = new HashSet<String>();
        for (var sign : Constellation.values()) {
            String name = "constellation/" + sign.id();
            dimensions(name, 512, 512);
            var source = originals.getAsJsonObject(sign.id());
            assertTrue(source.get("source").getAsString().endsWith(".png"));
            assertTrue(hashes.add(source.get("sha256").getAsString()), "Повтор оригинала " + sign);
            var image = ImageIO.read(TEXTURES.resolve(name + ".png").toFile());
            int visible = 0;
            for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++)
                if ((image.getRGB(x, y) >>> 24) > 32) visible++;
            assertTrue(visible > 350 && visible < 100000, name + ": " + visible);
            assertEquals(0, image.getRGB(0, 0) >>> 24);
        }
    }

    @Test
    void cardsAndInterfaceTexturesHaveExpectedSizes() throws IOException {
        for (String name : List.of("card_front", "card_back", "card_zmei")) dimensions("gui/" + name, 256, 384);
        dimensions("gui/starfield", 512, 512);
        dimensions("particle/star", 8, 8);
        for (String name : List.of("upright", "reversed", "sky", "timer")) dimensions("gui/" + name, 16, 16);
        byte[] front = Files.readAllBytes(TEXTURES.resolve("gui/card_front.png"));
        assertFalse(java.util.Arrays.equals(front, Files.readAllBytes(TEXTURES.resolve("gui/card_back.png"))));
        assertFalse(java.util.Arrays.equals(front, Files.readAllBytes(TEXTURES.resolve("gui/card_zmei.png"))));
    }
}
