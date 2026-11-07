package team.civitatum.books.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import team.civitatum.books.document.BookArchive;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.document.BookPage;

/** Uploads an archive's PNGs one at a time; the editor is replaced only when every image is accepted. */
final class BookImport {
    final BookDocument document;
    private final Deque<Map.Entry<String, byte[]>> remaining;
    private String awaiting;

    BookImport(BookArchive.Contents archive, BookDocument current, boolean administrative) {
        document = archive.document().copy();
        document.revision = current.revision;
        document.signed = current.signed;
        if (!administrative) document.author = current.author;
        remaining = new ArrayDeque<>(archive.images().entrySet());
    }

    byte[] next() {
        if (remaining.isEmpty()) return null;
        var image = remaining.removeFirst();
        awaiting = image.getKey();
        return image.getValue();
    }

    void accepted(String hash) {
        for (BookPage page : document.pages) {
            if (page.image.equals(awaiting)) page.image = hash;
        }
        awaiting = null;
    }
}
