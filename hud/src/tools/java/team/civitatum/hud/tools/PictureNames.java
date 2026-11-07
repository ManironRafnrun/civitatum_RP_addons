package team.civitatum.hud.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The names under which a mod's code may refer to its restyled pictures, matched against the text constants of a
 * class. Mods name a picture in many ways: the whole path ({@code textures/gui/cooking_pot.png}), the path without
 * {@code textures/} or the extension, a sprite name ({@code container/slot}), a bare file name that a helper puts into
 * a folder ({@code guigoblintinkering.png}, {@code rice_mill_gui}), or a pattern filled in at run time
 * ({@code textures/gui/dragonforge_%s.png}).
 */
final class PictureNames {
    /** A bare name this long is distinctive on its own; a shorter one counts only beside the mod's namespace. */
    private static final int DISTINCT_BARE_NAME = 8, MIN_BARE_NAME = 4;
    private static final String PNG = ".png";
    private static final Pattern FILLED = Pattern.compile("%[sd]");
    /** Bare names that are common words among constants (keys, field names) and point at nothing in particular. */
    private static final Set<String> GENERIC = Set.of("background", "container", "inventory", "widgets", "buttons",
        "settings", "generic", "default", "overlay", "scrollbar", "selected", "highlighted", "disabled", "slot");

    private final List<String> paths = new ArrayList<>();
    private final Set<String> exact = new HashSet<>();
    private final Map<String, Set<String>> bare = new HashMap<>();

    /**
     * A restyled picture, by its path inside {@code assets/<namespace>}. Vanilla's pictures go without bare names:
     * {@code furnace} or {@code crafting_table} are names of blocks as much as of screens.
     */
    void add(String namespace, String inAssets, boolean bareNames) {
        paths.add(inAssets);
        String inTextures = inAssets.substring("textures/".length());
        addWithoutExtension(inAssets);
        addWithoutExtension(inTextures);
        String inFolder = inTextures.substring(inTextures.indexOf('/') + 1);
        addWithoutExtension(inFolder);
        if (inFolder.startsWith("sprites/")) addWithoutExtension(inFolder.substring("sprites/".length()));
        if (!bareNames) return;
        String stem = inFolder.substring(inFolder.lastIndexOf('/') + 1, inFolder.length() - PNG.length());
        if (GENERIC.contains(stem)) return;
        if (stem.length() >= DISTINCT_BARE_NAME) {
            exact.add(stem);
            exact.add(stem + PNG);
        } else if (stem.length() >= MIN_BARE_NAME) {
            var names = bare.computeIfAbsent(namespace, k -> new HashSet<>());
            names.add(stem);
            names.add(stem + PNG);
        }
    }

    private void addWithoutExtension(String name) {
        exact.add(name);
        exact.add(name.substring(0, name.length() - PNG.length()));
    }

    /** Whether one of a class's text constants names a restyled picture. */
    boolean namedBy(Set<String> constants) {
        for (String constant : constants) {
            // a full resource location, "namespace:path"
            String name = constant.substring(constant.indexOf(':') + 1);
            if (exact.contains(name)) return true;
            if (FILLED.matcher(name).find() && (name.contains("textures/") || name.contains("gui/"))) {
                var pattern = Pattern.compile(FILLED.splitAsStream(name).map(Pattern::quote)
                    .reduce((a, b) -> a + "[^/]*" + b).orElse("") + (name.matches(".*%[sd]$") ? "[^/]*" : ""));
                if (paths.stream().anyMatch(path -> pattern.matcher(path).find())) return true;
            }
        }
        for (var mod : bare.entrySet()) {
            if (constants.contains(mod.getKey()) && mod.getValue().stream().anyMatch(constants::contains)) return true;
        }
        return false;
    }
}
