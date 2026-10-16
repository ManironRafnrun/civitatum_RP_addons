package ru.teacraft.civitatum;

/** The item editor has a server-enforced allowlist, independent of which UI sent a packet. */
public final class AppearanceRules {
    public static void check(CharacterData saved,CharacterData incoming,EditorMode mode) {
        if(mode==EditorMode.CREATE)return;
        if(!saved.race.equals(incoming.race))throw new IllegalArgumentException("В этом редакторе нельзя менять расу");
        if(mode==EditorMode.FULL)return;
        var allowed=saved.copy();
        allowed.name=incoming.name;
        allowed.hair=incoming.hair;allowed.hairStyle=incoming.hairStyle;allowed.hairAsset=incoming.hairAsset;
        allowed.hairMain=incoming.hairMain;allowed.hairDecor1=incoming.hairDecor1;allowed.hairDecor2=incoming.hairDecor2;
        allowed.hairLinkedShades=incoming.hairLinkedShades;allowed.mustache=incoming.mustache;
        allowed.beard=incoming.beard;allowed.tattoo=incoming.tattoo;allowed.tattooColor=incoming.tattooColor;allowed.glow=incoming.glow;
        allowed.wardrobe=incoming.wardrobe;allowed.wardrobeEnabled=incoming.wardrobeEnabled;allowed.outfitPreset=incoming.outfitPreset;
        allowed.underwearColor=incoming.underwearColor;allowed.naked=incoming.naked;
        if(!DataJson.GSON.toJsonTree(allowed).equals(DataJson.GSON.toJsonTree(incoming)))
            throw new IllegalArgumentException("Предмет меняет только имя, причёску, бороду, одежду и татуировки");
    }
}
