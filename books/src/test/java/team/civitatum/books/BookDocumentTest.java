package team.civitatum.books;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.document.BookPage;
import team.civitatum.books.document.DocumentKind;

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
        assertThrows(RuntimeException.class, book::checkedJson);
    }

    @Test
    void storedFormatKeepsGsonEscapingAndNumberFormat() {
        BookDocument book = new BookDocument();
        book.title = "<Сказ> & 'башня'=\"1\"";
        book.revision = 7;
        BookPage page = book.pages.getFirst();
        page.setText("Дорога\n😀 <b>\u2028\t\\");
        page.format(0, 6, BookPage.BOLD, null);
        page.format(7, 9, 0, 0x923c32);
        page.imageWidth = 0.3f;
        // written by the JsonObject serializer of 0.4.1; drafts and stored books must keep reading the same way
        assertEquals("{\"version\":2,\"kind\":\"book\","
                + "\"title\":\"\\u003cСказ\\u003e \\u0026 \\u0027башня\\u0027\\u003d\\\"1\\\"\","
                + "\"author\":\"\",\"signed\":false,\"revision\":7,"
                + "\"pages\":[{\"text\":\"Дорога\\n😀 \\u003cb\\u003e\\u2028\\t\\\\\","
                + "\"image\":\"\",\"imageWidth\":0.3,\"imageHeight\":130,\"imageBottom\":false,"
                + "\"fontSize\":10,\"alignment\":0,"
                + "\"styles\":[[0,6,12620905],[7,9,38334664]]}]}", book.json());
        assertEquals(book.json(), book.checkedJson());
    }

    @Test
    void checkedJsonAcceptsAndRejectsLikeParsing() {
        BookDocument book = new BookDocument();
        book.pages.getFirst().fontSize = 15;
        assertEquals("font", reason(() -> BookDocument.parse(book.bytes())));
        assertEquals("font", reason(book::checkedJson));
        book.pages.getFirst().fontSize = 10;
        book.pages.getFirst().format(0, 0, 0, 0x923c32);
        book.pages.getFirst().setText("абв");
        book.pages.getFirst().format(0, 3, 0, 0x1000000);
        assertEquals("style", reason(book::checkedJson));
        assertEquals("style", reason(() -> BookDocument.parse(book.bytes())));
    }

    private static String reason(org.junit.jupiter.api.function.Executable action) {
        return assertThrows(IllegalArgumentException.class, action).getMessage();
    }

    @Test
    void utf8SizeIsCountedLikeTheEncoder() {
        // three-byte characters: under the character limit, over the byte limit only in UTF-8
        assertSameVerdict(scroll("字".repeat(BookDocument.MAX_CHARACTERS)));
        assertSameVerdict(scroll("字".repeat(BookDocument.MAX_CHARACTERS - 100)));
        // a high surrogate stands alone while a character outside the BMP is typed; the encoder writes one byte
        assertSameVerdict(scroll("\uD83D".repeat(BookDocument.MAX_CHARACTERS)));
        assertSameVerdict(scroll("😀".repeat(BookDocument.MAX_CHARACTERS / 2)));
    }

    private static BookDocument scroll(String text) {
        BookDocument book = new BookDocument();
        book.kind = DocumentKind.SCROLL;
        book.pages.getFirst().setText(text);
        return book;
    }

    private static void assertSameVerdict(BookDocument book) {
        boolean parsed;
        try {
            BookDocument.parse(book.bytes());
            parsed = true;
        } catch (IllegalArgumentException exception) {
            parsed = false;
        }
        boolean checked;
        try {
            book.checkedJson();
            checked = true;
        } catch (IllegalArgumentException exception) {
            checked = false;
        }
        assertEquals(parsed, checked);
    }
}
