package team.civitatum.charactercreation.tools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** A 64x64 RGBA skin texture as plain ARGB ints, with the few operations the generators need. */
final class Texture {
    static final int SIZE = 64;
    private static final int WHITE = 0xFFFFFFFF;

    private final int[] argb;

    private Texture(int[] argb) {
        this.argb = argb;
    }

    static Texture empty() {
        return new Texture(new int[SIZE * SIZE]);
    }

    /** Reads a PNG; fully transparent pixels become 0 so their hidden colour never leaks into results. */
    static Texture read(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null || image.getWidth() != SIZE || image.getHeight() != SIZE) {
            throw new IOException(file + " is not a 64x64 image");
        }
        int[] argb = image.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE);
        for (int i = 0; i < argb.length; i++) {
            if (argb[i] >>> 24 == 0) {
                argb[i] = 0;
            }
        }
        return new Texture(argb);
    }

    void write(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, SIZE, SIZE, argb, 0, SIZE);
        ImageIO.write(image, "png", file.toFile());
    }

    Texture copy() {
        return new Texture(argb.clone());
    }

    int get(int x, int y) {
        return argb[y * SIZE + x];
    }

    void set(int x, int y, int value) {
        argb[y * SIZE + x] = value;
    }

    boolean opaque(int index) {
        return argb[index] >>> 24 != 0;
    }

    void clear(int x, int y, int width, int height) {
        for (int v = y; v < y + height; v++) {
            Arrays.fill(argb, v * SIZE + x, v * SIZE + x + width, 0);
        }
    }

    /** Only the pixels inside the mask; everything else transparent. */
    Texture keep(boolean[] mask) {
        int[] out = argb.clone();
        for (int i = 0; i < out.length; i++) {
            if (!mask[i]) {
                out[i] = 0;
            }
        }
        return new Texture(out);
    }

    /** Pixels that are not fully transparent. */
    boolean[] occupied() {
        var mask = new boolean[argb.length];
        for (int i = 0; i < argb.length; i++) {
            mask[i] = opaque(i);
        }
        return mask;
    }

    /** A mask texture: opaque white inside the mask, transparent outside. */
    static Texture mask(boolean[] mask) {
        int[] out = new int[SIZE * SIZE];
        for (int i = 0; i < out.length; i++) {
            out[i] = mask[i] ? WHITE : 0;
        }
        return new Texture(out);
    }

    /**
     * The grey base the mod tints: inside each mask the colour is replaced by its brightness relative to the brightest
     * pixel of that mask (which becomes white), so tinting keeps the drawn shading. Pixels outside the masks and alpha
     * stay as they are.
     */
    Texture neutral(boolean[]... masks) {
        int[] out = argb.clone();
        for (boolean[] mask : masks) {
            double brightest = 0;
            for (int i = 0; i < argb.length; i++) {
                if (mask[i]) {
                    brightest = Math.max(brightest, luma(argb[i]));
                }
            }
            double scale = Math.max(brightest, 1);
            for (int i = 0; i < argb.length; i++) {
                if (mask[i]) {
                    // Math.rint rounds halves to even, as the original numpy code did
                    int grey = (int) Math.min(255, Math.max(0, Math.rint(luma(argb[i]) / scale * 255)));
                    out[i] = argb[i] & 0xFF000000 | grey << 16 | grey << 8 | grey;
                }
            }
        }
        return new Texture(out);
    }

    /** Perceived brightness (Rec. 709 weights), 0-255. */
    static double luma(int argb) {
        return (argb >> 16 & 255) * .2126 + (argb >> 8 & 255) * .7152 + (argb & 255) * .0722;
    }
}
