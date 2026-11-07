package team.civitatum.books.document;

/** Presentation and storage limits travel with the document, including exported and retained copies. */
public enum DocumentKind {
    BOOK, SCROLL, NOTE;

    /** Stored in documents and used in translation keys; the screen asks for it every frame. */
    private final String id = name().toLowerCase(java.util.Locale.ROOT);

    public String id() { return id; }

    public int textLimit() {
        return this == SCROLL ? BookDocument.MAX_CHARACTERS : BookDocument.MAX_PAGE_CHARACTERS;
    }

    public static DocumentKind parse(String id) {
        for (DocumentKind kind : values()) if (kind.id().equals(id)) return kind;
        throw new IllegalArgumentException("kind");
    }
}
