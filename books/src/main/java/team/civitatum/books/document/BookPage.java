package team.civitatum.books.document;

import java.util.Arrays;

/** Text styles use RGB in the upper 24 bits and bold/italic in the two lower bits. */
public final class BookPage {
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int INK = 0x30251a << 2;

    private String text = "";
    private int[] styles = new int[0];
    /** Grows with every change of the text or its styles; lets the screen keep a line layout until it is stale. */
    private int changes;
    public String image = "";
    public float imageWidth = 0.85f;
    public int imageHeight = 130;
    public boolean imageBottom;
    public int fontSize = 10;
    public int alignment;

    public String text() {
        return text;
    }

    public int changeCount() {
        return changes;
    }

    public int styleAt(int index) {
        return index >= 0 && index < styles.length ? styles[index] : INK;
    }

    public void setText(String value) {
        text = value;
        styles = new int[value.length()];
        Arrays.fill(styles, INK);
        changes++;
    }

    public void replace(int start, int end, String insertion) {
        if (start < 0 || end < start || end > text.length()) {
            throw new IllegalArgumentException("selection");
        }
        int[] result = new int[text.length() - (end - start) + insertion.length()];
        System.arraycopy(styles, 0, result, 0, start);
        Arrays.fill(result, start, start + insertion.length(), styleAt(start - 1));
        System.arraycopy(styles, end, result, start + insertion.length(), text.length() - end);
        text = text.substring(0, start) + insertion + text.substring(end);
        styles = result;
        changes++;
    }

    public void format(int start, int end, int flag, Integer color) {
        for (int index = start; index < end; index++) {
            styles[index] = color == null ? styles[index] ^ flag : (color << 2) | (styles[index] & 3);
        }
        changes++;
    }

    void setStyle(int start, int end, int style) {
        Arrays.fill(styles, start, end, style);
        changes++;
    }

    /** Removes the overflow while preserving its character formatting. Images stay on their page. */
    public BookPage split(int offset) {
        BookPage tail = new BookPage();
        tail.text = text.substring(offset);
        tail.styles = Arrays.copyOfRange(styles, offset, styles.length);
        tail.fontSize = fontSize;
        tail.alignment = alignment;
        text = text.substring(0, offset);
        styles = Arrays.copyOf(styles, offset);
        changes++;
        return tail;
    }

    public void prepend(BookPage prefix) {
        int[] combined = new int[prefix.styles.length + styles.length];
        System.arraycopy(prefix.styles, 0, combined, 0, prefix.styles.length);
        System.arraycopy(styles, 0, combined, prefix.styles.length, styles.length);
        text = prefix.text + text;
        styles = combined;
        changes++;
    }
}
