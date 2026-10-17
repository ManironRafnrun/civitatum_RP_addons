package team.civitatum.charactercreation;

public final class RaceDefinition {
    public String id, name, parent, description, positive, negative, active;
    public float minHeight, maxHeight;
    public int food, cooldown;
    public boolean magic, pointedEars, hornsAllowed, hornsRequired, tailAllowed, beardAllowed, hairAllowed = true;
    public boolean bootsAllowed = true;
    public String[] skinColors, hairColors, eyeColors, hornColors, tattooColors;
    public int[] earStyles, hornStyles;
    public int tattoos;
    public boolean family() { return RaceCatalog.children(id).size() > 0; }
    public boolean elf() { return "elf".equals(parent); }
    public boolean dragon() { return "dragonborn".equals(parent); }
    public boolean algnor() { return "algnor".equals(parent); }
    public int color(String part, int index) {
        String[] colors = palette(part);
        return Integer.parseInt(colors[Math.max(0, Math.min(index, colors.length - 1))].replace("#", ""), 16);
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
