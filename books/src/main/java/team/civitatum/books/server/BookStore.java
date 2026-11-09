package team.civitatum.books.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.document.PngImage;

/**
 * World-local documents and deduplicated images. A successful save always means a completed disk write.
 * Used on the logical server thread only; the image worker merely decodes and hands its result back to that thread.
 * <p>
 * A document or image the world does not have is read from the shared library, if there is one: the books a server
 * or a pack ships, readable in every world it runs. The library is never written: a book changed in a world is saved
 * to the world and from then on read from it there.
 */
public final class BookStore {
    public static final long MAX_IMAGE_STORAGE = 256L * 1024 * 1024;
    private final Path books;
    private final Path images;
    /** The shared library's documents and images; null without a library. */
    private final Path libraryBooks;
    private final Path libraryImages;
    private long imageBytes;

    public BookStore(Path directory) throws IOException {
        this(directory, null);
    }

    /**
     * @param library the shared library, with the same books and images folders as the world's; null for none. It
     *                need not exist.
     */
    public BookStore(Path directory, Path library) throws IOException {
        libraryBooks = library == null ? null : library.resolve("books");
        libraryImages = library == null ? null : library.resolve("images");
        books = Files.createDirectories(directory.resolve("books"));
        images = Files.createDirectories(directory.resolve("images"));
        try (var files = Files.list(images)) {
            for (Path path : files.filter(path -> path.toString().endsWith(".png")).toList()) {
                imageBytes += Files.size(path);
            }
        }
    }

    public BookDocument read(UUID id) throws IOException {
        Path path = books.resolve(id + ".json");
        if (libraryBooks != null && !Files.exists(path) && Files.isRegularFile(libraryBooks.resolve(id + ".json"))) {
            path = libraryBooks.resolve(id + ".json");
        }
        if (Files.size(path) > BookDocument.MAX_BYTES) throw new IOException("document_size");
        return BookDocument.parse(Files.readAllBytes(path));
    }

    public void write(UUID id, BookDocument document) throws IOException {
        writeAtomically(books.resolve(id + ".json"), document.checkedJson().getBytes(StandardCharsets.UTF_8));
    }

    public String putImage(byte[] normalizedPng) throws IOException {
        String hash = PngImage.hash(normalizedPng);
        Path path = imagePath(hash);
        if (!Files.exists(path)) {
            if (imageBytes + normalizedPng.length > MAX_IMAGE_STORAGE) throw new IOException("storage_full");
            writeAtomically(path, normalizedPng);
            imageBytes += normalizedPng.length;
        }
        return hash;
    }

    public boolean hasImage(String hash) {
        return Files.isRegularFile(imagePath(hash)) || libraryImage(hash) != null;
    }

    public byte[] image(String hash) throws IOException {
        Path path = imagePath(hash);
        Path shared = Files.exists(path) ? null : libraryImage(hash);
        if (shared != null) path = shared;
        if (Files.size(path) > PngImage.MAX_BYTES) throw new IOException("png_size");
        return Files.readAllBytes(path);
    }

    /** The library's copy of an image; null without a library or the image in it. */
    private Path libraryImage(String hash) {
        if (libraryImages == null || !PngImage.isHash(hash)) return null;
        Path path = libraryImages.resolve(hash + ".png");
        return Files.isRegularFile(path) ? path : null;
    }

    private Path imagePath(String hash) {
        if (!PngImage.isHash(hash)) throw new IllegalArgumentException("image");
        return images.resolve(hash + ".png");
    }

    public static void writeAtomically(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".book-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
