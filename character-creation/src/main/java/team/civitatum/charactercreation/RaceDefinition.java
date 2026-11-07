package team.civitatum.charactercreation;

public final class RaceDefinition {
    /** Texts live in the lang files under {@link #key}: name, description, positive, negative, ability, ability_name. */
    public String id, parent;
    public float minHeight, maxHeight;
    public int food, cooldown;
    public boolean magic, pointedEars, hornsAllowed, hornsRequired, tailAllowed, beardAllowed, hairAllowed = true;
    public boolean bootsAllowed = true;
    public String[] skinColors, hairColors, eyeColors, hornColors, tattooColors;
    public int[] earStyles, hornStyles;
    public int tattoos;
    /** Palettes as RGB, parsed from the hex strings once: skins, horns and hair are coloured every frame. */
    private transient int[] skinRgb, hairRgb, eyeRgb, hornRgb, tattooRgb;
    public boolean family() { return RaceCatalog.children(id).size() > 0; }
    /** A family is chosen through its subraces, which carry the abilities. */
    public boolean hasAbility() { return !family(); }
    public String key(String field) { return "race.civitatum." + id + "." + field; }
    public boolean elf() { return "elf".equals(parent); }
    public boolean dragon() { return "dragonborn".equals(parent); }
    public boolean algnor() { return "algnor".equals(parent); }
    public int color(String part, int index) {
        int[] colors = rgb(part);
        return colors[Math.clamp(index, 0, colors.length - 1)];
    }

    private int[] rgb(String part) {
        return switch (part) {
            case "hair" -> hairRgb != null ? hairRgb : (hairRgb = parse(hairColors));
            case "eyes" -> eyeRgb != null ? eyeRgb : (eyeRgb = parse(eyeColors));
            case "horn" -> hornRgb != null ? hornRgb : (hornRgb = parse(hornColors));
            case "tattoo" -> tattooRgb != null ? tattooRgb : (tattooRgb = parse(tattooColors));
            default -> skinRgb != null ? skinRgb : (skinRgb = parse(skinColors));
        };
    }

    /** Fills every palette now; the catalog calls it before the definition is shared between threads. */
    void bake() {
        for (String part : new String[]{"skin", "hair", "eyes", "horn", "tattoo"}) rgb(part);
    }

    private static int[] parse(String[] colors) {
        int[] rgb = new int[colors.length];
        for (int i = 0; i < colors.length; i++) rgb[i] = Integer.parseInt(colors[i].replace("#", ""), 16);
        return rgb;
    }
    public String[] palette(String part) {
        return switch (part) {
            case "hair" -> hairColors;
            case "eyes" -> eyeColors;
            case "horn" -> hornColors;
            case "tattoo" -> tattooColors;
            default -> skinColors;
        };
    }
}
