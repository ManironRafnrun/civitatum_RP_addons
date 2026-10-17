package team.civitatum.charactercreation.tools;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Generates the base skin of a new character, {@code textures/character/skin/<model>/}:
 * <ul>
 *   <li>{@code original.png} - the source skin, inner layer only;</li>
 *   <li>{@code neutral.png} - the same in grey, which the mod tints with the chosen skin colour;</li>
 *   <li>{@code mask_skin.png} - where the skin colour applies.</li>
 * </ul>
 *
 * <p>Input: {@code input/skin/skin.json} and the source PNG it names. The source must cover every pixel of the inner
 * layer; its outer layer and stray template pixels are dropped.
 *
 * <p>Run with {@code ./gradlew :character-creation:generateSkin}, or start {@link #main} from the IDE in the
 * {@code character-creation} module folder.
 */
public final class SkinTexturesGenerator {
    private static final Path INPUT = Generation.INPUT.resolve("skin");
    private static final Path OUTPUT = Generation.TEXTURES.resolve("skin");

    private SkinTexturesGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Generation.checkWorkingDirectory();
        JsonObject meta = Generation.readJson(INPUT.resolve("skin.json"));
        String sourceModel = meta.get("source_model").getAsString();

        boolean[] inner = SkinLayout.surface(sourceModel, "base");
        Texture skin = Texture.read(INPUT.resolve(meta.get("source").getAsString())).keep(inner);
        boolean[] covered = skin.occupied();
        for (int i = 0; i < inner.length; i++) {
            if (inner[i] != covered[i]) {
                throw new IllegalStateException("The source skin does not cover the inner layer at "
                        + i % Texture.SIZE + "," + i / Texture.SIZE);
            }
        }
        Texture neutral = skin.neutral(covered);
        Texture mask = Texture.mask(covered);

        Generation.clean(OUTPUT);
        for (String model : SkinLayout.MODELS) {
            Path folder = OUTPUT.resolve(model);
            SkinLayout.remap(skin, sourceModel, model).write(folder.resolve("original.png"));
            SkinLayout.remap(neutral, sourceModel, model).write(folder.resolve("neutral.png"));
            SkinLayout.remap(mask, sourceModel, model).write(folder.resolve("mask_skin.png"));
        }
        System.out.println("Generated the skin for " + SkinLayout.MODELS + " in " + OUTPUT);
    }
}
