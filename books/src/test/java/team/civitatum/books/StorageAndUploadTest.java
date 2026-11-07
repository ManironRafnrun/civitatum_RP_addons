package team.civitatum.books;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.document.PngImage;
import team.civitatum.books.network.UploadBuffer;
import team.civitatum.books.network.UploadKind;
import team.civitatum.books.server.BookStore;

class StorageAndUploadTest {
    @TempDir Path directory;

    @Test
    void completedDocumentsSurviveAStoreRestart() throws Exception {
        BookStore store = new BookStore(directory);
        UUID id = UUID.randomUUID();
        BookDocument book = new BookDocument();
        book.title = "Полевые заметки";
        book.pages.getFirst().setText("Кириллица после перезапуска");
        store.write(id, book);
        BookDocument restored = new BookStore(directory).read(id);
        assertEquals(book.json(), restored.json());
        restored.revision++;
        store.write(id, restored);
        assertEquals(1, new BookStore(directory).read(id).revision);
    }

    @Test
    void imagesAreDeduplicatedAndTransparencyIsPreserved() throws Exception {
        byte[] png = PngImage.normalize(png(48, 32));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(0x7f22aa44, image.getRGB(0, 0));
        BookStore store = new BookStore(directory);
        String hash = store.putImage(png);
        assertEquals(hash, store.putImage(png));
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertArrayEquals(png, new BookStore(directory).image(hash));
        assertThrows(RuntimeException.class, () -> store.image("../secret"));
    }

    @Test
    void detailedPngAboveSixtyFourKibKeepsItsPixels() throws Exception {
        BufferedImage original = new BufferedImage(384, 256, BufferedImage.TYPE_INT_ARGB);
        java.util.Random random = new java.util.Random(7314);
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) original.setRGB(x, y, random.nextInt());
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(original, "PNG", output);
        byte[] normalized = PngImage.normalize(output.toByteArray());
        assertTrue(normalized.length > 64 * 1024 && normalized.length <= PngImage.MAX_BYTES);
        BufferedImage restored = ImageIO.read(new ByteArrayInputStream(normalized));
        assertEquals(original.getWidth(), restored.getWidth());
        assertEquals(original.getHeight(), restored.getHeight());
        assertArrayEquals(original.getRGB(0, 0, 384, 256, null, 0, 384),
                restored.getRGB(0, 0, 384, 256, null, 0, 384));
    }

    @Test
    void invalidAndOversizedPngFilesAreRejected() throws Exception {
        assertThrows(Exception.class, () -> PngImage.normalize(new byte[100]));
        byte[] oversizedDimensions = png(1025, 1);
        assertThrows(Exception.class, () -> PngImage.normalize(oversizedDimensions));
        assertThrows(Exception.class, () -> PngImage.normalize(new byte[PngImage.MAX_BYTES + 1]));
    }

    @Test
    void uploadOnlyCompletesWithAllSequentialChunks() {
        UUID id = UUID.randomUUID();
        UploadBuffer upload = new UploadBuffer(id, UploadKind.SAVE, 5, 100);
        upload.append(id, UploadKind.SAVE, 5, 0, new byte[] {1, 2}, 101);
        assertFalse(upload.complete());
        assertThrows(IllegalStateException.class, upload::bytes);
        upload.append(id, UploadKind.SAVE, 5, 2, new byte[] {3, 4, 5}, 102);
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, upload.bytes());
    }

    @Test
    void reorderedExpiredAndForgedUploadsAreRejected() {
        UUID id = UUID.randomUUID();
        UploadBuffer upload = new UploadBuffer(id, UploadKind.IMAGE, 10, 100);
        assertThrows(RuntimeException.class, () -> upload.append(id, UploadKind.IMAGE, 10, 1, new byte[1], 101));
        assertThrows(RuntimeException.class, () -> upload.append(UUID.randomUUID(), UploadKind.IMAGE, 10, 0, new byte[1], 101));
        assertThrows(RuntimeException.class, () -> upload.append(id, UploadKind.SIGN, 10, 0, new byte[1], 101));
        assertThrows(RuntimeException.class, () -> upload.append(id, UploadKind.IMAGE, 10, 0, new byte[1], 30100));
        assertThrows(RuntimeException.class, () -> new UploadBuffer(id, UploadKind.SAVE, BookDocument.MAX_BYTES + 1, 0));
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x7f22aa44);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        return output.toByteArray();
    }
}
