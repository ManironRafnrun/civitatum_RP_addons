package team.civitatum.books.client;

import java.util.ArrayDeque;
import java.util.Deque;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.document.BookPage;
import team.civitatum.books.document.DocumentKind;
import team.civitatum.books.document.DocumentFormats;
import team.civitatum.books.document.TextLayout;

/** Editing and history are independent of Minecraft screens and network acknowledgements. */
public final class BookEditor {
    public static final int PAGE_WIDTH = 350;
    public static final int PAGE_HEIGHT = 372;
    private static final int HISTORY_CHARACTERS = 4 * 1024 * 1024;
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    public BookDocument document;
    public int page;
    public int cursor;
    public int anchor;

    public BookEditor(BookDocument document) {
        this.document = document;
    }

    public BookPage currentPage() {
        return document.pages.get(page);
    }

    public void selectPage(int index) {
        page = Math.clamp(index, 0, document.pages.size() - 1);
        cursor = 0;
        anchor = 0;
    }

    public int selectionStart() { return Math.min(cursor, anchor); }
    public int selectionEnd() { return Math.max(cursor, anchor); }

    public String selection() {
        return currentPage().text().substring(selectionStart(), selectionEnd());
    }

    public void remember() {
        String json = document.json();
        if (undo.isEmpty() || !undo.peekLast().equals(json)) undo.addLast(json);
        trim(undo);
        redo.clear();
    }

    private static void trim(Deque<String> history) {
        int characters = history.stream().mapToInt(String::length).sum();
        while (history.size() > 1 && (history.size() > 60 || characters > HISTORY_CHARACTERS)) {
            characters -= history.removeFirst().length();
        }
    }

    public boolean undo() { return restore(undo, redo); }
    public boolean redo() { return restore(redo, undo); }

    private boolean restore(Deque<String> from, Deque<String> to) {
        if (from.isEmpty()) return false;
        to.addLast(document.json());
        trim(to);
        int revision = document.revision;
        document = BookDocument.parse(from.removeLast());
        document.revision = revision;
        page = Math.min(page, document.pages.size() - 1);
        cursor = Math.min(cursor, currentPage().text().length());
        anchor = cursor;
        return true;
    }

    public void clearHistory() {
        undo.clear();
        redo.clear();
    }

    public void insert(String value, TextLayout.GlyphWidth measure) {
        String insertion = value.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ")
                .replace("\0", "").replace("§", "");
        int length = currentPage().text().length() - selection().length() + insertion.length();
        if (length > document.kind.textLimit()
                || document.characterCount() - selection().length() + insertion.length() > BookDocument.MAX_CHARACTERS) {
            throw new IllegalArgumentException("text");
        }
        String before = document.json();
        int oldPage = page;
        int oldCursor = cursor;
        int oldAnchor = anchor;
        remember();
        try {
            int start = selectionStart();
            currentPage().replace(start, selectionEnd(), insertion);
            cursor = start + insertion.length();
            anchor = cursor;
            paginate(measure);
            BookDocument.parse(document.bytes());
        } catch (RuntimeException exception) {
            document = BookDocument.parse(before);
            page = oldPage;
            cursor = oldCursor;
            anchor = oldAnchor;
            throw exception;
        }
    }

    public void changeKind(DocumentKind kind, TextLayout.GlyphWidth measure) {
        String before = document.json();
        int oldPage = page, oldCursor = cursor, oldAnchor = anchor;
        remember();
        try {
            DocumentFormats.change(document, kind);
            selectPage(0);
            paginate(measure);
            BookDocument.parse(document.bytes());
        } catch (RuntimeException exception) {
            document = BookDocument.parse(before);
            page = oldPage; cursor = oldCursor; anchor = oldAnchor;
            throw exception;
        }
    }

    /** Overflow moves forward without deleting page boundaries deliberately added by the author. */
    public void paginate(TextLayout.GlyphWidth measure) {
        if (document.kind == DocumentKind.SCROLL) return;
        for (int index = 0; index < document.pages.size(); index++) {
            BookPage current = document.pages.get(index);
            var lines = TextLayout.wrap(current, PAGE_WIDTH, (codePoint, style) ->
                    measure.width(codePoint, style) * current.fontSize / 9f);
            int capacity = Math.max(1, textHeight(current) / lineHeight(current));
            if (lines.size() <= capacity) continue;
            if (document.kind == DocumentKind.NOTE) throw new IllegalArgumentException("note_full");
            if (index + 1 == document.pages.size()) {
                if (document.pages.size() >= BookDocument.MAX_PAGES) throw new IllegalArgumentException("pages");
                BookPage continuation = new BookPage();
                continuation.fontSize = current.fontSize;
                continuation.alignment = current.alignment;
                document.pages.add(continuation);
            }
            int cut = lines.get(capacity).start();
            // Keep the final newline with its next line, avoiding a phantom overflow row on this page.
            if (cut > 0 && current.text().charAt(cut - 1) == '\n') cut--;
            BookPage tail = current.split(cut);
            BookPage following = document.pages.get(index + 1);
            if (tail.text().length() + following.text().length() > BookDocument.MAX_PAGE_CHARACTERS) {
                if (document.pages.size() >= BookDocument.MAX_PAGES) throw new IllegalArgumentException("pages");
                following = new BookPage();
                following.fontSize = current.fontSize;
                following.alignment = current.alignment;
                document.pages.add(index + 1, following);
            }
            following.prepend(tail);
            if (page == index && cursor >= cut) {
                page++;
                cursor -= cut;
                anchor = Math.max(0, anchor - cut);
            }
        }
    }

    public static int textHeight(BookPage page) {
        return PAGE_HEIGHT - (page.image.isEmpty() ? 0 : page.imageHeight + 16);
    }

    public static int lineHeight(BookPage page) {
        return page.fontSize + 3;
    }
}
