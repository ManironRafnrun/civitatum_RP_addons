package ru.teacraft.civitatum.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import ru.teacraft.civitatum.*;
import java.util.*;

/** Keeps the authored UVs and alpha; only pixels in the selected disjoint mask are tinted. */
public final class AppearanceTextures {
    private static final Map<String,NativeImage> ASSETS=new HashMap<>();
    private static final Map<ResourceLocation,NativeImage> SKINS=new LinkedHashMap<>();
    private static NativeImage image(String path) {
        return ASSETS.computeIfAbsent(path,key->{
            try(var in=Minecraft.getInstance().getResourceManager().open(Civitatum.res(key))) {return NativeImage.read(in);}
            catch(java.io.IOException e){throw new IllegalStateException("Missing appearance texture: "+key,e);}
        });
    }
    public static NativeImage ownSkin(ResourceLocation location) {
        NativeImage source=SKINS.get(location);
        if(source==null) {
            var texture=Minecraft.getInstance().getTextureManager().getTexture(location);
            int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                texture.bind();
                int w=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH);
                int h=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
                if(w<64 || h!=w || w>4096)return null;
                source=new NativeImage(w,h,false);source.downloadTexture(0,false);
            } finally {GlStateManager._bindTexture(previous);}
            SKINS.put(location,source);
            if(SKINS.size()>32){var it=SKINS.entrySet().iterator();var first=it.next();it.remove();first.getValue().close();}
        }
        var result=new NativeImage(source.getWidth(),source.getHeight(),false);result.copyFrom(source);return result;
    }
    public static void apply(NativeImage target,AppearanceCatalog.Asset asset,String model,int... colors) {
        var v=asset.models().get(model);if(v==null)v=asset.models().get("classic");
        var original=image(v.directory()+"/"+v.textures().get("original"));
        var neutral=image(v.directory()+"/"+v.textures().get("neutral"));
        NativeImage[] masks=new NativeImage[3];int[] tints=new int[3];
        for(int i=0;i<3;i++) {
            String key=AppearanceCatalog.CHANNELS.get(i);int n=AppearanceCatalog.color(asset,key,colors[i]);
            if(n==0 || !v.masks().containsKey(key))continue;
            masks[i]=image(v.directory()+"/"+v.masks().get(key));tints[i]=asset.controls().get(key).colors().get(n).rgb();
        }
        int scale=target.getWidth()/64;
        for(int y=0;y<64;y++)for(int x=0;x<64;x++) {
            int pixel=original.getPixelRGBA(x,y),alpha=pixel>>>24;if(alpha==0)continue;
            for(int i=0;i<3;i++)if(masks[i]!=null && (masks[i].getPixelRGBA(x,y)>>>24)>0) {
                int n=neutral.getPixelRGBA(x,y),t=tints[i];
                int red=Math.round((n&255)*(t>>16&255)/255f),green=Math.round((n>>8&255)*(t>>8&255)/255f),blue=Math.round((n>>16&255)*(t&255)/255f);
                pixel=(alpha<<24)|(blue<<16)|(green<<8)|red;break;
            }
            for(int dy=0;dy<scale;dy++)for(int dx=0;dx<scale;dx++) {
                int tx=x*scale+dx,ty=y*scale+dy;
                target.setPixelRGBA(tx,ty,over(pixel,target.getPixelRGBA(tx,ty)));
            }
        }
    }
    private static int over(int top,int bottom) {
        int a=top>>>24;if(a==255)return top;if(a==0)return bottom;
        float sa=a/255f,da=(bottom>>>24)/255f,out=sa+da*(1-sa);int result=Math.round(out*255)<<24;
        for(int shift=0;shift<=16;shift+=8)result|=Math.round(((top>>>shift&255)*sa+(bottom>>>shift&255)*da*(1-sa))/out)<<shift;
        return result;
    }
    public static void clear(){ASSETS.values().forEach(NativeImage::close);ASSETS.clear();SKINS.values().forEach(NativeImage::close);SKINS.clear();}
}
