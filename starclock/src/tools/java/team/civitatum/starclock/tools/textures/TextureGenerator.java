package team.civitatum.starclock.tools.textures;

import java.io.IOException;
import java.nio.file.Path;
import team.civitatum.starclock.tools.art.PngFiles;

/**
 * Java port of tools/generate_textures.py: the tower key sprite and the model atlases. Pixels match the Python
 * generator (Pillow 11.3.0); the PNG encoding does not. The icons of the deck, sextant, bowl and ring are drawn by
 * hand now and kept in the repository; the generator no longer writes them, so a run does not put back the old ones.
 *
 * <p>usage: {@code TextureGenerator --output <resources dir>}
 */
public final class TextureGenerator {
    public static void main(String[] args) throws IOException {
        if (args.length != 2 || !args[0].equals("--output"))
            throw new IllegalArgumentException("usage: --output <dir>");
        generate(Path.of(args[1]));
    }

    public static void generate(Path output) throws IOException {
        var items = output.resolve("assets/starclock/textures/item");
        PngFiles.writeRgba(ItemSprites.towerKey(), items.resolve("tower_key.png"));
        System.out.println("Создана 1 текстура в " + items);
        var models = output.resolve("assets/starclock/textures/model");
        for (String kind : ItemSprites.TOOLS)
            PngFiles.writeRgba(ModelAtlases.atlas(kind), models.resolve(kind + ".png"));
        System.out.println("Создано 4 атласа моделей 64×64 в " + models);
    }

    private TextureGenerator() {}
}
