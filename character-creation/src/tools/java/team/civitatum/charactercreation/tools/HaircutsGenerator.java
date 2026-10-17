package team.civitatum.charactercreation.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the haircuts: textures in {@code textures/character/haircuts/<id>/<slim|classic>/} and the catalog
 * {@code appearance/haircuts.json}, from {@code input/haircuts/haircuts.json} and the source skins it names.
 *
 * <p>For every style:
 * <ol>
 *   <li>the source skin is cleaned: pixels outside the skin layout and the cleanup listed in the metadata go;</li>
 *   <li>every pixel is put into a colour zone ({@code main}, {@code decor_1}, {@code decor_2}) by the style's
 *       {@link PixelRules rules} (main when none matches), or split between main and decor_1 by the gradient weight;</li>
 *   <li>a grey base ({@code neutral.png}) is made, which the mod tints per zone, and a mask per zone;</li>
 *   <li>gradient styles also get a smooth base and smooth per-zone weights;</li>
 *   <li>styles with a single zone get shade maps instead: a second hair field for their drawn shadows;</li>
 *   <li>all of it is written for the slim model and, with the arms moved, for the classic one.</li>
 * </ol>
 *
 * <p>Run with {@code ./gradlew :character-creation:generateHaircuts}, or start {@link #main} from the IDE in the
 * {@code character-creation} module folder.
 */
public final class HaircutsGenerator {
    private static final Path INPUT = Generation.INPUT.resolve("haircuts");
    private static final Path OUTPUT = Generation.TEXTURES.resolve("haircuts");
    private static final Path CATALOG = Generation.CATALOGS.resolve("haircuts.json");
    private static final String SOURCE_MODEL = "slim";
    private static final List<String> ZONES = List.of("main", "decor_1", "decor_2");

    private HaircutsGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Generation.checkWorkingDirectory();
        JsonObject meta = Generation.readJson(INPUT.resolve("haircuts.json"));
        Map<Integer, SkinLayout.Pixel> layout = SkinLayout.atlas(SOURCE_MODEL);

        Generation.clean(OUTPUT);
        JsonArray entries = new JsonArray();
        for (JsonElement element : meta.getAsJsonArray("styles")) {
            entries.add(style(meta, element.getAsJsonObject(), layout));
        }
        JsonObject catalog = new JsonObject();
        catalog.add("entries", entries);
        Generation.writeJson(CATALOG, catalog);
        System.out.println("Generated " + entries.size() + " haircuts in " + OUTPUT + " and " + CATALOG);
    }

    private static JsonObject style(JsonObject meta, JsonObject style, Map<Integer, SkinLayout.Pixel> layout)
            throws IOException {
        String id = style.get("id").getAsString();
        Texture art = clean(Texture.read(INPUT.resolve(style.get("source").getAsString())), style, layout);
        boolean[] drawn = art.occupied();

        // zones in their order in the metadata (main first)
        List<String> zones = new ArrayList<>();
        style.getAsJsonObject("zones").keySet().forEach(zones::add);
        if (!zones.getFirst().equals("main") || !ZONES.containsAll(zones)) {
            throw new IllegalStateException(id + ": zones must start with main and use " + ZONES);
        }
        Map<String, int[]> weights = weights(id, style, art, drawn, zones, layout);

        // the zone with the largest weight owns the pixel in the plain (non-gradient) masks
        Map<String, int[]> owned = new LinkedHashMap<>();
        zones.forEach(zone -> owned.put(zone, new int[weights.get(zone).length]));
        for (int i = 0; i < drawn.length; i++) {
            if (drawn[i]) {
                String best = zones.getFirst();
                for (String zone : zones) {
                    if (weights.get(zone)[i] > weights.get(best)[i]) {
                        best = zone;
                    }
                }
                owned.get(best)[i] = 255;
            }
        }
        for (String zone : zones) {
            if (Arrays.stream(owned.get(zone)).allMatch(w -> w == 0)) {
                throw new IllegalStateException(id + ": zone " + zone + " owns no pixel");
            }
        }

        Map<String, Integer> references = new LinkedHashMap<>();
        Texture neutral = neutral(art, owned, references);
        boolean gradient = style.has("gradient");
        Texture smoothNeutral = gradient ? neutral(art, weights, new LinkedHashMap<>()) : null;
        boolean shaded = zones.size() == 1;

        JsonObject controls = new JsonObject();
        for (String zone : zones) {
            JsonObject z = style.getAsJsonObject("zones").getAsJsonObject(zone);
            String kind = z.get("kind").getAsString();
            JsonObject control = new JsonObject();
            control.addProperty("label_ru", meta.getAsJsonObject("zone_labels").get(zone).getAsString());
            control.addProperty("affects_ru", z.get("description").getAsString());
            control.addProperty("kind", kind);
            control.add("palette", palette(meta, kind, !zone.equals("main"), references.get(zone)));
            controls.add(zone, control);
        }

        JsonObject models = new JsonObject();
        Integer darkReference = null;
        for (String model : SkinLayout.MODELS) {
            Path folder = OUTPUT.resolve(id).resolve(model);
            Texture original = SkinLayout.remap(art, SOURCE_MODEL, model);
            original.write(folder.resolve("original.png"));
            SkinLayout.remap(neutral, SOURCE_MODEL, model).write(folder.resolve("neutral.png"));

            JsonObject variant = new JsonObject();
            variant.addProperty("directory", "assets/civitatum/textures/character/haircuts/" + id + "/" + model);
            JsonObject textures = new JsonObject();
            textures.addProperty("original", "original.png");
            textures.addProperty("neutral", "neutral.png");
            variant.add("textures", textures);
            JsonObject masks = new JsonObject();
            JsonObject blending = null;

            if (shaded) {
                ShadeMaps maps = ShadeMaps.of(original);
                if (darkReference == null) {
                    darkReference = maps.darkReference();
                }
                maps.write(folder);
                masks.addProperty("main", "tone_mask_main.png");
                masks.addProperty("decor_1", "tone_mask_decor_1.png");
                blending = blending("tone_neutral.png", "tone_blend_main.png", "tone_blend_decor_1.png");
            } else {
                for (String zone : zones) {
                    SkinLayout.remap(mask(owned.get(zone)), SOURCE_MODEL, model).write(folder.resolve("mask_" + zone + ".png"));
                    masks.addProperty(zone, "mask_" + zone + ".png");
                }
                if (gradient) {
                    SkinLayout.remap(smoothNeutral, SOURCE_MODEL, model).write(folder.resolve("neutral_gradient.png"));
                    for (String zone : zones) {
                        SkinLayout.remap(mask(weights.get(zone)), SOURCE_MODEL, model).write(folder.resolve("blend_" + zone + ".png"));
                    }
                    blending = blending("neutral_gradient.png", "blend_main.png", "blend_decor_1.png");
                }
            }
            variant.add("masks", masks);
            if (blending != null) {
                variant.add("gradient_blending", blending);
            }
            models.add(model, variant);
        }

        if (shaded) {
            // a second hair field for the drawn shadows: the main palette, starting from their average colour
            JsonObject shade = controls.getAsJsonObject("main").deepCopy();
            shade.addProperty("label_ru", meta.getAsJsonObject("shade_zone").get("label").getAsString());
            shade.addProperty("affects_ru", meta.getAsJsonObject("shade_zone").get("description").getAsString());
            shade.getAsJsonArray("palette").get(0).getAsJsonObject().addProperty("rgb", hex(darkReference));
            controls.add("decor_1", shade);
        }

        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("name", style.get("name").getAsString());
        entry.addProperty("linked_shades", gradient || shaded || style.has("linked_shades") && style.get("linked_shades").getAsBoolean());
        entry.add("controls", controls);
        entry.add("models", models);
        return entry;
    }

    /** Drops pixels outside the skin layout, then the style's own cleanup. */
    private static Texture clean(Texture source, JsonObject style, Map<Integer, SkinLayout.Pixel> layout) {
        boolean[] inLayout = new boolean[Texture.SIZE * Texture.SIZE];
        layout.keySet().forEach(index -> inLayout[index] = true);
        Texture art = source.keep(inLayout);
        if (style.has("cleanup")) {
            JsonObject cleanup = style.getAsJsonObject("cleanup");
            if (cleanup.has("remove_pixels")) {
                for (JsonElement point : cleanup.getAsJsonArray("remove_pixels")) {
                    art.set(point.getAsJsonArray().get(0).getAsInt(), point.getAsJsonArray().get(1).getAsInt(), 0);
                }
            }
            if (cleanup.has("min_alpha")) {
                int min = cleanup.get("min_alpha").getAsInt();
                for (int y = 0; y < Texture.SIZE; y++) {
                    for (int x = 0; x < Texture.SIZE; x++) {
                        int alpha = art.get(x, y) >>> 24;
                        if (alpha > 0 && alpha < min) {
                            art.set(x, y, 0);
                        }
                    }
                }
            }
        }
        return art;
    }

    /** Per zone, 0-255 per pixel; the zones of a drawn pixel always add up to 255. */
    private static Map<String, int[]> weights(String id, JsonObject style, Texture art, boolean[] drawn, List<String> zones,
                                              Map<Integer, SkinLayout.Pixel> layout) {
        Map<String, int[]> weights = new LinkedHashMap<>();
        zones.forEach(zone -> weights.put(zone, new int[drawn.length]));

        PixelExpression weight = null, where = null;
        if (style.has("gradient")) {
            JsonObject gradient = style.getAsJsonObject("gradient");
            weight = PixelExpression.parse(gradient.get("weight").getAsString());
            where = gradient.has("where") ? PixelExpression.parse(gradient.get("where").getAsString()) : null;
            if (!zones.equals(List.of("main", "decor_1"))) {
                throw new IllegalStateException(id + ": a gradient needs exactly the zones main and decor_1");
            }
        }
        JsonObject main = new JsonObject();
        main.addProperty("zone", "main");
        PixelRules rules = new PixelRules(style.has("rules") ? style.getAsJsonArray("rules") : null, main);
        if (style.has("rules")) {
            for (JsonElement rule : style.getAsJsonArray("rules")) {
                String zone = rule.getAsJsonObject().get("zone").getAsString();
                if (!zones.contains(zone)) {
                    throw new IllegalStateException(id + ": rule puts pixels into unknown zone " + zone);
                }
            }
        }

        for (int i = 0; i < drawn.length; i++) {
            if (!drawn[i]) {
                continue;
            }
            int argb = art.get(i % Texture.SIZE, i / Texture.SIZE);
            var pixel = new PixelExpression.Pixel(argb >> 16 & 255, argb >> 8 & 255, argb & 255,
                    i % Texture.SIZE, i / Texture.SIZE, layout.get(i));
            if (weight != null) {
                double value = where == null || where.test(pixel) ? weight.number(pixel) : 0;
                int secondary = (int) Math.rint(Math.min(1, Math.max(0, value)) * 255);
                weights.get("main")[i] = 255 - secondary;
                weights.get("decor_1")[i] = secondary;
                continue;
            }
            weights.get(rules.match(pixel).get("zone").getAsString())[i] = 255;
        }
        return weights;
    }

    /**
     * The grey base: every pixel's brightness relative to the brightest colour of its zone(s), weighted by how much
     * of the pixel each zone owns. Fills {@code references} with that brightest colour per zone (the "original"
     * entry of the zone's palette).
     */
    private static Texture neutral(Texture art, Map<String, int[]> weights, Map<String, Integer> references) {
        double[] referenceLum = new double[Texture.SIZE * Texture.SIZE];
        for (Map.Entry<String, int[]> zone : weights.entrySet()) {
            int[] w = zone.getValue();
            int max = Arrays.stream(w).max().orElse(0);
            int reference = 0;
            double brightest = -1;
            for (int i = 0; i < w.length; i++) {
                if (w[i] == max) {
                    int argb = art.get(i % Texture.SIZE, i / Texture.SIZE);
                    double lum = Texture.luma(argb);
                    if (lum > brightest) {
                        brightest = lum;
                        reference = argb & 0xFFFFFF;
                    }
                }
            }
            references.put(zone.getKey(), reference);
            double referenceBrightness = Texture.luma(reference);
            for (int i = 0; i < w.length; i++) {
                referenceLum[i] += w[i] / 255.0 * referenceBrightness;
            }
        }
        Texture out = Texture.empty();
        for (int i = 0; i < referenceLum.length; i++) {
            int argb = art.get(i % Texture.SIZE, i / Texture.SIZE);
            if (argb >>> 24 == 0) {
                continue;
            }
            int grey = (int) Math.min(255, Math.max(0, Math.rint(Texture.luma(argb) / Math.max(referenceLum[i], 1) * 255)));
            out.set(i % Texture.SIZE, i / Texture.SIZE, argb & 0xFF000000 | grey << 16 | grey << 8 | grey);
        }
        return out;
    }

    /** "Original" (the zone's own brightest colour) followed by the fixed palette of the zone's kind. */
    private static JsonArray palette(JsonObject meta, String kind, boolean secondary, int reference) {
        JsonArray palette = new JsonArray();
        palette.add(colour("Исходный", hex(reference)));
        JsonObject palettes = meta.getAsJsonObject("palettes");
        List<JsonElement> choices = new ArrayList<>();
        palettes.getAsJsonArray(kind).forEach(choices::add);
        if (secondary && kind.equals("hair")) {
            palettes.getAsJsonArray("hair_secondary_extra").forEach(choices::add);
        }
        for (JsonElement choice : choices) {
            palette.add(colour(choice.getAsJsonObject().get("label").getAsString(), choice.getAsJsonObject().get("rgb").getAsString()));
        }
        return palette;
    }

    private static JsonObject colour(String label, String rgb) {
        JsonObject colour = new JsonObject();
        colour.addProperty("label_ru", label);
        colour.addProperty("rgb", rgb);
        return colour;
    }

    private static JsonObject blending(String neutral, String main, String decor) {
        JsonObject blending = new JsonObject();
        blending.addProperty("neutral", neutral);
        JsonObject masks = new JsonObject();
        masks.addProperty("main", main);
        masks.addProperty("decor_1", decor);
        blending.add("masks", masks);
        return blending;
    }

    /** White where the weight is above 0, with the weight as alpha. */
    private static Texture mask(int[] weight) {
        Texture out = Texture.empty();
        for (int i = 0; i < weight.length; i++) {
            out.set(i % Texture.SIZE, i / Texture.SIZE, weight[i] << 24 | (weight[i] > 0 ? 0xFFFFFF : 0));
        }
        return out;
    }

    static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /**
     * For a style drawn in one colour: splits it by brightness into the main colour and its shadows, so both can be
     * tinted, with smooth weights, plain masks and a grey base.
     */
    private record ShadeMaps(Texture blendMain, Texture blendShadow, Texture maskMain, Texture maskShadow,
                             Texture neutral, int darkReference) {
        static ShadeMaps of(Texture original) {
            int n = Texture.SIZE * Texture.SIZE;
            List<Double> levels = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (original.opaque(i)) {
                    levels.add(Texture.luma(pixel(original, i)));
                }
            }
            levels.sort(null);
            // the darkest and brightest fifth are clipped: shadows below 20 %, highlights above 80 %
            double low = levels.get(levels.size() / 5), high = levels.get(levels.size() * 4 / 5);
            if (high - low < 1) {
                low = levels.getFirst();
                high = levels.getLast() + 1;
            }
            Texture blendMain = Texture.empty(), blendShadow = Texture.empty(), maskMain = Texture.empty(),
                    maskShadow = Texture.empty(), neutral = Texture.empty();
            long[] dark = new long[3];
            int darkCount = 0;
            for (int i = 0; i < n; i++) {
                int argb = pixel(original, i);
                int alpha = argb >>> 24;
                boolean drawn = alpha != 0;
                double light = Texture.luma(argb);
                int main = drawn ? (int) Math.rint(Math.max(0, Math.min(1, (light - low) / (high - low))) * 255) : 0;
                int shadow = drawn ? 255 - main : 0;
                set(blendMain, i, main << 24 | 0xFFFFFF);
                set(blendShadow, i, shadow << 24 | 0xFFFFFF);
                set(maskMain, i, (drawn && main >= shadow ? 255 : 0) << 24 | 0xFFFFFF);
                set(maskShadow, i, (drawn && main < shadow ? 255 : 0) << 24 | 0xFFFFFF);
                double reference = drawn ? (main * high + shadow * low) / 255 : 1;
                int value = drawn ? (int) Math.rint(Math.min(255, light / Math.max(1, reference) * 235)) : 0;
                set(neutral, i, alpha << 24 | value << 16 | value << 8 | value);
                if (drawn && light <= low) {
                    dark[0] += argb >> 16 & 255;
                    dark[1] += argb >> 8 & 255;
                    dark[2] += argb & 255;
                    darkCount++;
                }
            }
            int darkReference = 0;
            for (int c = 0; c < 3; c++) {
                darkReference = darkReference << 8 | (int) Math.rint((double) dark[c] / darkCount);
            }
            return new ShadeMaps(blendMain, blendShadow, maskMain, maskShadow, neutral, darkReference);
        }

        void write(Path folder) throws IOException {
            blendMain.write(folder.resolve("tone_blend_main.png"));
            blendShadow.write(folder.resolve("tone_blend_decor_1.png"));
            maskMain.write(folder.resolve("tone_mask_main.png"));
            maskShadow.write(folder.resolve("tone_mask_decor_1.png"));
            neutral.write(folder.resolve("tone_neutral.png"));
        }

        private static int pixel(Texture t, int i) {
            return t.get(i % Texture.SIZE, i / Texture.SIZE);
        }

        private static void set(Texture t, int i, int argb) {
            t.set(i % Texture.SIZE, i / Texture.SIZE, argb);
        }
    }
}
