package team.civitatum.charactercreation;

/** Stable saved indices: zero always means none. Drawings use the normal skin UVs. */
public final class FacialHair {
    /** Styles of each kind, "none" included; names are {@code facial_hair.civitatum.<beard|mustache>.<index>}. */
    public static final int STYLES = 6;
    public static String beardKey(int style) { return "facial_hair.civitatum.beard." + style; }
    public static String mustacheKey(int style) { return "facial_hair.civitatum.mustache." + style; }
    private FacialHair(){}
}
