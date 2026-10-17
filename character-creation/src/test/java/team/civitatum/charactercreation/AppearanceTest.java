package team.civitatum.charactercreation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class AppearanceTest {
 @Test void allPreparedVariantsAndMasksExist() {
  var assets=new ArrayList<>(AppearanceCatalog.hair());
  assertEquals(41,assets.size());int clothing=0;
  for(String sex:List.of("male","female")) for(String group:AppearanceCatalog.GROUPS) {
   var items=AppearanceCatalog.clothes(sex,group);assets.addAll(items);clothing+=items.size();
  }
  assertEquals(43,clothing);
  for(var a:assets) {
   assertEquals(Set.of("classic","slim"),a.models().keySet());
   for(var v:a.models().values()) {
    for(String f:v.textures().values())assertNotNull(getClass().getResource("/assets/civitatum/"+v.directory()+"/"+f),a.id()+"/"+f);
    for(String key:a.controls().keySet())assertTrue(v.masks().containsKey(key));
    for(String f:v.masks().values())assertNotNull(getClass().getResource("/assets/civitatum/"+v.directory()+"/"+f));
   }
  }
 }
 @Test void independentChoicesSurviveJsonAndServerValidation() {
  var c=RaceCatalog.defaults("human","female");AppearanceCatalog.outfit(c,"many_layers");
  c.hairAsset="ombre_wreath";c.hairMain=2;c.hairDecor1=3;c.hairDecor2=4;
  c.wardrobe.get("coat").main=2;c.wardrobe.get("coat").decor1=4;c.wardrobe.get("boots").main=3;
  var saved=RaceState.read(new RaceState(c,0,0).json()).character();
  assertEquals(DataJson.GSON.toJsonTree(c),DataJson.GSON.toJsonTree(saved));assertEquals(DataJson.GSON.toJsonTree(c),DataJson.GSON.toJsonTree(RaceCatalog.validate(saved)));
  assertEquals(4,saved.hairDecor2);assertEquals(4,saved.wardrobe.get("coat").decor1);assertEquals(3,saved.wardrobe.get("boots").main);
 }
 @Test void serverRemovesInvalidAssetsAndUnavailableChannels() {
  var c=RaceCatalog.defaults("human","male");c.hairAsset="../../evil";c.wardrobe.put("coat",new CharacterData.Garment("many_layers/coat"));
  c.wardrobe.put("boots",new CharacterData.Garment("ruby_sash/boots"));c.wardrobe.get("boots").decor2=999;
  var v=RaceCatalog.validate(c);assertEquals("",v.hairAsset);assertFalse(v.wardrobe.containsKey("coat"));assertEquals(0,v.wardrobe.get("boots").decor2);
  c=RaceCatalog.defaults("fire_dragonborn","female");c.hairAsset="black_long";assertEquals("",RaceCatalog.validate(c).hairAsset);
 }
 @Test void originalVersionCharactersKeepLegacyAppearance() {
  var c=DataJson.GSON.fromJson("{\"selected\":true,\"race\":\"human\",\"sex\":\"female\",\"hairStyle\":2}",CharacterData.class);
  var v=RaceCatalog.validate(c);assertEquals(2,v.hairStyle);assertEquals("",v.hairAsset);assertFalse(v.wardrobeEnabled);assertTrue(v.wardrobe.isEmpty());
 }
 @Test void allCompleteOutfitsFitTheChosenSexAndHaveNoSlotCollisions() {
  assertEquals(7,AppearanceCatalog.outfits("female").size());assertEquals(6,AppearanceCatalog.outfits("male").size());
  for(String sex:List.of("male","female"))for(var o:AppearanceCatalog.outfits(sex)) {
   var c=RaceCatalog.defaults("human",sex);AppearanceCatalog.outfit(c,o.id());assertEquals(o.items().size(),c.wardrobe.size());
   assertEquals(DataJson.GSON.toJsonTree(c),DataJson.GSON.toJsonTree(RaceCatalog.validate(c)));
  }
 }
}
