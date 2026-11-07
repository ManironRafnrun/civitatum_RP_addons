package team.civitatum.starclock.discord;

import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import team.civitatum.starclock.prediction.Constellation;
import team.civitatum.starclock.prediction.Orientation;
import static org.junit.jupiter.api.Assertions.*;

class DiscordMessageTest {
    private static PredictionMessage.Card card(Constellation sign, Orientation orientation, String tier,
                                               String category, String target) {
        return new PredictionMessage.Card(sign, orientation, tier, category, target);
    }

    private static String description(String json) {
        return JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("embeds").get(0).getAsJsonObject()
                .get("description").getAsString();
    }

    @Test
    void messageHasBothLanguagesImageAndNoMentions() {
        var json = PredictionMessage.read("Some_One",
                card(Constellation.BEAST, Orientation.UPRIGHT, "I", "", ""));
        var message = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(message.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty());
        assertEquals("attachment://" + PredictionMessage.IMAGE_NAME, message.getAsJsonArray("embeds").get(0)
                .getAsJsonObject().getAsJsonObject("image").get("url").getAsString());
        var text = description(json);
        for (String part : new String[] {"**Зверь** — Прямое положение", "Стая и чутьё.", "Игрок: Some\\_One",
                "**Beast**", "Player: Some\\_One"}) {
            assertTrue(text.contains(part), part + " нет в: " + text);
        }
        assertFalse(text.contains("мин"), "Срок не публикуется: " + text);
    }

    @Test
    void messageShowsOnlyWhatTheCardOfThisTierShows() {
        var first = description(PredictionMessage.read("A",
                card(Constellation.BEAST, Orientation.REVERSED, "I", "", "")));
        assertFalse(first.contains("Событие:"));
        assertFalse(first.contains("Цель:"));
        var third = description(PredictionMessage.read("Reader",
                card(Constellation.BEAST, Orientation.REVERSED, "III", "spawn", "player")));
        assertTrue(third.contains("Событие: Призыв"));
        // На карте здесь «Вы», но в общем канале это было бы неверно.
        assertTrue(third.contains("Цель: Reader"));
        var world = description(PredictionMessage.read("Reader",
                card(Constellation.BEAST, Orientation.REVERSED, "III", "effect", "nearby_wolf")));
        assertTrue(world.contains("Цель: Ближайший волк"));
    }

    @Test
    void serpentHasNoOrientation() {
        var text = description(PredictionMessage.read("A",
                card(Constellation.SERPENT, Orientation.NONE, "I", "", "")));
        assertTrue(text.startsWith("**Змей**\n"));
        assertFalse(text.contains("положение"));
    }

    @Test
    void cancellationHasCountInBothLanguagesAndNoImage() {
        var json = PredictionMessage.cancelled(3);
        var message = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(message.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").isEmpty());
        assertFalse(message.getAsJsonArray("embeds").get(0).getAsJsonObject().has("image"));
        var text = description(json);
        assertTrue(text.contains("Отменено: 3."), text);
        assertTrue(text.contains("Cancelled: 3."), text);
    }

    @Test
    void everyCardRendersAtTextureSize() throws Exception {
        for (var sign : Constellation.values()) {
            var orientations = sign == Constellation.SERPENT ? new Orientation[] {Orientation.NONE}
                    : new Orientation[] {Orientation.UPRIGHT, Orientation.REVERSED};
            for (var orientation : orientations) {
                var image = ImageIO.read(new ByteArrayInputStream(CardImage.render(sign, orientation)));
                assertEquals(256, image.getWidth());
                assertEquals(384, image.getHeight());
            }
        }
    }

    @Test
    void reversedCardDiffersFromUpright() {
        assertFalse(Arrays.equals(CardImage.render(Constellation.BEAST, Orientation.UPRIGHT),
                CardImage.render(Constellation.BEAST, Orientation.REVERSED)));
    }

    @Test
    void multipartKeepsImageBytesIntact() {
        byte[] image = {(byte) 0x89, 'P', 'N', 'G', 0, (byte) 0xFF};
        byte[] body = WebhookClient.multipart("b", "{}", image, "card.png");
        var text = new String(body, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("--b\r\n"));
        assertTrue(text.contains("name=\"payload_json\""));
        assertTrue(text.contains("name=\"files[0]\"; filename=\"card.png\""));
        assertTrue(text.contains(new String(image, StandardCharsets.ISO_8859_1) + "\r\n--b--\r\n"));
    }
}
