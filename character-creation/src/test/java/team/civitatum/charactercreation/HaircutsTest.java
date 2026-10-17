package team.civitatum.charactercreation;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HaircutsTest {
    @Test void gradientWeightsAreCompleteAndKeepAuthoredAlphaSeparate() throws Exception {
        int variants=0,transitions=0;
        for(var hair:AppearanceCatalog.hair())for(var v:hair.models().values())if(v.gradient()!=null){
            var original=ImageIO.read(getClass().getResource("/assets/civitatum/"+v.directory()+"/original.png"));
            var weights=new ArrayList<java.awt.image.BufferedImage>();
            for(String file:v.gradient().masks().values())weights.add(ImageIO.read(getClass().getResource("/assets/civitatum/"+v.directory()+"/"+file)));
            for(int y=0;y<64;y++)for(int x=0;x<64;x++){
                int sum=0;for(var w:weights){int alpha=w.getRGB(x,y)>>>24;sum+=alpha;if(alpha>0 && alpha<255)transitions++;}
                assertEquals((original.getRGB(x,y)>>>24)>0?255:0,sum,hair.id()+" at "+x+","+y);
            }
            variants++;
        }
        assertEquals(46,variants);assertTrue(transitions>500); // 9 gradient + 14 single-colour styles with shade maps, two models each
    }
    @Test void linkedSecondaryColorsFollowTheMainHueAndRetainIndependentAccessories() {
        for(String id:List.of("hc_fall_leaves","hc_tangled","hc_blonde","hc_moonlit")){
            var h=AppearanceCatalog.hair(id);int original=AppearanceCatalog.tint(h,"decor_1",0,0,true);assertEquals(-1,original);
            int base=h.controls().get("main").colors().get(2).rgb();
            for(int n=0;n<7;n++){
                int color=AppearanceCatalog.tint(h,"decor_1",n,2,true);
                float baseRG=(base>>16&255)/(float)(base>>8&255),actualRG=(color>>16&255)/(float)(color>>8&255);
                assertEquals(baseRG,actualRG,.06,id+" hue "+n);
            }
            assertNotEquals(AppearanceCatalog.tint(h,"decor_1",3,2,true),AppearanceCatalog.tint(h,"decor_1",3,5,true));
        }
        var wreath=AppearanceCatalog.hair("hc_black_tumblr");assertFalse(AppearanceCatalog.shadeChannel(wreath,"decor_1",true));
        assertEquals(wreath.controls().get("decor_1").colors().get(2).rgb(),AppearanceCatalog.tint(wreath,"decor_1",2,5,true));
    }
    @Test void facialHairUsesSkinTexturesAndIsOnlyAllowedForDwarfWomen() throws Exception {
        for(var race:RaceCatalog.all())if(!race.family())for(String sex:List.of("male","female")){
            var c=RaceCatalog.defaults(race.id,sex);c.beard=4;c.mustache=5;
            var saved=RaceCatalog.validate(c);boolean allowed=race.beardAllowed && (sex.equals("male") || race.id.equals("dwarf"));
            assertEquals(allowed?4:0,saved.beard,race.id+sex);assertEquals(allowed?5:0,saved.mustache,race.id+sex);
        }
        Set<String> unique=new HashSet<>();
        for(String kind:List.of("beard","mustache"))for(int n=1;n<=5;n++){
            var url=getClass().getResource("/assets/civitatum/textures/character/facial_hair/"+kind+"_"+n+".png");assertNotNull(url);
            var image=ImageIO.read(url);assertEquals(64,image.getWidth());assertEquals(64,image.getHeight());
            for(int y=8;y<=13;y++)for(int x:new int[]{9,10,13,14})assertEquals(0,image.getRGB(x,y)>>>24,"default eyes stay clear");
            try(var in=url.openStream()){assertTrue(unique.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))));}
        }
    }
    @Test void newChoicesSurviveSaveAndCosmeticEditWithoutChangingBody() {
        var c=RaceCatalog.defaults("dwarf","female");var edit=c.copy();edit.beard=5;edit.mustache=2;edit.hairAsset="hc_moonlit";edit.hairMain=3;edit.hairDecor1=4;edit.hairLinkedShades=false;
        assertDoesNotThrow(()->AppearanceRules.check(c,edit,EditorMode.COSMETIC));
        var saved=RaceState.read(new RaceState(edit,0,0).json()).character();
        assertEquals(DataJson.GSON.toJsonTree(edit),DataJson.GSON.toJsonTree(RaceCatalog.validate(saved)));
        var old=DataJson.GSON.fromJson("{\"selected\":true,\"race\":\"human\",\"sex\":\"male\",\"beard\":2}",CharacterData.class);assertEquals(2,RaceCatalog.validate(old).beard);assertEquals(0,old.mustache);
    }
}
