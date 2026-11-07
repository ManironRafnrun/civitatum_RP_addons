package team.civitatum.books.document;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Versioned, bounded on-disk format shared by the editor and the server. */
public final class BookDocument {
    public static final int MAX_PAGES = 128;
    public static final int MAX_PAGE_CHARACTERS = 16384;
    public static final int MAX_CHARACTERS = 131072;
    public static final int MAX_BYTES = 393216;
    public static final int MAX_IMAGES = 32;
    /** Bold and italic bits under a 24-bit colour. */
    private static final int MAX_STYLE = 0x3ffffff;
    /**
     * Initial JSON capacity besides the text, per page and for the header: keys and numbers take about 120 characters,
     * so the buffer rarely grows for ordinary pages.
     */
    private static final int JSON_OVERHEAD = 160;
    private static final Gson GSON = new Gson();

    public DocumentKind kind = DocumentKind.BOOK;
    public String title = "";
    public String author = "";
    public boolean signed;
    public int revision;
    public final List<BookPage> pages = new ArrayList<>(List.of(new BookPage()));

    public int characterCount() {
        return pages.stream().mapToInt(page -> page.text().length()).sum();
    }

    public Set<String> images() {
        Set<String> hashes = new HashSet<>();
        for (BookPage page : pages) {
            if (!page.image.isEmpty()) hashes.add(page.image);
        }
        return hashes;
    }

    public BookDocument copy() {
        return parse(bytes());
    }

    public byte[] bytes() {
        return json().getBytes(StandardCharsets.UTF_8);
    }

    public String json() {
        // Streaming keeps one copy of the text instead of a JSON tree; the writer is configured like Gson.toJson.
        StringWriter output = new StringWriter(characterCount() + JSON_OVERHEAD * pages.size() + JSON_OVERHEAD);
        try (JsonWriter writer = GSON.newJsonWriter(output)) {
            writer.setLenient(true);
            writer.beginObject();
            writer.name("version").value(2);
            writer.name("kind").value(kind.id());
            writer.name("title").value(title);
            writer.name("author").value(author);
            writer.name("signed").value(signed);
            writer.name("revision").value(revision);
            writer.name("pages").beginArray();
            for (BookPage page : pages) writePage(writer, page);
            writer.endArray();
            writer.endObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return output.toString();
    }

    private static void writePage(JsonWriter writer, BookPage page) throws IOException {
        writer.beginObject();
        writer.name("text").value(page.text());
        writer.name("image").value(page.image);
        // boxed like JsonObject.addProperty, so the float keeps Float.toString formatting
        writer.name("imageWidth").value((Number) page.imageWidth);
        writer.name("imageHeight").value(page.imageHeight);
        writer.name("imageBottom").value(page.imageBottom);
        writer.name("fontSize").value(page.fontSize);
        writer.name("alignment").value(page.alignment);
        writer.name("styles").beginArray();
        int length = page.text().length();
        for (int start = 0; start < length;) {
            int style = page.styleAt(start);
            int end = start + 1;
            while (end < length && page.styleAt(end) == style) end++;
            if (style != BookPage.INK) writer.beginArray().value(start).value(end).value(style).endArray();
            start = end;
        }
        writer.endArray();
        writer.endObject();
    }

    /**
     * The JSON of this document after the checks of {@link #parse}. Editing and storage call it on every change, so it
     * inspects the document itself instead of serializing it twice and reading the copy back.
     */
    public String checkedJson() {
        String json = json();
        requireSize(json);
        validate();
        return json;
    }

    public static BookDocument parse(byte[] bytes) {
        require(bytes.length <= MAX_BYTES, "document_size");
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    public static BookDocument parse(String json) {
        requireSize(json);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int version = root.get("version").getAsInt();
        require(version == 1 || version == 2, "version");
        BookDocument document = new BookDocument();
        document.kind = version == 1 ? DocumentKind.BOOK : DocumentKind.parse(root.get("kind").getAsString());
        document.title = root.get("title").getAsString();
        document.author = root.get("author").getAsString();
        document.signed = root.get("signed").getAsBoolean();
        document.revision = root.get("revision").getAsInt();
        JsonArray pages = root.getAsJsonArray("pages");
        document.pages.clear();
        for (var element : pages) {
            JsonObject value = element.getAsJsonObject();
            BookPage page = new BookPage();
            page.setText(value.get("text").getAsString());
            page.image = value.get("image").getAsString();
            page.imageWidth = value.get("imageWidth").getAsFloat();
            page.imageHeight = value.get("imageHeight").getAsInt();
            page.imageBottom = value.get("imageBottom").getAsBoolean();
            page.fontSize = value.get("fontSize").getAsInt();
            page.alignment = value.get("alignment").getAsInt();
            int previousEnd = 0;
            for (var rangeValue : value.getAsJsonArray("styles")) {
                JsonArray range = rangeValue.getAsJsonArray();
                require(range.size() == 3, "style");
                int start = range.get(0).getAsInt();
                int end = range.get(1).getAsInt();
                int style = range.get(2).getAsInt();
                require(start >= previousEnd && end > start && end <= page.text().length(), "style");
                page.setStyle(start, end, style);
                previousEnd = end;
            }
            document.pages.add(page);
        }
        document.validate();
        return document;
    }

    /** Limits of the format; a parsed document and one about to be saved pass the same checks. */
    private void validate() {
        require(title.length() <= 80 && author.length() <= 80, "title");
        require(revision >= 0 && revision < Integer.MAX_VALUE, "revision");
        require(!pages.isEmpty() && pages.size() <= MAX_PAGES, "pages");
        require(kind == DocumentKind.BOOK || pages.size() == 1, "pages");
        for (BookPage page : pages) {
            require(page.text().length() <= kind.textLimit() && page.text().indexOf('\0') < 0, "text");
            require(page.image.isEmpty() || PngImage.isHash(page.image), "image");
            require(Float.isFinite(page.imageWidth) && page.imageWidth >= 0.25f && page.imageWidth <= 1, "image");
            require(page.imageHeight >= 40 && page.imageHeight <= 240, "image");
            require(page.fontSize >= 9 && page.fontSize <= 14, "font");
            require(page.alignment >= 0 && page.alignment <= 2, "alignment");
            require(page.hasStylesWithin(MAX_STYLE), "style");
        }
        require(characterCount() <= MAX_CHARACTERS, "text");
        require(images().size() <= MAX_IMAGES, "images");
    }

    private static void requireSize(String json) {
        require(json.length() <= MAX_BYTES && utf8Length(json) <= MAX_BYTES, "document_size");
    }

    /**
     * Length of {@code value} encoded as UTF-8 by {@link String#getBytes}, without the copy. An unpaired surrogate
     * counts as the one-byte replacement the encoder writes: while a character outside the BMP is typed, its high
     * surrogate briefly stands alone. Guava's {@code Utf8.encodedLength} throws there, so it is not used.
     */
    private static int utf8Length(String value) {
        int bytes = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x80) bytes++;
            else if (character < 0x800) bytes += 2;
            else if (Character.isHighSurrogate(character) && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                bytes += 4;
                index++;
            } else if (Character.isSurrogate(character)) bytes++;
            else bytes += 3;
        }
        return bytes;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException(reason);
    }
}
