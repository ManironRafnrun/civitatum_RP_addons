package team.civitatum.books.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.civitatum.books.document.BookDocument;

class DraftStoreTest {
    @TempDir Path directory;

    @Test
    void recoveryOffersOnlyDifferentUnsignedDraftsForTheSameRevision() throws Exception {
        DraftStore drafts = new DraftStore(directory, UUID.randomUUID(), Runnable::run);
        BookDocument server = new BookDocument();
        server.revision = 3;
        assertNull(drafts.recover(server));
        drafts.write(server).join();
        assertNull(drafts.recover(server));
        BookDocument changed = server.copy();
        changed.pages.getFirst().setText("Несохранённый абзац");
        drafts.write(changed).join();
        assertEquals(changed.json(), drafts.recover(server).json());
        server.revision = 4;
        assertNull(drafts.recover(server));
        server.revision = 3;
        server.signed = true;
        assertNull(drafts.recover(server));
    }
}
