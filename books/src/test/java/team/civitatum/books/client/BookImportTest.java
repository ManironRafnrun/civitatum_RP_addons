package team.civitatum.books.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import team.civitatum.books.document.BookArchive;
import team.civitatum.books.document.BookDocument;

class BookImportTest {
    @Test
    void ordinaryImportBecomesADraftAndKeepsTheDestinationRevision() {
        BookDocument source = new BookDocument();
        source.title = "Письмо";
        source.author = "Король";
        source.signed = true;
        source.revision = 90;
        BookDocument destination = new BookDocument();
        destination.revision = 4;
        BookImport pending = new BookImport(new BookArchive.Contents(source, Map.of()), destination, false);
        assertEquals(4, pending.document.revision);
        assertFalse(pending.document.signed);
        assertEquals("", pending.document.author);
        assertEquals("Письмо", pending.document.title);
    }

    @Test
    void administrativeImportPreservesSignatureAndRemapsNormalizedImages() {
        BookDocument source = new BookDocument();
        source.author = "Городской архив";
        source.pages.getFirst().image = "a".repeat(64);
        BookDocument destination = new BookDocument();
        destination.signed = true;
        BookImport pending = new BookImport(new BookArchive.Contents(source, Map.of("a".repeat(64), new byte[] {1})), destination, true);
        assertArrayEquals(new byte[] {1}, pending.next());
        pending.accepted("b".repeat(64));
        assertNull(pending.next());
        assertTrue(pending.document.signed);
        assertEquals("Городской архив", pending.document.author);
        assertEquals("b".repeat(64), pending.document.pages.getFirst().image);
    }
}
