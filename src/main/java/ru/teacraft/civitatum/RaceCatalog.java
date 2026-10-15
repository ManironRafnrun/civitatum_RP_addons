package ru.teacraft.civitatum;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RaceCatalog {
    private static final LinkedHashMap<String, RaceDefinition> ALL = load();
    private static LinkedHashMap<String, RaceDefinition> load() {
        try (var in = RaceCatalog.class.getResourceAsStream("/races.json")) {
            if (in == null) throw new IllegalStateException("Missing races.json");
            RaceDefinition[] definitions = DataJson.GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), RaceDefinition[].class);
            var result = new LinkedHashMap<String, RaceDefinition>();
            for (var r : definitions) {
                for (String part : List.of("skin","hair","eyes","horn","tattoo")) {
                    var palette = r.palette(part);
                    if (palette == null || palette.length == 0 || palette.length > 6)
                        throw new IllegalStateException("Invalid palette: " + r.id + "/" + part);
                    for (String color : palette) if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalStateException("Invalid color");
                }
                if (r.minHeight < .9f || r.maxHeight > 2.5f || r.minHeight > r.maxHeight) throw new IllegalStateException("Invalid height");
                if (result.put(r.id, r) != null) throw new IllegalStateException("Duplicate race");
            }
            return result;
        } catch (Exception e) { throw new IllegalStateException("Cannot read race catalog", e); }
    }
    public static Collection<RaceDefinition> all() { return Collections.unmodifiableCollection(ALL.values()); }
    public static RaceDefinition get(String id) { return ALL.get(id); }
    public static List<RaceDefinition> roots() { return ALL.values().stream().filter(r -> r.parent == null).toList(); }
    public static List<RaceDefinition> children(String id) { return ALL.values().stream().filter(r -> id.equals(r.parent)).toList(); }
    public static CharacterData defaults(String id, String sex) {
        RaceDefinition r = Objects.requireNonNull(get(id));
        var c = new CharacterData();
        c.race = id; c.sex = sex; c.selected = true;
        c.height = Math.round((r.minHeight + r.maxHeight) * 10) / 20f;
        c.ears = r.earStyles[0]; c.horns = r.hornsRequired ? r.hornStyles[0] : 0;
        if (r.hornsRequired && c.horns == 0) c.horns = r.hornStyles[r.hornStyles.length - 1];
        c.hairStyle = r.hairAllowed ? 1 : 0;
        if (r.algnor() && c.female()) c.horns = 0;
        return validate(c);
    }
    public static CharacterData validate(CharacterData input) {
        Objects.requireNonNull(input, "character");
        var c = input.copy();
        var r = Objects.requireNonNull(get(c.race), "unknown race");
        if (r.family()) throw new IllegalArgumentException("Нужно выбрать подрасу");
        if (!List.of("male", "female").contains(c.sex)) throw new IllegalArgumentException("Неизвестный пол");
        c.name = c.name == null ? "" : c.name.strip().replaceAll("[\\p{Cntrl}§]", "");
        if (c.name.length() > 32) c.name = c.name.substring(0, 32);
        c.height = finite(c.height, r.minHeight, r.maxHeight, (r.minHeight + r.maxHeight) / 2);
        c.skin = paletteIndex(c.skin, r.skinColors); c.hair = paletteIndex(c.hair, r.hairColors);
        c.eyes = paletteIndex(c.eyes, r.eyeColors); c.hornColor = paletteIndex(c.hornColor, r.hornColors);
        c.tattooColor = paletteIndex(c.tattooColor, r.tattooColors);
        c.ears = contains(r.earStyles, c.ears) ? c.ears : r.earStyles[0];
        if (!r.hornsAllowed) c.horns = 0;
        else {
            boolean mayNone = !r.hornsRequired || (r.algnor() && c.female());
            if (c.horns == 0 && !mayNone) c.horns = r.hornStyles[0];
            else if (c.horns != 0 && !contains(r.hornStyles, c.horns)) c.horns = r.hornStyles[0];
        }
        c.tail = r.tailAllowed ? clamp(c.tail, 0, 1) : 0;
        c.beard = r.beardAllowed ? clamp(c.beard, 0, 3) : 0;
        c.hairStyle = r.hairAllowed ? clamp(c.hairStyle, 0, 4) : 0;
        c.tattoo = clamp(c.tattoo, 0, r.tattoos);
        if (!"orthosian".equals(r.id) && !"jahar".equals(r.id)) c.glow = false;
        c.earPitch = angle(c.earPitch); c.earYaw = angle(c.earYaw); c.earRoll = angle(c.earRoll);
        c.hornPitch = angle(c.hornPitch); c.hornYaw = angle(c.hornYaw); c.hornRoll = angle(c.hornRoll);
        c.earOffset = finite(c.earOffset, -2, 2, 0); c.hornOffset = finite(c.hornOffset, -.2f, 2, 0);
        c.hornSize = finite(c.hornSize, .65f, 1.35f, 1);
        c.bust = finite(c.bust, 0, .8f, .6f);
        c.bustX = finite(c.bustX, -1, 1, 0); c.bustY = finite(c.bustY, -1, 1, 0);
        c.bustZ = finite(c.bustZ, -1, 0, 0); c.cleavage = finite(c.cleavage, 0, .1f, 0);
        c.bounce = finite(c.bounce, 0, .5f, .333f); c.floppiness = finite(c.floppiness, .25f, 1, .75f);
        if (!c.female() || "forged".equals(c.race)) c.bust = 0;
        AppearanceCatalog.validate(c,r.hairAllowed);
        return c;
    }
    static int paletteIndex(int index, String[] palette) {
        if (index < 0 || index >= palette.length) throw new IllegalArgumentException("Цвет не входит в палитру");
        return index;
    }
    public static float finite(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    static float angle(float value) { return finite(value, -60, 60, 0); }
    static int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
    static boolean contains(int[] values, int n) { for (int v : values) if (v == n) return true; return false; }
}
