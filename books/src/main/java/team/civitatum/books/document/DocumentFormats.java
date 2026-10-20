package team.civitatum.books.document;

/** Lossless text/style conversion. A single sheet cannot silently discard a second illustration. */
public final class DocumentFormats {
    public static void change(BookDocument document, DocumentKind kind) {
        if (kind == document.kind) return;
        if (kind != DocumentKind.BOOK) {
            if (document.characterCount() > kind.textLimit()) throw new IllegalArgumentException("note_full");
            if (document.pages.stream().filter(page -> !page.image.isEmpty()).count() > 1) {
                throw new IllegalArgumentException("mode_images");
            }
            BookPage first = document.pages.getFirst();
            BookPage merged = new BookPage();
            merged.fontSize = first.fontSize;
            merged.alignment = first.alignment;
            for (int index = document.pages.size() - 1; index >= 0; index--) merged.prepend(document.pages.get(index));
            for (BookPage page : document.pages) if (!page.image.isEmpty()) {
                merged.image = page.image;
                merged.imageWidth = page.imageWidth;
                merged.imageHeight = page.imageHeight;
                merged.imageBottom = page.imageBottom;
            }
            document.pages.clear();
            document.pages.add(merged);
        }
        document.kind = kind;
    }

    private DocumentFormats() {}
}
