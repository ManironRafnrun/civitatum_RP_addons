package team.civitatum.books.document;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Validates dimensions before decoding; re-encoding strips metadata and keeps transparency. */
public final class PngImage {
    public static final int MAX_BYTES = 524288;
    public static final int MAX_DIMENSION = 1024;
    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    /** A SHA-256 digest in lower-case hex, the name of a stored image. */
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    public static byte[] normalize(byte[] bytes) throws IOException {
        if (bytes.length < 24 || bytes.length > MAX_BYTES || !Arrays.equals(SIGNATURE, Arrays.copyOf(bytes, 8))) {
            throw new IOException("png_format");
        }
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("png_format");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new IOException("png_dimensions");
                }
                BufferedImage image = reader.read(0);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "PNG", output)) throw new IOException("png_format");
                if (output.size() > MAX_BYTES) throw new IOException("png_size");
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        }
    }

    public static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    public static boolean isHash(String value) {
        return HASH.matcher(value).matches();
    }

    private PngImage() {}
}
