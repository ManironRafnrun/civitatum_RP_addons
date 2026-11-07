package team.civitatum.starclock.tools.art;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code starclock-art.json}: which archive and which originals the figures were made from, and where each was
 * cropped. Written exactly as Python's {@code json.dumps(..., ensure_ascii=False, indent=2)} plus a newline.
 */
public final class ArtReport {
    private final String archive;
    private final String sha256;
    private final Map<String, Figure> figures = new LinkedHashMap<>();

    public record Figure(String source, String sha256, int[] crop) {}

    public ArtReport(String archive, String sha256) {
        this.archive = archive;
        this.sha256 = sha256;
    }

    public void add(String id, Figure figure) {
        figures.put(id, figure);
    }

    public String toJson() {
        var out = new StringBuilder("{\n");
        out.append("  \"archive\": ").append(quote(archive)).append(",\n");
        out.append("  \"sha256\": ").append(quote(sha256)).append(",\n");
        out.append("  \"figures\": {");
        if (figures.isEmpty()) {
            out.append("}\n}\n");
            return out.toString();
        }
        out.append('\n');
        int i = 0;
        for (var entry : figures.entrySet()) {
            var f = entry.getValue();
            out.append("    ").append(quote(entry.getKey())).append(": {\n");
            out.append("      \"source\": ").append(quote(f.source())).append(",\n");
            out.append("      \"sha256\": ").append(quote(f.sha256())).append(",\n");
            out.append("      \"crop\": [\n");
            for (int k = 0; k < f.crop().length; k++)
                out.append("        ").append(f.crop()[k]).append(k + 1 < f.crop().length ? ",\n" : "\n");
            out.append("      ]\n");
            out.append("    }").append(++i < figures.size() ? ",\n" : "\n");
        }
        out.append("  }\n}\n");
        return out.toString();
    }

    /** A JSON string as Python writes it with {@code ensure_ascii=False}: quotes, backslash and controls escaped. */
    private static String quote(String s) {
        var out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
