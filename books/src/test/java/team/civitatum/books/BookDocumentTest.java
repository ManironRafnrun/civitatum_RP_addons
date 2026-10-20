package team.civitatum.books;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.document.BookPage;

class BookDocumentTest {
    @Test
    void russianTextAndFormattingSurviveStorage() {
        BookDocument book = new BookDocument();
        book.title = "Записки путешественника";
        BookPage page = book.pages.getFirst();
        page.setText("Дорога к башне\nВторая строка");
        page.format(0, 6, BookPage.BOLD, null);
        page.format(7, 13, 0, 0x923c32);
        BookDocument restored = BookDocument.parse(book.bytes());
        assertEquals(book.json(), restored.json());
        assertEquals(BookPage.INK | BookPage.BOLD, restored.pages.getFirst().styleAt(0));
        assertEquals(0x923c32 << 2, restored.pages.getFirst().styleAt(7));
    }

    @Test
    void replacingSelectionKeepsNeighborStyles() {
        BookPage page = new BookPage();
        page.setText("abcdef");
        page.format(0, 2, BookPage.BOLD, null);
        page.replace(2, 4, "Я");
        assertEquals("abЯef", page.text());
        assertEquals(BookPage.INK | BookPage.BOLD, page.styleAt(2));
        assertEquals(BookPage.INK, page.styleAt(3));
    }

    @Test
    void splittingAndPrependingKeepTextAndStyles() {
        BookPage page = new BookPage();
        page.setText("Первая вторая");
        page.format(7, 13, BookPage.ITALIC, null);
        BookPage tail = page.split(7);
        BookPage following = new BookPage();
        following.setText(" третья");
        following.prepend(tail);
        assertEquals("Первая ", page.text());
        assertEquals("вторая третья", following.text());
        assertEquals(BookPage.INK | BookPage.ITALIC, following.styleAt(0));
        assertEquals(BookPage.INK, following.styleAt(6));
    }

    @Test
    void invalidVersionImageAndLayoutAreRejected() {
        String json = new BookDocument().json();
        assertThrows(RuntimeException.class, () -> BookDocument.parse(json.replace("\"version\":2", "\"version\":99")));
        assertThrows(RuntimeException.class, () -> BookDocument.parse(json.replace("\"image\":\"\"", "\"image\":\"../secret\"")));
        assertThrows(RuntimeException.class, () -> BookDocument.parse(json.replace("\"fontSize\":10", "\"fontSize\":100")));
        assertThrows(RuntimeException.class, () -> BookDocument.parse(json.replace("\"imageWidth\":0.85", "\"imageWidth\":2.0")));
    }

    @Test
    void limitsApplyToUtf8BytesAndTheWholeDocument() {
        assertThrows(RuntimeException.class, () -> BookDocument.parse(new byte[BookDocument.MAX_BYTES + 1]));
        BookDocument book = new BookDocument();
        book.pages.clear();
        for (int index = 0; index < 9; index++) {
            BookPage page = new BookPage();
            page.setText("я".repeat(15000));
            book.pages.add(page);
        }
        assertTrue(book.json().getBytes(StandardCharsets.UTF_8).length < BookDocument.MAX_BYTES);
        assertThrows(RuntimeException.class, () -> BookDocument.parse(book.bytes()));
    }
}
