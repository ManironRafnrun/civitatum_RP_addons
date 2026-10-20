package team.civitatum.books.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import team.civitatum.books.server.BookStore;

/** Portable .cbook ZIP: book.json and images/<sha256>.png, without world-local identifiers. */
public final class BookArchive {
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    public record Contents(BookDocument document, Map<String, byte[]> images) {}

    public static Contents read(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) throw new IOException("archive_size");
        Map<String, byte[]> images = new HashMap<>();
        Set<String> names = new HashSet<>();
        byte[] document = null;
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                if (!names.add(name) || names.size() > BookDocument.MAX_IMAGES + 1) throw new IOException("archive_format");
                if (name.equals("book.json")) {
                    document = boundedRead(input, BookDocument.MAX_BYTES);
                } else if (name.matches("images/[0-9a-f]{64}\\.png")) {
                    String hash = name.substring(7, 71);
                    byte[] bytes = boundedRead(input, PngImage.MAX_BYTES);
                    if (!PngImage.hash(bytes).equals(hash)) throw new IOException("archive_format");
                    images.put(hash, bytes);
                } else {
                    throw new IOException("archive_format");
                }
                input.closeEntry();
            }
        }
        if (document == null) throw new IOException("archive_format");
        BookDocument book;
        try {
            book = BookDocument.parse(document);
        } catch (RuntimeException exception) {
            throw new IOException("archive_format", exception);
        }
        if (!book.images().equals(images.keySet())) throw new IOException("archive_images");
        // Revalidate and strip metadata before any image is sent to the server.
        Map<String, byte[]> normalized = new HashMap<>();
        Map<String, String> replacements = new HashMap<>();
        for (var entry : images.entrySet()) {
            byte[] png = PngImage.normalize(entry.getValue());
            String hash = PngImage.hash(png);
            normalized.put(hash, png);
            replacements.put(entry.getKey(), hash);
        }
        for (BookPage page : book.pages) {
            if (!page.image.isEmpty()) page.image = replacements.get(page.image);
        }
        book.revision = 0;
        return new Contents(book, Map.copyOf(normalized));
    }

    public static void write(Path path, BookDocument document, Map<String, byte[]> images) throws IOException {
        BookDocument copy = BookDocument.parse(document.bytes());
        copy.revision = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            put(zip, "book.json", copy.bytes());
            for (String hash : copy.images().stream().sorted().toList()) {
                byte[] png = images.get(hash);
                if (png == null || png.length > PngImage.MAX_BYTES || !PngImage.hash(png).equals(hash)) {
                    throw new IOException("archive_images");
                }
                put(zip, "images/" + hash + ".png", png);
            }
        }
        if (output.size() > MAX_BYTES) throw new IOException("archive_size");
        BookStore.writeAtomically(path.toAbsolutePath(), output.toByteArray());
    }

    private static byte[] boundedRead(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > limit) throw new IOException("archive_size");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void put(ZipOutputStream output, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }

    private BookArchive() {}
}
