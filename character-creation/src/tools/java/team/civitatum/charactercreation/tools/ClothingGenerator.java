package team.civitatum.charactercreation.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the clothing: textures in {@code textures/character/clothing/<gender>/<outfit>/<slim|classic>/<piece>/}
 * and the catalog {@code appearance/clothing.json}, from {@code input/clothing/clothing.json} and the source skins it
 * names.
 *
 * <p>For every outfit:
 * <ol>
 *   <li>the source skin is limited to the body, arms and legs (clothing never covers the head);</li>
 *   <li>every pixel is put into a garment ({@code piece}: coat, boots ...) and a colour field ({@code slot}: main,
 *       decor_1, decor_2, or fixed - never tinted) by the outfit's {@link PixelRules rules};</li>
 *   <li>each garment becomes its own item: its pixels, a grey base the mod tints per field and a mask per field;</li>
 *   <li>all of it is written for the model the skin was drawn for and, with the arms moved, for the other one.</li>
 * </ol>
 *
 * <p>Run with {@code ./gradlew :character-creation:generateClothing}, or start {@link #main} from the IDE in the
 * {@code character-creation} module folder.
 */
public final class ClothingGenerator {
    private static final Path INPUT = Generation.INPUT.resolve("clothing");
    private static final Path OUTPUT = Generation.TEXTURES.resolve("clothing");
    private static final Path CATALOG = Generation.CATALOGS.resolve("clothing.json");
    private static final List<String> TINTED = List.of("main", "decor_1", "decor_2");
    private static final String FIXED = "fixed";

    private ClothingGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Generation.checkWorkingDirectory();
        JsonObject meta = Generation.readJson(INPUT.resolve("clothing.json"));

        Generation.clean(OUTPUT);
        JsonArray entries = new JsonArray();
        int items = 0;
        for (JsonElement element : meta.getAsJsonArray("outfits")) {
            JsonObject entry = outfit(meta, element.getAsJsonObject());
            items += entry.getAsJsonObject("items").size();
            entries.add(entry);
        }
        JsonObject catalog = new JsonObject();
        catalog.add("entries", entries);
        Generation.writeJson(CATALOG, catalog);
        System.out.println("Generated " + entries.size() + " outfits (" + items + " items) in " + OUTPUT + " and " + CATALOG);
    }

    private static JsonObject outfit(JsonObject meta, JsonObject outfit) throws IOException {
        String id = outfit.get("id").getAsString();
        String gender = outfit.get("gender").getAsString();
        String sourceModel = outfit.get("source_model").getAsString();

        Map<Integer, SkinLayout.Pixel> layout = SkinLayout.atlas(sourceModel);
        boolean[] body = new boolean[Texture.SIZE * Texture.SIZE];
        layout.forEach((index, pixel) -> body[index] = !pixel.group().equals("head"));
        Texture art = Texture.read(INPUT.resolve(outfit.get("source").getAsString())).keep(body);

        // piece and slot of every drawn pixel; pieces in the order they first appear
        PixelRules rules = new PixelRules(outfit.getAsJsonArray("rules"),
                outfit.has("default") ? outfit.getAsJsonObject("default") : null);
        String[] pieceOf = new String[body.length];
        String[] slotOf = new String[body.length];
        List<String> pieces = new ArrayList<>();
        for (int i = 0; i < body.length; i++) {
            int argb = art.get(i % Texture.SIZE, i / Texture.SIZE);
            if (argb >>> 24 == 0) {
                continue;
            }
            JsonObject result = rules.match(new PixelExpression.Pixel(argb >> 16 & 255, argb >> 8 & 255, argb & 255,
                    i % Texture.SIZE, i / Texture.SIZE, layout.get(i)));
            pieceOf[i] = result.get("piece").getAsString();
            slotOf[i] = result.get("slot").getAsString();
            if (!TINTED.contains(slotOf[i]) && !slotOf[i].equals(FIXED)) {
                throw new IllegalStateException(id + ": unknown slot " + slotOf[i]);
            }
            if (!meta.getAsJsonObject("piece_labels").has(pieceOf[i])) {
                throw new IllegalStateException(id + ": unknown piece " + pieceOf[i]);
            }
            if (!pieces.contains(pieceOf[i])) {
                pieces.add(pieceOf[i]);
            }
        }

        JsonObject items = new JsonObject();
        for (String piece : pieces) {
            items.add(piece, item(meta, outfit, piece, art, pieceOf, slotOf, sourceModel));
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("name", outfit.get("name").getAsString());
        entry.addProperty("gender", gender);
        entry.add("items", items);
        return entry;
    }

    private static JsonObject item(JsonObject meta, JsonObject outfit, String piece, Texture art, String[] pieceOf,
                                   String[] slotOf, String sourceModel) throws IOException {
        String id = outfit.get("id").getAsString();
        boolean[] own = new boolean[pieceOf.length];
        for (int i = 0; i < own.length; i++) {
            own[i] = piece.equals(pieceOf[i]);
        }
        Texture original = art.keep(own);

        // grey base per tinted field: brightness relative to the field's brightest colour; fixed pixels stay as drawn
        Texture neutral = original.copy();
        Map<String, boolean[]> masks = new LinkedHashMap<>();
        Map<String, Integer> references = new LinkedHashMap<>();
        for (String slot : TINTED) {
            boolean[] field = new boolean[own.length];
            boolean any = false;
            for (int i = 0; i < own.length; i++) {
                field[i] = own[i] && slot.equals(slotOf[i]);
                any |= field[i];
            }
            if (!any) {
                continue;
            }
            int reference = 0;
            double brightest = -1;
            for (int i = 0; i < field.length; i++) {
                if (field[i]) {
                    int argb = original.get(i % Texture.SIZE, i / Texture.SIZE);
                    if (Texture.luma(argb) > brightest) {
                        brightest = Texture.luma(argb);
                        reference = argb & 0xFFFFFF;
                    }
                }
            }
            double scale = Math.max(Texture.luma(reference), 1);
            for (int i = 0; i < field.length; i++) {
                if (field[i]) {
                    int argb = original.get(i % Texture.SIZE, i / Texture.SIZE);
                    int grey = (int) Math.min(255, Math.max(0, Math.rint(Texture.luma(argb) / scale * 255)));
                    neutral.set(i % Texture.SIZE, i / Texture.SIZE, argb & 0xFF000000 | grey << 16 | grey << 8 | grey);
                }
            }
            masks.put(slot, field);
            references.put(slot, reference);
        }
        if (!masks.containsKey("main")) {
            throw new IllegalStateException(id + "/" + piece + ": a garment needs pixels in its main field");
        }

        JsonObject descriptions = outfit.has("descriptions") && outfit.getAsJsonObject("descriptions").has(piece)
                ? outfit.getAsJsonObject("descriptions").getAsJsonObject(piece) : new JsonObject();
        String pieceLabel = meta.getAsJsonObject("piece_labels").get(piece).getAsString();
        JsonObject controls = new JsonObject();
        for (String slot : masks.keySet()) {
            JsonObject control = new JsonObject();
            control.addProperty("label_ru", meta.getAsJsonObject("slot_labels").get(slot).getAsString());
            control.addProperty("affects_ru", descriptions.has(slot) ? descriptions.get(slot).getAsString() : pieceLabel);
            control.add("palette", palette(meta, outfit, piece, slot, references.get(slot)));
            controls.add(slot, control);
        }

        JsonObject models = new JsonObject();
        for (String model : SkinLayout.MODELS) {
            Path folder = OUTPUT.resolve(outfit.get("gender").getAsString()).resolve(id).resolve(model).resolve(piece);
            SkinLayout.remap(original, sourceModel, model).write(folder.resolve("original.png"));
            SkinLayout.remap(neutral, sourceModel, model).write(folder.resolve("neutral.png"));
            JsonObject maskFiles = new JsonObject();
            for (var mask : masks.entrySet()) {
                SkinLayout.remap(Texture.mask(mask.getValue()), sourceModel, model)
                        .write(folder.resolve("mask_" + mask.getKey() + ".png"));
                maskFiles.addProperty(mask.getKey(), "mask_" + mask.getKey() + ".png");
            }
            JsonObject textures = new JsonObject();
            textures.addProperty("original", "original.png");
            textures.addProperty("neutral", "neutral.png");
            JsonObject variant = new JsonObject();
            variant.addProperty("directory", "assets/civitatum/textures/character/clothing/"
                    + outfit.get("gender").getAsString() + "/" + id + "/" + model + "/" + piece);
            variant.add("textures", textures);
            variant.add("masks", maskFiles);
            models.add(model, variant);
        }

        JsonObject item = new JsonObject();
        item.addProperty("label_ru", pieceLabel);
        item.add("controls", controls);
        item.add("models", models);
        return item;
    }

    /** "Original" (the field's brightest colour), then the outfit's own palette for this field or the shared one. */
    private static JsonArray palette(JsonObject meta, JsonObject outfit, String piece, String slot, int reference) {
        JsonArray choices;
        String key = piece + "/" + slot;
        if (outfit.has("palettes") && outfit.getAsJsonObject("palettes").has(key)) {
            choices = outfit.getAsJsonObject("palettes").getAsJsonArray(key);
        } else {
            boolean leather = false;
            for (JsonElement p : meta.getAsJsonArray("leather_pieces")) {
                leather |= p.getAsString().equals(piece);
            }
            String shared = slot.equals("main") && leather ? "leather" : slot;
            choices = meta.getAsJsonObject("palettes").getAsJsonArray(shared);
        }
        JsonArray palette = new JsonArray();
        palette.add(colour("Исходный", HaircutsGenerator.hex(reference)));
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
}
