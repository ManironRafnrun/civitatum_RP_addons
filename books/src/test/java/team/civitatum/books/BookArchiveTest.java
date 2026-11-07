package team.civitatum.books;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.civitatum.books.document.BookArchive;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.document.BookPage;
import team.civitatum.books.document.PngImage;
import team.civitatum.books.server.BookSigning;

class BookArchiveTest {
    @TempDir Path directory;

    @Test
    void archiveKeepsPagesFormattingAuthorAndPngWithoutWorldRevision() throws Exception {
        BookDocument book = new BookDocument();
        book.title = "Письмо для ивента";
        book.author = "Городской архив";
        book.signed = true;
        book.revision = 24;
        book.pages.getFirst().setText("Путнику, прибывшему к воротам.");
        book.pages.getFirst().format(0, 7, BookPage.BOLD, null);
        BufferedImage image = new BufferedImage(32, 24, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(5, 5, 0x7f33bb44);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", bytes);
        byte[] png = PngImage.normalize(bytes.toByteArray());
        String hash = PngImage.hash(png);
        book.pages.getFirst().image = hash;
        Path file = directory.resolve("event.cbook");
        BookArchive.write(file, book, Map.of(hash, png));
        BookArchive.Contents imported = BookArchive.read(file);
        book.revision = 0;
        assertEquals(book.json(), imported.document().json());
        assertArrayEquals(png, imported.images().get(hash));
        assertEquals(0x7f33bb44, ImageIO.read(new java.io.ByteArrayInputStream(imported.images().get(hash))).getRGB(5, 5));
    }

    @Test
    void pathTraversalAndUnrelatedFilesAreRejectedWithoutExtractingAnything() throws Exception {
        Path file = directory.resolve("bad.cbook");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("../outside.txt"));
            zip.write(new byte[] {1});
            zip.closeEntry();
        }
        assertThrows(Exception.class, () -> BookArchive.read(file));
        assertFalse(Files.exists(directory.getParent().resolve("outside.txt")));
    }

    @Test
    void missingImagesAndOversizedExpandedDocumentsAreRejected() throws Exception {
        BookDocument book = new BookDocument();
        book.pages.getFirst().image = "a".repeat(64);
        assertThrows(Exception.class, () -> BookArchive.write(directory.resolve("missing.cbook"), book, Map.of()));
        Path file = directory.resolve("bomb.cbook");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("book.json"));
            zip.write(new byte[BookDocument.MAX_BYTES + 1]);
            zip.closeEntry();
        }
        assertThrows(Exception.class, () -> BookArchive.read(file));
    }

    @Test
    void playerCannotForgeArchiveAuthorButAdministratorCanSetEventSignature() {
        assertEquals("Player", BookSigning.author("", "Королевский архив", "Player", false, true));
        assertEquals("", BookSigning.author("", "Королевский архив", "Player", false, false));
        assertEquals("Королевский архив", BookSigning.author("", "Королевский архив", "Admin", true, true));
        assertEquals("Архив", BookSigning.author("Архив", "Подмена", "Player", false, true));
        assertThrows(RuntimeException.class, () -> BookSigning.clean("Архив\nИгрок"));
    }
}
