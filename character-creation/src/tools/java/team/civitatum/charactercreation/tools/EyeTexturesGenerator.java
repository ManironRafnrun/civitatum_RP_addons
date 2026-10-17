package team.civitatum.charactercreation.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates the eyes drawn on top of the skin, {@code textures/character/eyes/<model>/}:
 * <ul>
 *   <li>{@code original.png} - the eye pixels of the source;</li>
 *   <li>{@code neutral.png} - the same with every tinted zone in grey;</li>
 *   <li>{@code mask_<zone>.png} - one per tinted zone (iris, upper contour), where its colour applies.</li>
 * </ul>
 *
 * <p>Input: {@code input/eyes/eyes.json} and the source PNG it names. The metadata lists the pixels of every zone;
 * every drawn pixel of the source must belong to a zone. Zones not marked as tinted (the white of the eye) keep their
 * colour.
 *
 * <p>Run with {@code ./gradlew :character-creation:generateEyes}, or start {@link #main} from the IDE in the
 * {@code character-creation} module folder.
 */
public final class EyeTexturesGenerator {
    private static final Path INPUT = Generation.INPUT.resolve("eyes");
    private static final Path OUTPUT = Generation.TEXTURES.resolve("eyes");

    private EyeTexturesGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Generation.checkWorkingDirectory();
        JsonObject meta = Generation.readJson(INPUT.resolve("eyes.json"));
        String sourceModel = meta.get("source_model").getAsString();
        Texture eyes = Texture.read(INPUT.resolve(meta.get("source").getAsString()));

        boolean[] drawn = eyes.occupied();
        boolean[] zoned = new boolean[drawn.length];
        for (Map.Entry<String, JsonElement> zone : meta.getAsJsonObject("zones").entrySet()) {
            for (int index : pixels(zone.getValue().getAsJsonArray())) {
                zoned[index] = true;
            }
        }
        for (int i = 0; i < drawn.length; i++) {
            if (drawn[i] != zoned[i]) {
                throw new IllegalStateException("Pixel " + i % Texture.SIZE + "," + i / Texture.SIZE
                        + (drawn[i] ? " is drawn but in no zone" : " is in a zone but not drawn"));
            }
        }

        List<String> tinted = new ArrayList<>();
        meta.getAsJsonArray("tinted").forEach(zone -> tinted.add(zone.getAsString()));
        List<boolean[]> masks = new ArrayList<>();
        for (String zone : tinted) {
            boolean[] mask = new boolean[drawn.length];
            for (int index : pixels(meta.getAsJsonObject("zones").getAsJsonArray(zone))) {
                mask[index] = true;
            }
            masks.add(mask);
        }
        Texture neutral = eyes.neutral(masks.toArray(boolean[][]::new));

        Generation.clean(OUTPUT);
        for (String model : SkinLayout.MODELS) {
            Path folder = OUTPUT.resolve(model);
            SkinLayout.remap(eyes, sourceModel, model).write(folder.resolve("original.png"));
            SkinLayout.remap(neutral, sourceModel, model).write(folder.resolve("neutral.png"));
            for (int z = 0; z < tinted.size(); z++) {
                SkinLayout.remap(Texture.mask(masks.get(z)), sourceModel, model)
                        .write(folder.resolve("mask_" + tinted.get(z) + ".png"));
            }
        }
        System.out.println("Generated the eyes for " + SkinLayout.MODELS + " in " + OUTPUT);
    }

    /** {@code [[x, y], ...]} as texture indexes. */
    private static List<Integer> pixels(JsonArray points) {
        List<Integer> indexes = new ArrayList<>();
        for (JsonElement point : points) {
            JsonArray xy = point.getAsJsonArray();
            indexes.add(xy.get(1).getAsInt() * Texture.SIZE + xy.get(0).getAsInt());
        }
        return indexes;
    }
}
