package ru.teacraft.civitatum;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class EditorRulesTest {
 @Test void cosmeticItemCannotChangeBodyIdentityOrBirthsign() {
  var saved=RaceCatalog.defaults("human","female");
  for(String field:List.of("height","sex","race","bust","skin","eyes","eyeHeight","armModel","birthsign","earYaw")) {
   var c=saved.copy();
   switch(field){case "height"->c.height+=.05f;case "sex"->c.sex="male";case "race"->c.race="orc";case "bust"->c.bust=.2f;case "skin"->c.skin=2;case "eyes"->c.eyes=2;case "eyeHeight"->c.eyeHeight=1;case "armModel"->c.armModel="classic";case "birthsign"->c.birthsign="mage";case "earYaw"->c.earYaw=30;}
   assertThrows(IllegalArgumentException.class,()->AppearanceRules.check(saved,c,EditorMode.COSMETIC),field);
  }
 }
 @Test void cosmeticItemAcceptsOnlyItsWhitelistedChanges() {
  var saved=RaceCatalog.defaults("human","female");var c=saved.copy();
  c.name="Арина";c.hairAsset="ombre_wreath";c.hairMain=2;c.hairDecor1=3;c.hairDecor2=4;c.beard=5;c.underwearColor=3;c.naked=true;
  c.wardrobe.get("coat").main=2;
  assertDoesNotThrow(()->AppearanceRules.check(saved,c,EditorMode.COSMETIC));
 }
 @Test void fullLooksAllowsSexAndBodyButNeverRace() {
  var saved=RaceCatalog.defaults("human","female");var c=saved.copy();c.sex="male";c.height=1.9f;c.birthsign="lady";c.eyeHeight=-2;
  assertDoesNotThrow(()->AppearanceRules.check(saved,c,EditorMode.FULL));c.race="orc";
  assertThrows(IllegalArgumentException.class,()->AppearanceRules.check(saved,c,EditorMode.FULL));
 }
 @Test void newDefaultsAndBoundsAreConsistent() {
  var female=RaceCatalog.defaults("human","female");assertEquals("slim",female.armModel);
  var male=RaceCatalog.defaults("human","male");assertEquals("classic",male.armModel);
  female.eyeHeight=99;female.underwearColor=99;female.birthsign="untrusted";female.armModel="../evil";
  var checked=RaceCatalog.validate(female);assertEquals(2,checked.eyeHeight);assertEquals(6,checked.underwearColor);assertEquals("none",checked.birthsign);assertEquals("auto",checked.armModel);
  assertEquals(7,BirthsignCatalog.all().size());
 }
 @Test void idleLoopsAtTheAuthoredReturnTickAndUsesRealTracks() {
  assertEquals(0,IdleAnimation.offset("rightLeg","y",150),.00001);assertEquals(12,IdleAnimation.value("rightLeg","y",150),.00001);
  assertEquals(0,IdleAnimation.BEGIN);assertEquals(15,IdleAnimation.RETURN);assertEquals(250,IdleAnimation.END);
  assertEquals(IdleAnimation.value("head","yaw",16),IdleAnimation.value("head","yaw",251),.00001);
  assertNotEquals(IdleAnimation.value("head","yaw",15),IdleAnimation.value("head","yaw",200));
  assertEquals(0,IdleAnimation.value("leftArm","pitch",156),.00001);
  assertEquals(.5065025687f,IdleAnimation.value("rightLeg","yaw",150),.00001);
  for(float time:new float[]{0,15,200,250,251,100000})for(String part:List.of("head","torso","leftArm","leftLeg","rightLeg"))for(String axis:List.of("x","y","z","pitch","yaw","roll"))assertTrue(Float.isFinite(IdleAnimation.value(part,axis,time)));
 }
 @Test void allFourEarsAndThreeTailsAreValidated() {
  var c=RaceCatalog.defaults("forest_algnor","female");for(int n=1;n<=4;n++){c.ears=n;assertEquals(n,RaceCatalog.validate(c).ears);}
  for(int n=1;n<=3;n++){c.tail=n;assertEquals(n,RaceCatalog.validate(c).tail);}
 }
}
