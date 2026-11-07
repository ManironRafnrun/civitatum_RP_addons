package team.civitatum.books.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.server.BookStore;

/** Recovery files are local drafts; they never overwrite a newer server revision automatically. */
final class DraftStore {
    private final Path file;
    private final Executor disk;

    DraftStore(Path gameDirectory, UUID book, Executor disk) {
        file = gameDirectory.resolve("civitatum_books/drafts").resolve(book + ".json");
        this.disk = disk;
    }

    BookDocument recover(BookDocument serverCopy) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > BookDocument.MAX_BYTES || serverCopy.signed) return null;
        BookDocument draft = BookDocument.parse(Files.readAllBytes(file));
        return !draft.signed && draft.revision == serverCopy.revision && !draft.json().equals(serverCopy.json()) ? draft : null;
    }

    /**
     * Takes the snapshot on the calling thread and writes it on {@code disk}: a draft is written every two seconds
     * while the book has unsaved changes, and a disk stall must not freeze the editor. A single-thread executor keeps
     * the writes in order.
     */
    CompletableFuture<Void> write(BookDocument document) {
        byte[] bytes = document.checkedJson().getBytes(StandardCharsets.UTF_8);
        return CompletableFuture.runAsync(() -> {
            try {
                BookStore.writeAtomically(file, bytes);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }, disk);
    }
}
