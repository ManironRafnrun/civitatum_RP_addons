package ru.teacraft.civitatum;

/** Hide entire underwear pieces, including their outer skin-layer pixels, under matching garments. */
public record UnderwearVisibility(boolean upper,boolean lower) {
    public static UnderwearVisibility of(CharacterData c) {
        boolean upper=true,lower=true;
        if(!c.naked && c.wardrobeEnabled && c.wardrobe!=null)for(String group:AppearanceCatalog.GROUPS) {
            var choice=c.wardrobe.get(group);var asset=choice==null?null:AppearanceCatalog.clothing(choice.asset);
            if(asset==null || !asset.permits(c.sex) || !asset.group().equals(group))continue;
            switch(group) {
                case "trousers","skirt" -> lower=false;
                case "shirt","vest","coat","corset" -> upper=false;
            }
            if(asset.id().endsWith("/dress"))lower=false;
        }
        return new UnderwearVisibility(upper,lower);
    }
    public boolean visible(int x,int y) {return upperPixel(x,y)?upper:lower;}
    /** 64px player UV: chest side faces and shoulders; waist, torso bottom and leg UVs are lower. */
    public static boolean upperPixel(int x,int y) {
        return x>=16 && x<40 && ((y>=20 && y<28) || (y>=36 && y<44)
            || (x>=20 && x<28 && ((y>=16 && y<20) || (y>=32 && y<36))));
    }
}
