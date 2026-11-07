package team.civitatum.books.server;

import java.io.IOException;
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
 */
public final class BookStore {
    public static final long MAX_IMAGE_STORAGE = 256L * 1024 * 1024;
    private final Path books;
    private final Path images;
    private long imageBytes;

    public BookStore(Path directory) throws IOException {
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
        if (Files.size(path) > BookDocument.MAX_BYTES) throw new IOException("document_size");
        return BookDocument.parse(Files.readAllBytes(path));
    }

    public void write(UUID id, BookDocument document) throws IOException {
        byte[] bytes = document.bytes();
        BookDocument.parse(bytes);
        writeAtomically(books.resolve(id + ".json"), bytes);
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
        return Files.isRegularFile(imagePath(hash));
    }

    public byte[] image(String hash) throws IOException {
        Path path = imagePath(hash);
        if (Files.size(path) > PngImage.MAX_BYTES) throw new IOException("png_size");
        return Files.readAllBytes(path);
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
