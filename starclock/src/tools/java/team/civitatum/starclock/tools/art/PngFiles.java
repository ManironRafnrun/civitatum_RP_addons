package team.civitatum.starclock.tools.art;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** PNG input and output. Samples are read raw: like Pillow, no gamma or colour profile is applied. */
public final class PngFiles {
    /** {@code Image.open(...).convert("RGB")}: three bands, any alpha dropped. */
    public static Raster readRgb(byte[] png) throws IOException {
        var image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null) throw new IOException("not a PNG image");
        var source = image.getRaster();
        int bands = source.getNumBands();
        if (bands != 3 && bands != 4) throw new IOException("unsupported PNG layout: " + bands + " bands");
        var out = new Raster(image.getWidth(), image.getHeight(), 3);
        int[] pixel = new int[bands];
        for (int y = 0; y < out.height; y++)
            for (int x = 0; x < out.width; x++) {
                source.getPixel(x, y, pixel);
                for (int c = 0; c < 3; c++) out.set(x, y, c, pixel[c]);
            }
        return out;
    }

    /** Writes an "RGBA" raster as a non-premultiplied 8-bit RGBA PNG. */
    public static void writeRgba(Raster raster, Path path) throws IOException {
        if (raster.bands != 4) throw new IllegalArgumentException("RGBA expected");
        var image = new BufferedImage(raster.width, raster.height, BufferedImage.TYPE_4BYTE_ABGR);
        var target = image.getRaster();
        int[] pixel = new int[4];
        for (int y = 0; y < raster.height; y++)
            for (int x = 0; x < raster.width; x++) {
                for (int c = 0; c < 4; c++) pixel[c] = raster.get(x, y, c);
                target.setPixel(x, y, pixel);
            }
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(image, "png", path.toFile())) throw new IOException("no PNG writer");
    }

    private PngFiles() {}
}
