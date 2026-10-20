package team.civitatum.books.document;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        root.addProperty("kind", kind.id());
        root.addProperty("title", title);
        root.addProperty("author", author);
        root.addProperty("signed", signed);
        root.addProperty("revision", revision);
        JsonArray array = new JsonArray();
        for (BookPage page : pages) {
            JsonObject value = new JsonObject();
            value.addProperty("text", page.text());
            value.addProperty("image", page.image);
            value.addProperty("imageWidth", page.imageWidth);
            value.addProperty("imageHeight", page.imageHeight);
            value.addProperty("imageBottom", page.imageBottom);
            value.addProperty("fontSize", page.fontSize);
            value.addProperty("alignment", page.alignment);
            JsonArray ranges = new JsonArray();
            for (int start = 0; start < page.text().length();) {
                int style = page.styleAt(start);
                int end = start + 1;
                while (end < page.text().length() && page.styleAt(end) == style) end++;
                if (style != BookPage.INK) {
                    JsonArray range = new JsonArray();
                    range.add(start);
                    range.add(end);
                    range.add(style);
                    ranges.add(range);
                }
                start = end;
            }
            value.add("styles", ranges);
            array.add(value);
        }
        root.add("pages", array);
        return GSON.toJson(root);
    }

    public static BookDocument parse(byte[] bytes) {
        require(bytes.length <= MAX_BYTES, "document_size");
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    public static BookDocument parse(String json) {
        require(json.length() <= MAX_BYTES, "document_size");
        require(json.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES, "document_size");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int version = root.get("version").getAsInt();
        require(version == 1 || version == 2, "version");
        BookDocument document = new BookDocument();
        document.kind = version == 1 ? DocumentKind.BOOK : DocumentKind.parse(root.get("kind").getAsString());
        document.title = root.get("title").getAsString();
        document.author = root.get("author").getAsString();
        document.signed = root.get("signed").getAsBoolean();
        document.revision = root.get("revision").getAsInt();
        require(document.title.length() <= 80 && document.author.length() <= 80, "title");
        require(document.revision >= 0 && document.revision < Integer.MAX_VALUE, "revision");
        JsonArray pages = root.getAsJsonArray("pages");
        require(!pages.isEmpty() && pages.size() <= MAX_PAGES, "pages");
        require(document.kind == DocumentKind.BOOK || pages.size() == 1, "pages");
        document.pages.clear();
        for (var element : pages) {
            JsonObject value = element.getAsJsonObject();
            BookPage page = new BookPage();
            page.setText(value.get("text").getAsString());
            require(page.text().length() <= document.kind.textLimit() && page.text().indexOf('\0') < 0, "text");
            page.image = value.get("image").getAsString();
            require(page.image.isEmpty() || page.image.matches("[0-9a-f]{64}"), "image");
            page.imageWidth = value.get("imageWidth").getAsFloat();
            page.imageHeight = value.get("imageHeight").getAsInt();
            page.imageBottom = value.get("imageBottom").getAsBoolean();
            page.fontSize = value.get("fontSize").getAsInt();
            page.alignment = value.get("alignment").getAsInt();
            require(Float.isFinite(page.imageWidth) && page.imageWidth >= 0.25f && page.imageWidth <= 1, "image");
            require(page.imageHeight >= 40 && page.imageHeight <= 240, "image");
            require(page.fontSize >= 9 && page.fontSize <= 14, "font");
            require(page.alignment >= 0 && page.alignment <= 2, "alignment");
            int previousEnd = 0;
            for (var rangeValue : value.getAsJsonArray("styles")) {
                JsonArray range = rangeValue.getAsJsonArray();
                require(range.size() == 3, "style");
                int start = range.get(0).getAsInt();
                int end = range.get(1).getAsInt();
                int style = range.get(2).getAsInt();
                require(start >= previousEnd && end > start && end <= page.text().length(), "style");
                require(style >= 0 && style <= 0x3ffffff, "style");
                page.setStyle(start, end, style);
                previousEnd = end;
            }
            document.pages.add(page);
        }
        require(document.characterCount() <= MAX_CHARACTERS, "text");
        require(document.images().size() <= MAX_IMAGES, "images");
        return document;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException(reason);
    }
}
