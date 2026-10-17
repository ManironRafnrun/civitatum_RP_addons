package team.civitatum.inventory.tools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Generates the stone background of the inventory windows, {@code textures/gui/stone.png}: 32x32 dark brown noise
 * with diagonal veins, alpha 102 everywhere.
 *
 * <p>The noise comes from {@link Random} with a fixed seed, so every run produces the same image.
 *
 * <p>Run with {@code ./gradlew :inventory:generateStoneTexture}, or start {@link #main} straight from the IDE: without
 * arguments it writes into the module's resources, relative to the working directory (the {@code inventory} module
 * folder, the IDE's default). An argument overrides the output file.
 */
public final class StoneTexture {
    private static final int SIZE = 32;
    // Picked out of the first 300 million seeds to look like the original Python texture: same brightness, spread and
    // grain of the noise, and the closest placement of light and dark patches.
    private static final int SEED = 297860597;
    private static final int ALPHA = 102;

    private static final Path DEFAULT_OUTPUT =
            Path.of("src/main/resources/assets/civitatum_inventory/textures/gui/stone.png");

    private StoneTexture() {
    }

    public static void main(String[] args) throws IOException {
        Path output = args.length > 0 ? Path.of(args[0]) : DEFAULT_OUTPUT;
        if (args.length == 0 && !Files.isDirectory(Path.of("src/main/resources"))) {
            throw new IllegalStateException("Run from the inventory module folder (working directory is "
                    + Path.of("").toAbsolutePath() + ") or pass the output file as an argument");
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        ImageIO.write(generate(), "png", output.toFile());
        System.out.println("Generated " + output);
    }

    static BufferedImage generate() {
        Random random = new Random(SEED);
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int noise = random.nextInt(17) - 8; // -8..8
                int vein = (x + y * 3) % 23 < 3 ? 3 : 0;
                int red = Math.max(0, 39 + noise + vein);
                int green = Math.max(0, 35 + noise + vein);
                int blue = Math.max(0, 29 + noise);
                image.setRGB(x, y, ALPHA << 24 | red << 16 | green << 8 | blue);
            }
        }
        return image;
    }
}
