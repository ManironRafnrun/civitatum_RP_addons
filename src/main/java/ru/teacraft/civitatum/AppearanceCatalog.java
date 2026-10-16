package ru.teacraft.civitatum;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared whitelist: assets, palettes and wardrobe rules are identical on client and server. */
public final class AppearanceCatalog {
    public static final List<String> CHANNELS=List.of("main","decor_1","decor_2");
    public static final List<String> GROUPS=List.of("trousers","skirt","shirt","vest","coat","corset","belt","gloves","boots");
    public static final List<String> GROUP_NAMES=List.of("Брюки","Юбка","Рубашка / блуза","Жилет","Верхняя одежда","Корсет","Пояс","Перчатки","Обувь");
    public record Color(String label,int rgb) {}
    public record Channel(String label,String affects,List<Color> colors,String kind) {}
    public record Gradient(String neutral,Map<String,String> masks) {}
    public record Variant(String directory,Map<String,String> textures,Map<String,String> masks,Gradient gradient) {}
    public record Asset(String id,String name,String gender,String group,Map<String,Channel> controls,Map<String,Variant> models,boolean linkedShades) {
        public boolean permits(String sex) { return gender.equals("any") || gender.equals(sex); }
    }
    public record Outfit(String id,String name,String gender,List<String> items) {}
    private static final Map<String,Asset> HAIR=new LinkedHashMap<>(), CLOTHES=new LinkedHashMap<>();
    private static final Map<String,Outfit> OUTFITS=new LinkedHashMap<>();
    static {
        for(String set:List.of("hair","hair_v2"))for(var e:read(set)) {var o=e.getAsJsonObject();var a=asset(o.get("id").getAsString(),o.get("name").getAsString(),"any","",o);if(HAIR.put(a.id,a)!=null)throw new IllegalStateException("Duplicate hair: "+a.id);}
        for(var e:read("clothing")) {
            var o=e.getAsJsonObject();String id=o.get("id").getAsString(), name=o.get("name").getAsString(), gender=o.get("gender").getAsString();
            var items=new ArrayList<String>();
            for(var entry:o.getAsJsonObject("items").entrySet()) {
                String itemId=id+"/"+entry.getKey();
                var a=asset(itemId,name+" • "+entry.getValue().getAsJsonObject().get("label_ru").getAsString(),gender,group(entry.getKey()),entry.getValue().getAsJsonObject());
                CLOTHES.put(itemId,a);items.add(itemId);
            }
            OUTFITS.put(id,new Outfit(id,name,gender,List.copyOf(items)));
        }
    }
    private static JsonArray read(String name) {
        try(var in=AppearanceCatalog.class.getResourceAsStream("/appearance/"+name+".json")) {
            if(in==null)throw new IllegalStateException("Missing appearance catalog: "+name);
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("entries");
        } catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static Asset asset(String id,String name,String gender,String group,JsonObject o) {
        var controls=new LinkedHashMap<String,Channel>();
        for(String key:CHANNELS) if(o.getAsJsonObject("controls").has(key)) {
            var c=o.getAsJsonObject("controls").getAsJsonObject(key);var colors=new ArrayList<Color>();
            for(var e:c.getAsJsonArray("palette")) {var p=e.getAsJsonObject();colors.add(new Color(p.get("label_ru").getAsString(),Integer.parseInt(p.get("rgb").getAsString().substring(1),16)));}
            if(colors.isEmpty() || colors.size()>10)throw new IllegalStateException("Invalid palette: "+id);
            controls.put(key,new Channel(c.get("label_ru").getAsString(),c.get("affects_ru").getAsString(),List.copyOf(colors),c.has("kind")?c.get("kind").getAsString():"decor"));
        }
        var models=new LinkedHashMap<String,Variant>();
        for(var entry:o.getAsJsonObject("models").entrySet()) {
            var v=entry.getValue().getAsJsonObject();String path=v.get("directory").getAsString();
            if(!path.startsWith("assets/civitatum/textures/character/") || path.contains(".."))throw new IllegalStateException("Invalid asset path");
            var gradient=v.has("gradient_blending")?v.getAsJsonObject("gradient_blending"):null;
            models.put(entry.getKey(),new Variant(path.substring("assets/civitatum/".length()),strings(v.getAsJsonObject("textures")),strings(v.getAsJsonObject("masks")),gradient==null?null:new Gradient(gradient.get("neutral").getAsString(),strings(gradient.getAsJsonObject("masks")))));
        }
        return new Asset(id,name,gender,group,Collections.unmodifiableMap(controls),Collections.unmodifiableMap(models),o.has("linked_shades") && o.get("linked_shades").getAsBoolean());
    }
    private static Map<String,String> strings(JsonObject o) {var m=new LinkedHashMap<String,String>();for(var e:o.entrySet())m.put(e.getKey(),e.getValue().getAsString());return Collections.unmodifiableMap(m);}
    private static String group(String id) {return switch(id){case "breeches"->"trousers";case "blouse"->"shirt";case "tunic","dress","gambeson"->"coat";case "sash"->"belt";default->id;};}
    public static List<Asset> hair(){return List.copyOf(HAIR.values());}
    public static Asset hair(String id){return HAIR.get(id);}
    public static Asset clothing(String id){return CLOTHES.get(id);}
    public static List<Asset> clothes(String sex,String group){return CLOTHES.values().stream().filter(a->a.permits(sex)&&a.group.equals(group)).toList();}
    public static List<Outfit> outfits(String sex){return OUTFITS.values().stream().filter(o->o.gender.equals(sex)).toList();}
    public static void outfit(CharacterData c,String id) {
        c.wardrobe.clear();c.outfitPreset="";var outfit=OUTFITS.get(id);
        if(outfit==null || !outfit.gender.equals(c.sex)) {c.wardrobeEnabled=false;return;}
        c.wardrobeEnabled=true;c.outfitPreset=id;
        for(String item:outfit.items){var a=CLOTHES.get(item);c.wardrobe.put(a.group,new CharacterData.Garment(item));}
    }
    public static int color(Asset a,String channel,int n) {var p=a.controls.get(channel);return p==null?0:Math.max(0,Math.min(p.colors.size()-1,n));}
    public static boolean shadeChannel(Asset a,String channel,boolean linked){return linked && a.linkedShades && !channel.equals("main") && a.controls.containsKey(channel) && a.controls.get(channel).kind.equals("hair");}
    public static int colorCount(Asset a,String channel,boolean linked){return shadeChannel(a,channel,linked)?7:a.controls.get(channel).colors.size();}
    public static int tint(Asset a,String channel,int choice,int main,boolean linked){
        choice=Math.clamp(choice,0,colorCount(a,channel,linked)-1);main=color(a,"main",main);
        if(shadeChannel(a,channel,linked)){
            if(choice==0 && main==0)return -1;
            return HairTones.shade(a.controls.get("main").colors.get(main).rgb,choice,a.controls.get("main").colors.getFirst().rgb,a.controls.get(channel).colors.getFirst().rgb);
        }
        return choice==0?-1:a.controls.get(channel).colors.get(choice).rgb;
    }
    public static void validate(CharacterData c,boolean hairAllowed) {
        var h=HAIR.get(c.hairAsset);
        if(h==null || !hairAllowed){c.hairAsset="";c.hairMain=c.hairDecor1=c.hairDecor2=0;}
        else {c.hairMain=color(h,"main",c.hairMain);c.hairDecor1=shadeChannel(h,"decor_1",c.hairLinkedShades)?Math.clamp(c.hairDecor1,0,6):color(h,"decor_1",c.hairDecor1);c.hairDecor2=shadeChannel(h,"decor_2",c.hairLinkedShades)?Math.clamp(c.hairDecor2,0,6):color(h,"decor_2",c.hairDecor2);}
        var safe=new LinkedHashMap<String,CharacterData.Garment>();
        if(c.wardrobe!=null)for(String group:GROUPS) {
            var v=c.wardrobe.get(group);if(v==null)continue;var a=CLOTHES.get(v.asset);
            if(a==null || !a.permits(c.sex) || !a.group.equals(group))continue;
            v.main=color(a,"main",v.main);v.decor1=color(a,"decor_1",v.decor1);v.decor2=color(a,"decor_2",v.decor2);safe.put(group,v);
        }
        c.wardrobe=safe;var outfit=OUTFITS.get(c.outfitPreset);
        if(outfit==null || !outfit.gender.equals(c.sex))c.outfitPreset="";
    }
}
