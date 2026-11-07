package team.civitatum.books.server;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.network.UploadBuffer;

final class BookSession {
    final UUID token = UUID.randomUUID();
    final UUID book;
    final int slot;
    final boolean administrative;
    final BlockPos lectern;
    final ResourceKey<Level> dimension;
    final Set<String> uploadedImages = new HashSet<>();
    final Set<String> requestedImages = new HashSet<>();
    BookDocument document;
    UploadBuffer upload;
    boolean processingImage;
    long rateWindow;
    int receivedBytes;

    BookSession(UUID book, int slot, BlockPos lectern, ResourceKey<Level> dimension, BookDocument document, boolean administrative) {
        this.administrative = administrative;
        this.book = book;
        this.slot = slot;
        this.lectern = lectern;
        this.dimension = dimension;
        this.document = document;
    }

    boolean receive(int bytes, long now) {
        if (now - rateWindow >= 60000) {
            rateWindow = now;
            receivedBytes = 0;
        }
        receivedBytes += bytes;
        return receivedBytes <= 24 * 1024 * 1024;
    }
}
