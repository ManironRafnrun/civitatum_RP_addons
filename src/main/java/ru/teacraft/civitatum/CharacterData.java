package ru.teacraft.civitatum;

/** Only cosmetic choices. Race rules and numeric bounds are owned by the server. */
public final class CharacterData {
    public boolean selected;
    public String race = "human", sex = "male", name = "";
    public float height = 1.8f;
    public int skin, hair, eyes, hornColor, tattooColor;
    public int ears, horns, tail, beard, hairStyle, tattoo;
    public boolean usePlayerSkin, glow = true;
    public String hairAsset = "", outfitPreset = "";
    public int hairMain, hairDecor1, hairDecor2;
    public boolean wardrobeEnabled;
    public java.util.Map<String,Garment> wardrobe = new java.util.LinkedHashMap<>();
    public static final class Garment {
        public String asset = "";
        public int main, decor1, decor2;
        public Garment(String asset) { this.asset=asset; }
    }
    public float earPitch, earYaw, earRoll, earOffset, hornPitch, hornYaw, hornRoll, hornOffset, hornSize = 1;
    public boolean hornOnForehead;
    public float bust = .6f, bustX, bustY, bustZ, cleavage, bounce = .333f, floppiness = .75f;
    public boolean breastPhysics = true, uniboob = true, showInArmor = true, armorPhysics;
    public CharacterData copy() { return DataJson.GSON.fromJson(DataJson.GSON.toJson(this), CharacterData.class); }
    public boolean female() { return "female".equals(sex); }
    public String json() { return DataJson.GSON.toJson(this); }
}
