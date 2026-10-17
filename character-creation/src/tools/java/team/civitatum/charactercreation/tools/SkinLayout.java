package team.civitatum.charactercreation.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Minecraft 64x64 skin layout: which pixel belongs to which body part, layer and face, and how arms move between
 * the slim (3 pixels wide) and the classic (4 pixels wide) model.
 */
final class SkinLayout {
    /** Body models the mod renders; textures are generated for each. */
    static final List<String> MODELS = List.of("slim", "classic");

    /** Top-left corners of the four arm boxes: right arm, right sleeve, left arm, left sleeve. */
    private static final int[][] ARMS = {{40, 16}, {40, 32}, {32, 48}, {48, 48}};

    /**
     * Where one pixel of the skin texture belongs. {@code row} is its height on the side of the box, counted from
     * the top: -1 on the top face, the box height on the bottom face.
     */
    record Pixel(String group, String surface, String face, int row) {
    }

    /** A rectangle in the texture. */
    record Rect(int x, int y, int width, int height) {
    }

    private SkinLayout() {
    }

    /** The six faces of a box of width w, height h and depth d unwrapped at (x, y), as Minecraft lays them out. */
    static Map<String, Rect> faces(int x, int y, int w, int h, int d) {
        var faces = new LinkedHashMap<String, Rect>();
        faces.put("top", new Rect(x + d, y, w, d));
        faces.put("bottom", new Rect(x + d + w, y, w, d));
        faces.put("right", new Rect(x, y + d, d, h));
        faces.put("front", new Rect(x + d, y + d, w, h));
        faces.put("left", new Rect(x + d + w, y + d, d, h));
        faces.put("back", new Rect(x + 2 * d + w, y + d, w, h));
        return faces;
    }

    /** Every used pixel of the texture for a model; the key is {@code y * 64 + x}. */
    static Map<Integer, Pixel> atlas(String model) {
        int arm = armWidth(model);
        Object[][] boxes = {
            {"head", "base", 0, 0, 8, 8, 8}, {"head", "outer", 32, 0, 8, 8, 8},
            {"body", "base", 16, 16, 8, 12, 4}, {"body", "outer", 16, 32, 8, 12, 4},
            {"leg", "base", 0, 16, 4, 12, 4}, {"leg", "outer", 0, 32, 4, 12, 4},
            {"leg", "base", 16, 48, 4, 12, 4}, {"leg", "outer", 0, 48, 4, 12, 4},
            {"arm", "base", 40, 16, arm, 12, 4}, {"arm", "outer", 40, 32, arm, 12, 4},
            {"arm", "base", 32, 48, arm, 12, 4}, {"arm", "outer", 48, 48, arm, 12, 4},
        };
        var atlas = new LinkedHashMap<Integer, Pixel>();
        for (Object[] box : boxes) {
            int height = (int) box[5];
            var faces = faces((int) box[2], (int) box[3], (int) box[4], height, (int) box[6]);
            for (var face : faces.entrySet()) {
                Rect r = face.getValue();
                for (int v = 0; v < r.height(); v++) {
                    int row = switch (face.getKey()) {
                        case "top" -> -1;
                        case "bottom" -> height;
                        default -> v;
                    };
                    for (int u = 0; u < r.width(); u++) {
                        var previous = atlas.put((r.y() + v) * 64 + r.x() + u,
                                new Pixel((String) box[0], (String) box[1], face.getKey(), row));
                        if (previous != null) {
                            throw new IllegalStateException("Overlapping skin faces at " + (r.x() + u) + "," + (r.y() + v));
                        }
                    }
                }
            }
        }
        return atlas;
    }

    /** Pixels of one layer ({@code base} or {@code outer}) of a model. */
    static boolean[] surface(String model, String surface) {
        var mask = new boolean[64 * 64];
        atlas(model).forEach((index, pixel) -> mask[index] = pixel.surface().equals(surface));
        return mask;
    }

    static int armWidth(String model) {
        return switch (model) {
            case "slim" -> 3;
            case "classic" -> 4;
            default -> throw new IllegalArgumentException("Unknown model " + model);
        };
    }

    /**
     * Moves the arms of a texture drawn for one model onto the other. Going from slim to classic the middle column of
     * the front, back, top and bottom faces is doubled; going back, the second column is dropped.
     */
    static Texture remap(Texture source, String from, String to) {
        Texture out = source.copy();
        if (from.equals(to)) {
            return out;
        }
        int[] columns = armWidth(from) == 3 ? new int[] {0, 1, 1, 2} : new int[] {0, 2, 3};
        for (int[] arm : ARMS) {
            out.clear(arm[0], arm[1], 16, 16);
            var old = faces(arm[0], arm[1], armWidth(from), 12, 4);
            var now = faces(arm[0], arm[1], armWidth(to), 12, 4);
            for (var face : old.entrySet()) {
                Rect s = face.getValue();
                Rect d = now.get(face.getKey());
                boolean resized = List.of("front", "back", "top", "bottom").contains(face.getKey());
                for (int v = 0; v < d.height(); v++) {
                    for (int u = 0; u < d.width(); u++) {
                        int column = resized ? columns[u] : u;
                        out.set(d.x() + u, d.y() + v, source.get(s.x() + column, s.y() + v));
                    }
                }
            }
        }
        return out;
    }
}
