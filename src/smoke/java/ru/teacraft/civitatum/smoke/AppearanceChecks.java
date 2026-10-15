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
        underwear();
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
    private static void underwear() {
        // Authored bra: base torso band + jacket front. Briefs: torso bottom/waist + both leg layers.
        Set<Integer> femaleUpper=new HashSet<>();
        for(int y:new int[]{23,24,25})for(int x=16;x<40;x++)femaleUpper.add(y*64+x);
        for(int[] p:new int[][]{{21,39},{26,39},{21,40},{22,40},{25,40},{26,40},{22,41},{25,41}})femaleUpper.add(p[1]*64+p[0]);
        int checked=0;
        try {
            for(String sex:List.of("male","fem"))for(int scale:new int[]{1,2})for(boolean upper:new boolean[]{false,true})for(boolean lower:new boolean[]{false,true}) {
                String path="textures/character/underwear/underwear_"+sex+".png";
                try(var source=read(path);var result=new NativeImage(64*scale,64*scale,true);var emission=new NativeImage(64*scale,64*scale,true)) {
                    for(int y=0;y<64*scale;y++)for(int x=0;x<64*scale;x++){result.setPixelRGBA(x,y,0xFF876543);emission.setPixelRGBA(x,y,0xFFFFFFFF);}
                    AppearanceTextures.underwear(result,emission,path,0,new UnderwearVisibility(upper,lower));
                    for(int y=0;y<64;y++)for(int x=0;x<64;x++) {
                        int pixel=source.getPixelRGBA(x,y);
                        boolean show=(pixel>>>24)>0 && (sex.equals("fem") && femaleUpper.contains(y*64+x)?upper:lower);
                        for(int dy=0;dy<scale;dy++)for(int dx=0;dx<scale;dx++) {
                            check(result.getPixelRGBA(x*scale+dx,y*scale+dy)==(show?pixel:0xFF876543),sex+" underwear piece / scale "+scale+" at "+x+","+y);
                            check(emission.getPixelRGBA(x*scale+dx,y*scale+dy)==(show?0:0xFFFFFFFF),"only visible underwear occludes emission");
                        }
                    }
                    checked++;
                }
            }
        } catch(java.io.IOException e){throw new IllegalStateException(e);}
        System.out.println("CCC_UNDERWEAR_PIXELS_PASS cases="+checked);
    }
    private static void check(boolean success,String context){if(!success)throw new IllegalStateException(context);}
}
