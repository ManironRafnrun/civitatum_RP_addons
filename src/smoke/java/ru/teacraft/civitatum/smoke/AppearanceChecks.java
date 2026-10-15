package ru.teacraft.civitatum.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import ru.teacraft.civitatum.*;
import ru.teacraft.civitatum.client.AppearanceTextures;
import java.util.*;

/** Exhaustive comparison against the authored pixels, not a copy of the compositing algorithm. */
public final class AppearanceChecks {
    private static NativeImage read(String path) throws java.io.IOException {
        try(var in=Minecraft.getInstance().getResourceManager().open(Civitatum.res(path))) {return NativeImage.read(in);}
    }
    public static void run() {
        int variants=0,channels=0;
        var assets=new ArrayList<>(AppearanceCatalog.hair());
        for(String sex:List.of("male","female"))for(String group:AppearanceCatalog.GROUPS)assets.addAll(AppearanceCatalog.clothes(sex,group));
        try {
            for(var a:assets)for(String model:List.of("classic","slim")) {
                var v=a.models().get(model);
                try(var original=read(v.directory()+"/original.png");var neutral=read(v.directory()+"/neutral.png");var result=new NativeImage(64,64,true)) {
                    AppearanceTextures.apply(result,a,model,0,0,0);
                    for(int y=0;y<64;y++)for(int x=0;x<64;x++)if((original.getPixelRGBA(x,y)>>>24)>0)
                        check(original.getPixelRGBA(x,y)==result.getPixelRGBA(x,y),a.id()+" original "+model);
                    variants++;
                    for(int i=0;i<3;i++) {
                        String key=AppearanceCatalog.CHANNELS.get(i);var control=a.controls().get(key);if(control==null)continue;
                        try(var mask=read(v.directory()+"/"+v.masks().get(key));var changed=new NativeImage(64,64,true)) {
                            int[] colors={0,0,0};colors[i]=control.colors().size()-1;AppearanceTextures.apply(changed,a,model,colors);
                            int tint=control.colors().get(colors[i]).rgb();boolean changedPixel=false;
                            for(int y=0;y<64;y++)for(int x=0;x<64;x++) {
                                int before=original.getPixelRGBA(x,y),after=changed.getPixelRGBA(x,y),base=neutral.getPixelRGBA(x,y);
                                check((before>>>24)==(after>>>24),a.id()+" alpha");if((before>>>24)==0)continue;
                                if((mask.getPixelRGBA(x,y)>>>24)==0)check(before==after,a.id()+" protected pixel");
                                else {
                                    check((after&255)==Math.round((base&255)*(tint>>16&255)/255f),a.id()+" red");
                                    check((after>>8&255)==Math.round((base>>8&255)*(tint>>8&255)/255f),a.id()+" green");
                                    check((after>>16&255)==Math.round((base>>16&255)*(tint&255)/255f),a.id()+" blue");
                                    changedPixel|=before!=after;
                                }
                            }
                            check(changedPixel,a.id()+" has a visible tint for "+key);channels++;
                        }
                    }
                }
            }
        } catch(java.io.IOException e){throw new IllegalStateException(e);}
        System.out.println("CCC_APPEARANCE_PIXELS_PASS variants="+variants+" independent_channels="+channels);
    }
    private static void check(boolean success,String context){if(!success)throw new IllegalStateException(context);}
}
