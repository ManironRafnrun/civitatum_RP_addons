package team.civitatum.charactercreation.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Generates the beard and mustache textures, {@code textures/character/facial_hair/<name>.png}: 64x64 skin overlays
 * in grey, which the mod tints with the player's hair colour.
 *
 * <p>Input: {@code input/facial_hair/facial_hair.json}. Every style is drawn from rows of characters, one per pixel:
 * {@code .} is transparent, a digit 2-6 is a shade of grey ({@code digit * 25 + 35}, darker to lighter). The face rows
 * fill the bottom of the face, the chest rows go down the front of the body; beards with {@code "jaw"} also get the
 * sides of the face and the underside of the jaw shaded.
 *
 * <p>Run with {@code ./gradlew :character-creation:generateFacialHair}, or start {@link #main} from the IDE in the
 * {@code character-creation} module folder.
 */
public final class FacialHairTexturesGenerator {
    private static final Path INPUT = Generation.INPUT.resolve("facial_hair");
    private static final Path OUTPUT = Generation.TEXTURES.resolve("facial_hair");

    /** The face of the head is the 8x8 square at (8, 8); the front of the body starts at (20, 20). */
    private static final int FACE_LEFT = 8, FACE_BOTTOM = 16, CHEST_LEFT = 20, CHEST_TOP = 20;

    private FacialHairTexturesGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Generation.checkWorkingDirectory();
        JsonObject meta = Generation.readJson(INPUT.resolve("facial_hair.json"));

        Generation.clean(OUTPUT);
        JsonArray styles = meta.getAsJsonArray("styles");
        for (JsonElement element : styles) {
            JsonObject style = element.getAsJsonObject();
            texture(style).write(OUTPUT.resolve(style.get("name").getAsString() + ".png"));
        }
        System.out.println("Generated " + styles.size() + " beards and mustaches in " + OUTPUT);
    }

    private static Texture texture(JsonObject style) {
        Texture image = Texture.empty();
        JsonArray face = style.getAsJsonArray("face");
        int faceAlpha = style.has("face_alpha") ? style.get("face_alpha").getAsInt() : 255;
        draw(image, face, FACE_LEFT, FACE_BOTTOM - face.size(), faceAlpha);
        if (style.has("jaw") && style.get("jaw").getAsBoolean()) {
            // sides of the face, on the neighbouring faces of the head
            for (int x : new int[] {7, 16}) {
                for (int y = 12; y < 16; y++) {
                    put(image, x, y, 110 + (x + y) % 3 * 25, 255);
                }
            }
            // underside of the jaw, on the bottom face of the head
            for (int y = 1; y < 7; y++) {
                for (int x = 17; x < 23; x++) {
                    put(image, x, y, 90 + (x * 3 + y * 7) % 5 * 24, 255);
                }
            }
        }
        if (style.has("chest")) {
            draw(image, style.getAsJsonArray("chest"), CHEST_LEFT, CHEST_TOP, 255);
        }
        return image;
    }

    private static void draw(Texture image, JsonArray rows, int left, int top, int alpha) {
        for (int y = 0; y < rows.size(); y++) {
            String row = rows.get(y).getAsString();
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                if (c == '.') {
                    continue;
                }
                if (c < '2' || c > '6') {
                    throw new IllegalArgumentException("Unknown shade '" + c + "' in row \"" + row + "\"");
                }
                put(image, left + x, top + y, (c - '0') * 25 + 35, alpha);
            }
        }
    }

    private static void put(Texture image, int x, int y, int grey, int alpha) {
        image.set(x, y, alpha << 24 | grey << 16 | grey << 8 | grey);
    }
}
