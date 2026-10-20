package team.civitatum.books.document;

/** Presentation and storage limits travel with the document, including exported and retained copies. */
public enum DocumentKind {
    BOOK, SCROLL, NOTE;

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }

    public int textLimit() {
        return this == SCROLL ? BookDocument.MAX_CHARACTERS : BookDocument.MAX_PAGE_CHARACTERS;
    }

    public static DocumentKind parse(String id) {
        for (DocumentKind kind : values()) if (kind.id().equals(id)) return kind;
        throw new IllegalArgumentException("kind");
    }
}
