package team.civitatum.books;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import team.civitatum.books.client.BookEditor;
import team.civitatum.books.document.*;

class DocumentFormatsTest {
    @Test void legacyBooksAndEveryNewKindRoundTrip() {
        BookDocument book = new BookDocument();
        assertEquals(DocumentKind.BOOK, BookDocument.parse(book.json().replace("\"version\":2", "\"version\":1")
                .replace("\"kind\":\"book\",", "")).kind);
        for (DocumentKind kind : DocumentKind.values()) {
            book.kind = kind;
            assertEquals(kind, BookDocument.parse(book.bytes()).kind);
        }
        assertThrows(RuntimeException.class, () -> BookDocument.parse(book.json().replace("\"note\"", "\"invalid\"")));
    }

    @Test void aLongScrollDoesNotPaginateAndConvertsBackWithoutLosingText() {
        BookDocument book = new BookDocument();
        book.kind = DocumentKind.SCROLL;
        BookEditor editor = new BookEditor(book);
        String text = "Записи путника. ".repeat(1600);
        editor.insert(text, (point, style) -> 6);
        assertEquals(1, editor.document.pages.size());
        assertEquals(text, BookDocument.parse(editor.document.bytes()).pages.getFirst().text());
        editor.changeKind(DocumentKind.BOOK, (point, style) -> 6);
        assertTrue(editor.document.pages.size() > 1);
        assertEquals(text, editor.document.pages.stream().map(BookPage::text).collect(java.util.stream.Collectors.joining()));
        assertTrue(editor.document.pages.stream().allMatch(page -> page.text().length() <= BookDocument.MAX_PAGE_CHARACTERS));
    }

    @Test void aNoteRejectsOverflowAndPreservesThePreviousDocument() {
        BookDocument book = new BookDocument();
        book.kind = DocumentKind.NOTE;
        BookEditor editor = new BookEditor(book);
        editor.insert("Короткое письмо.", (point, style) -> 6);
        String before = editor.document.json();
        assertThrows(RuntimeException.class, () -> editor.insert("Абзац. ".repeat(500), (point, style) -> 6));
        assertEquals(before, editor.document.json());
        assertEquals(1, editor.document.pages.size());
        assertThrows(RuntimeException.class, () -> editor.insert("a".repeat(BookDocument.MAX_PAGE_CHARACTERS + 1), (point, style) -> 6));
    }

    @Test void convertingToANoteNeverDropsAnExtraPageOrIllustration() {
        BookDocument book = new BookDocument();
        book.pages.getFirst().setText("Длинная запись. ".repeat(200));
        BookEditor editor = new BookEditor(book);
        String before = book.json();
        assertThrows(RuntimeException.class, () -> editor.changeKind(DocumentKind.NOTE, (point, style) -> 6));
        assertEquals(before, editor.document.json());
        book = new BookDocument();
        book.pages.getFirst().image = "a".repeat(64);
        BookPage second = new BookPage();
        second.image = "b".repeat(64);
        book.pages.add(second);
        BookDocument illustrated = book;
        assertThrows(RuntimeException.class, () -> DocumentFormats.change(illustrated, DocumentKind.SCROLL));
        assertEquals(2, book.pages.size());
        assertEquals(DocumentKind.BOOK, book.kind);
    }

    @Test void conversionPreservesCharacterStylesImageAndUndoHistory() {
        BookDocument book = new BookDocument();
        book.pages.getFirst().setText("Первая ");
        book.pages.getFirst().format(0, 6, BookPage.BOLD, null);
        BookPage second = new BookPage();
        second.setText("вторая");
        second.format(0, 6, BookPage.ITALIC, null);
        second.image = "a".repeat(64);
        book.pages.add(second);
        BookEditor editor = new BookEditor(book);
        editor.changeKind(DocumentKind.SCROLL, (point, style) -> 6);
        assertEquals("Первая вторая", editor.currentPage().text());
        assertEquals(BookPage.INK | BookPage.BOLD, editor.currentPage().styleAt(0));
        assertEquals(BookPage.INK | BookPage.ITALIC, editor.currentPage().styleAt(7));
        assertEquals(second.image, editor.currentPage().image);
        assertTrue(editor.undo());
        assertEquals(DocumentKind.BOOK, editor.document.kind);
        assertEquals(2, editor.document.pages.size());
        assertTrue(editor.redo());
        assertEquals(DocumentKind.SCROLL, editor.document.kind);
    }

    @Test void serverParsingEnforcesSingleSheetsAndCharacterLimits() {
        BookDocument book = new BookDocument();
        book.kind = DocumentKind.NOTE;
        book.pages.add(new BookPage());
        assertThrows(RuntimeException.class, () -> BookDocument.parse(book.bytes()));
        book.pages.removeLast();
        book.pages.getFirst().setText("a".repeat(BookDocument.MAX_PAGE_CHARACTERS + 1));
        assertThrows(RuntimeException.class, () -> BookDocument.parse(book.bytes()));
        book.kind = DocumentKind.SCROLL;
        assertEquals(book.characterCount(), BookDocument.parse(book.bytes()).characterCount());
        book.pages.getFirst().setText("a".repeat(BookDocument.MAX_CHARACTERS + 1));
        assertThrows(RuntimeException.class, () -> BookDocument.parse(book.bytes()));
    }
}
