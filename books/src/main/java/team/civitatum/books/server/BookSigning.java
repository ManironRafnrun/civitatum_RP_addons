package team.civitatum.books.server;

/** Server-side author policy. Importing a file does not grant authority to forge another signature. */
public final class BookSigning {
    public static String author(String existing, String requested, String playerName, boolean administrative, boolean signed) {
        if (!signed) return administrative ? clean(requested) : existing;
        if (administrative) {
            String custom = clean(requested);
            return custom.isEmpty() ? playerName : custom;
        }
        return existing.isBlank() ? playerName : existing;
    }

    public static String clean(String value) {
        String author = value.strip();
        if (author.length() > 80 || author.codePoints().anyMatch(point -> Character.isISOControl(point) || point == 0x00a7)) {
            throw new IllegalArgumentException("author");
        }
        return author;
    }

    private BookSigning() {}
}
