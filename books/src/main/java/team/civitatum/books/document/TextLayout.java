package team.civitatum.books.document;

import java.util.ArrayList;
import java.util.List;

/** Character offsets are UTF-16 offsets; wrapping and cursor movement never split a code point. */
public final class TextLayout {
    @FunctionalInterface
    public interface GlyphWidth {
        float width(int codePoint, int style);
    }

    public record Line(int start, int end, float width) {}

    public static List<Line> wrap(BookPage page, float maximumWidth, GlyphWidth measure) {
        return wrap(page, maximumWidth, measure, Integer.MAX_VALUE);
    }

    /**
     * The first {@code maximumLines} lines of {@link #wrap(BookPage, float, GlyphWidth)}. Pagination only needs one
     * line past the capacity of a page, not the layout of a whole pasted text.
     */
    public static List<Line> wrap(BookPage page, float maximumWidth, GlyphWidth measure, int maximumLines) {
        List<Line> lines = new ArrayList<>();
        String text = page.text();
        int start = 0;
        while (start < text.length()) {
            if (lines.size() >= maximumLines) return lines;
            int offset = start;
            int lastSpace = -1;
            float width = 0;
            float widthAtSpace = 0;
            while (offset < text.length() && text.charAt(offset) != '\n') {
                int codePoint = text.codePointAt(offset);
                float advance = measure.width(codePoint, page.styleAt(offset));
                if (width + advance > maximumWidth && offset > start) break;
                width += advance;
                offset += Character.charCount(codePoint);
                if (Character.isWhitespace(codePoint)) {
                    lastSpace = offset;
                    widthAtSpace = width;
                }
            }
            if (offset < text.length() && text.charAt(offset) == '\n') {
                lines.add(new Line(start, offset, width));
                start = offset + 1;
            } else {
                if (offset < text.length() && lastSpace > start) {
                    offset = lastSpace;
                    width = widthAtSpace;
                }
                lines.add(new Line(start, offset, width));
                start = offset;
            }
        }
        if ((text.isEmpty() || text.endsWith("\n")) && lines.size() < maximumLines) {
            lines.add(new Line(text.length(), text.length(), 0));
        }
        return lines;
    }

    public static int previous(String text, int offset) {
        return offset == 0 ? 0 : text.offsetByCodePoints(offset, -1);
    }

    public static int next(String text, int offset) {
        return offset == text.length() ? offset : text.offsetByCodePoints(offset, 1);
    }

    private TextLayout() {}
}
