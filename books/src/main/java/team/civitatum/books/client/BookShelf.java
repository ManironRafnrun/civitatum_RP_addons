package team.civitatum.books.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import team.civitatum.books.document.BookArchive;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.document.PngImage;
import team.civitatum.books.server.BookStore;

/** Durable client copies, isolated by player and server/world. No server access is needed to read them. */
final class BookShelf {
    record Entry(UUID id, String title, String author, int pages, long openedAt, boolean complete) {}
    private final Path directory;

    BookShelf(Path gameDirectory, UUID player, String world) {
        directory = gameDirectory.resolve("civitatum_books/library").resolve(player.toString())
                .resolve(PngImage.hash(world.getBytes(StandardCharsets.UTF_8)));
    }

    void remember(UUID id, BookDocument document) throws IOException {
        byte[] bytes = document.bytes();
        BookDocument.parse(bytes);
        BookStore.writeAtomically(bookPath(id), bytes);
    }

    void image(String hash, byte[] png) throws IOException {
        if (png.length > PngImage.MAX_BYTES || !PngImage.hash(png).equals(hash)) throw new IOException("image");
        PngImage.normalize(png); // Validate dimensions and decoding before retaining network bytes.
        BookStore.writeAtomically(imagePath(hash), png);
    }

    List<Entry> entries() throws IOException {
        Path books = directory.resolve("books");
        if (!Files.isDirectory(books)) return List.of();
        List<Entry> entries = new ArrayList<>();
        try (var files = Files.list(books)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                try {
                    String name = file.getFileName().toString();
                    UUID id = UUID.fromString(name.substring(0, name.length() - 5));
                    BookDocument document = document(id);
                    boolean complete = document.images().stream().allMatch(hash -> Files.isRegularFile(imagePath(hash)));
                    entries.add(new Entry(id, document.title, document.author, document.pages.size(),
                            Files.getLastModifiedTime(file).toMillis(), complete));
                } catch (IOException | RuntimeException ignored) {
                    // One damaged local copy must not hide the remaining library.
                }
            }
        }
        entries.sort(Comparator.comparingLong(Entry::openedAt).reversed().thenComparing(entry -> entry.id().toString()));
        return List.copyOf(entries);
    }

    BookArchive.Contents read(UUID id) throws IOException {
        BookDocument document = document(id);
        var images = new HashMap<String, byte[]>();
        for (String hash : document.images()) {
            Path path = imagePath(hash);
            if (!Files.isRegularFile(path)) continue;
            try {
                byte[] png = bounded(path, PngImage.MAX_BYTES);
                if (PngImage.hash(png).equals(hash)) images.put(hash, png);
            } catch (IOException ignored) {
                // Text remains readable even if an illustration was not downloaded or was damaged.
            }
        }
        return new BookArchive.Contents(document, java.util.Map.copyOf(images));
    }

    private BookDocument document(UUID id) throws IOException {
        try {
            return BookDocument.parse(bounded(bookPath(id), BookDocument.MAX_BYTES));
        } catch (RuntimeException exception) {
            throw new IOException("document", exception);
        }
    }

    private static byte[] bounded(Path path, int maximum) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum) throw new IOException("size");
            return bytes;
        }
    }

    private Path bookPath(UUID id) { return directory.resolve("books").resolve(id + ".json"); }

    private Path imagePath(String hash) {
        if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("image");
        return directory.resolve("images").resolve(hash + ".png");
    }
}
