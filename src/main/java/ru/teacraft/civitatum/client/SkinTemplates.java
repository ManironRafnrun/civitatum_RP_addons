package ru.teacraft.civitatum.client;

import java.util.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import ru.teacraft.civitatum.*;

/** Race template or original player skin, composited with the authored clothing and hair UVs. */
public final class SkinTemplates {
    private static final Map<String,net.minecraft.resources.ResourceLocation> CACHE=new LinkedHashMap<>();
    private static long nextId;
    public static PlayerSkin skin(CharacterData c,PlayerSkin original) {
        if(!c.selected || (c.usePlayerSkin && c.hairAsset.isEmpty() && (!c.wardrobeEnabled || c.wardrobe.isEmpty()))) return original;
        var race=RaceCatalog.get(c.race);
        if(race==null) return original;
        String key=c.race+"_"+c.sex+"_"+c.skin+"_"+c.hair+"_"+c.eyes+"_"+c.hairStyle+"_"+original.model().id()+"_"+c.usePlayerSkin+"_"+original.texture()+"_"+c.hairAsset+"_"+c.hairMain+"_"+c.hairDecor1+"_"+c.hairDecor2+"_"+c.wardrobeEnabled+"_"+DataJson.GSON.toJson(c.wardrobe);
        var location=CACHE.get(key);
        if(location==null) {
            NativeImage image=c.usePlayerSkin?AppearanceTextures.ownSkin(original.texture()):new NativeImage(64,64,false);
            if(image==null)return original;
            if(!c.usePlayerSkin) {
                int skin=race.color("skin",c.skin), hair=race.color("hair",c.hair), eyes=race.color("eyes",c.eyes);
                // Base head, body, limbs. Clothing occupies the vanilla body/leg regions.
                fill(image,0,0,64,64,0);
                fill(image,0,0,32,16,argb(skin));
                fill(image,0,16,56,16,argb(skin));
                fill(image,16,48,32,16,argb(skin));
                if(!c.wardrobeEnabled) {
                    fill(image,16,16,24,16,argb(0x6C7075));
                    fill(image,0,16,16,16,argb(0x484F5C));
                    fill(image,16,48,16,16,argb(0x484F5C));
                } else {
                    // Technical base underwear until the separate authored underwear archive is supplied.
                    fill(image,16,28,24,4,argb(0xD6C7AB));
                    fill(image,0,16,16,6,argb(0xD6C7AB));fill(image,16,48,16,6,argb(0xD6C7AB));
                    if(c.female())fill(image,16,22,24,3,argb(0xD6C7AB));
                }
                // Tunic front collar; hands are uncovered.
                fill(image,21,20,6,2,argb(skin));
                if(c.hairAsset.isEmpty() && c.hairStyle!=0 && race.hairAllowed) {
                    fill(image,0,0,32,8,argb(hair)); fill(image,0,8,8,4,argb(hair));
                    fill(image,16,8,16,4,argb(hair));
                    fill(image,8,8,8,c.hairStyle==2 ? 3 : 1,argb(hair));
                }
                fill(image,9,11,2,1,argb(0xEFEDE6)); fill(image,13,11,2,1,argb(0xEFEDE6));
                fill(image,10,11,1,1,argb(eyes)); fill(image,13,11,1,1,argb(eyes));
                fill(image,11,14,2,1,argb(shade(skin,.7f)));
                if(race.id.equals("forged")) {
                    fill(image,20,20,8,12,argb(skin));
                    for(int y=20;y<32;y+=4) fill(image,20,y,8,1,argb(shade(skin,.55f)));
                    fill(image,23,24,2,2,argb(eyes));
                }
                if(race.id.equals("hobbit") || race.algnor()) {
                    fill(image,0,26,16,6,argb(skin)); fill(image,16,58,16,6,argb(skin));
                }
            }
            if(c.wardrobeEnabled)for(String group:AppearanceCatalog.GROUPS) {
                var choice=c.wardrobe.get(group);var asset=choice==null?null:AppearanceCatalog.clothing(choice.asset);
                if(asset!=null && asset.permits(c.sex))AppearanceTextures.apply(image,asset,original.model().id(),choice.main,choice.decor1,choice.decor2);
            }
            var hairAsset=AppearanceCatalog.hair(c.hairAsset);
            if(hairAsset!=null && race.hairAllowed)AppearanceTextures.apply(image,hairAsset,original.model().id(),c.hairMain,c.hairDecor1,c.hairDecor2);
            var texture=new DynamicTexture(image);
            texture.setFilter(false,false);
            location=Civitatum.res("generated/skin/"+Integer.toUnsignedString(key.hashCode(),16)+"_"+nextId++);
            Minecraft.getInstance().getTextureManager().register(location,texture);
            CACHE.put(key,location);
            if(CACHE.size()>256) {
                var first=CACHE.entrySet().iterator();
                var removed=first.next(); first.remove();
                Minecraft.getInstance().getTextureManager().release(removed.getValue());
            }
        }
        return new PlayerSkin(location,original.textureUrl(),original.capeTexture(),original.elytraTexture(),original.model(),original.secure());
    }
    private static void fill(NativeImage image,int x,int y,int width,int height,int color) {
        for(int yy=y;yy<y+height;yy++) for(int xx=x;xx<x+width;xx++) image.setPixelRGBA(xx,yy,color);
    }
    private static int argb(int rgb) { return 0xFF000000 | (rgb & 255)<<16 | rgb&0xFF00 | (rgb>>16)&255; }
    private static int shade(int rgb,float multiplier) {
        return ((int)((rgb>>16&255)*multiplier)<<16)|((int)((rgb>>8&255)*multiplier)<<8)|(int)((rgb&255)*multiplier);
    }
    public static void clear() {
        for(var location:CACHE.values()) Minecraft.getInstance().getTextureManager().release(location);
        CACHE.clear();AppearanceTextures.clear();
    }
}
