package team.civitatum.books.client;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.document.BookPage;
import team.civitatum.books.document.PngImage;

class BookShelfTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();
    private final UUID book = UUID.randomUUID();

    @Test
    void reopeningTheClientRetainsTextFormattingAuthorAndPng() throws Exception {
        byte[] png = png();
        String hash = PngImage.hash(png);
        BookDocument document = document();
        document.pages.getFirst().image = hash;
        document.pages.getFirst().format(0, 4, BookPage.BOLD, null);
        BookShelf shelf = new BookShelf(directory, player, "server:one");
        shelf.remember(book, document);
        shelf.image(hash, png);

        BookShelf reopened = new BookShelf(directory, player, "server:one");
        var saved = reopened.read(book);
        assertEquals(document.json(), saved.document().json());
        assertArrayEquals(png, saved.images().get(hash));
        assertEquals(1, reopened.entries().size());
        assertTrue(reopened.entries().getFirst().complete());
    }

    @Test
    void reopeningAnOriginalUpdatesOneRetainedCopy() throws Exception {
        BookShelf shelf = new BookShelf(directory, player, "server:one");
        BookDocument original = document();
        shelf.remember(book, original);
        BookDocument updated = original.copy();
        updated.revision++;
        updated.title = "Дополненные записи";
        updated.pages.getFirst().setText("Новая серверная редакция");
        shelf.remember(book, updated);
        assertEquals(1, shelf.entries().size());
        assertEquals(updated.json(), shelf.read(book).document().json());
    }

    @Test
    void anotherPlayerOrWorldCannotSeeTheseCopies() throws Exception {
        new BookShelf(directory, player, "server:one").remember(book, document());
        assertTrue(new BookShelf(directory, player, "server:two").entries().isEmpty());
        assertTrue(new BookShelf(directory, UUID.randomUUID(), "server:one").entries().isEmpty());
    }

    @Test
    void textIsAvailableBeforeImagesAndLateImageCompletesAllCopies() throws Exception {
        BookShelf shelf = new BookShelf(directory, player, "local:world");
        byte[] png = png();
        String hash = PngImage.hash(png);
        BookDocument document = document();
        document.pages.getFirst().image = hash;
        UUID other = UUID.randomUUID();
        shelf.remember(book, document);
        shelf.remember(other, document);
        assertTrue(shelf.entries().stream().noneMatch(BookShelf.Entry::complete));
        assertEquals(document.json(), shelf.read(book).document().json());
        assertTrue(shelf.read(book).images().isEmpty());

        shelf.image(hash, png);
        assertTrue(shelf.entries().stream().allMatch(BookShelf.Entry::complete));
        assertArrayEquals(png, shelf.read(other).images().get(hash));
        try (var paths = Files.walk(directory)) {
            assertEquals(1, paths.filter(path -> path.toString().endsWith(".png")).count());
        }
    }

    @Test
    void invalidImagesNeverBecomeLibraryFiles() throws Exception {
        BookShelf shelf = new BookShelf(directory, player, "server:one");
        byte[] png = png();
        assertThrows(IOException.class, () -> shelf.image("../escape", png));
        byte[] invalid = {1, 2, 3, 4};
        assertThrows(IOException.class, () -> shelf.image(PngImage.hash(invalid), invalid));
        assertThrows(IOException.class, () -> shelf.image(PngImage.hash(png), new byte[PngImage.MAX_BYTES + 1]));
    }

    @Test
    void damagedLocalFilesDoNotHideReadableBooks() throws Exception {
        BookShelf shelf = new BookShelf(directory, player, "server:one");
        BookDocument document = document();
        byte[] png = png();
        String hash = PngImage.hash(png);
        document.pages.getFirst().image = hash;
        shelf.remember(book, document);
        shelf.image(hash, png);
        UUID damaged = UUID.randomUUID();
        shelf.remember(damaged, document());
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (path.getFileName().toString().equals(damaged + ".json")
                        || path.getFileName().toString().equals(hash + ".png")) Files.writeString(path, "broken");
            }
        }
        assertEquals(1, shelf.entries().size());
        assertEquals(document.json(), shelf.read(book).document().json());
        assertTrue(shelf.read(book).images().isEmpty());
    }

    private static BookDocument document() {
        BookDocument document = new BookDocument();
        document.title = "Записки";
        document.author = "Городской архив";
        document.revision = 7;
        document.signed = true;
        document.pages.getFirst().setText("Книга, доступная без предмета в инвентаре.");
        return document;
    }

    private static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(3, 4, 0x804488cc);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        return PngImage.normalize(output.toByteArray());
    }
}
