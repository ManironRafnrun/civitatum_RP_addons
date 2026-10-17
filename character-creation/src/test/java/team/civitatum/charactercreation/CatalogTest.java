package team.civitatum.charactercreation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class CatalogTest {
    @Test void everyPlayableRaceHasValidDefaultsForBothSexes() {
        assertEquals(22,RaceCatalog.all().size());
        assertEquals(12,RaceCatalog.roots().size());
        assertEquals(19,RaceCatalog.all().stream().filter(r->!r.family()).count());
        for(var race:RaceCatalog.all()) if(!race.family()) for(String sex:List.of("male","female")) {
            var c=RaceCatalog.defaults(race.id,sex);
            assertTrue(c.height>=race.minHeight && c.height<=race.maxHeight);
            assertTrue(c.selected);assertEquals(sex,c.sex);
            assertEquals(c.json(),RaceCatalog.validate(c).json());
        }
    }
    @Test void paletteLimitsAndAuthorExceptions() {
        for(var race:RaceCatalog.all()) for(String part:List.of("skin","hair","eyes","horn","tattoo"))
            assertTrue(race.palette(part).length>=1 && race.palette(part).length<=6);
        assertEquals(1,RaceCatalog.get("jahar").eyeColors.length);
        assertEquals("#FFFFFF",RaceCatalog.get("jahar").eyeColors[0]);
        assertEquals(3,RaceCatalog.get("fire_dragonborn").skinColors.length);
        assertEquals(3,RaceCatalog.get("frost_dragonborn").skinColors.length);
        assertEquals(3,RaceCatalog.get("storm_dragonborn").skinColors.length);
        assertEquals(1,RaceCatalog.get("end_dragonborn").skinColors.length);
    }
    @Test void forbiddenAndMandatoryPartsAreServerEnforced() {
        var human=RaceCatalog.defaults("human","male");human.horns=5;human.tail=1;
        var checked=RaceCatalog.validate(human);assertEquals(0,checked.horns);assertEquals(0,checked.tail);
        var elf=RaceCatalog.defaults("wood_elf","female");elf.ears=0;
        assertTrue(RaceCatalog.validate(elf).ears>0);
        var dragon=RaceCatalog.defaults("fire_dragonborn","female");dragon.horns=0;
        assertTrue(RaceCatalog.validate(dragon).horns>0);
        var male=RaceCatalog.defaults("forest_algnor","male");male.horns=0;
        assertTrue(RaceCatalog.validate(male).horns>0);
        var female=RaceCatalog.defaults("forest_algnor","female");female.horns=0;
        assertEquals(0,RaceCatalog.validate(female).horns);
        female.horns=10;assertNotEquals(10,RaceCatalog.validate(female).horns);
        var city=RaceCatalog.defaults("city_algnor","male");city.horns=10;assertEquals(10,RaceCatalog.validate(city).horns);
    }
    @Test void maliciousValuesCannotLeaveServerBounds() {
        var c=RaceCatalog.defaults("jahar","male");c.eyes=1;
        assertThrows(IllegalArgumentException.class,()->RaceCatalog.validate(c));
        c.eyes=0;c.height=Float.NaN;c.hornPitch=Float.POSITIVE_INFINITY;c.hornOffset=-100;
        c.name="§x\nTest";c.bust=100;
        var v=RaceCatalog.validate(c);
        assertTrue(Float.isFinite(v.height));assertEquals(0,v.hornPitch);assertEquals(0,v.bust);
        assertTrue(v.hornOffset>=-.2f,"horn attachment must stay within the head");
        assertFalse(v.name.contains("§"));assertFalse(v.name.contains("\n"));
    }
    @Test void familyCannotBeCommittedAndIdentityIsBounded() {
        var c=new CharacterData();c.race="elf";
        assertThrows(IllegalArgumentException.class,()->RaceCatalog.validate(c));
        c.race="does_not_exist";assertThrows(NullPointerException.class,()->RaceCatalog.validate(c));
        var longName=RaceCatalog.defaults("human","male");longName.name="а".repeat(80);
        assertEquals(32,RaceCatalog.validate(longName).name.length());
    }
    @Test void persistenceKeepsCurseCooldownAndAppearance() {
        var c=RaceCatalog.defaults("orthosian","female");c.tattoo=6;c.skin=5;c.bust=.8f;c.earYaw=15;
        var state=new RaceState(c,4,123456);
        var saved=RaceState.read(state.json());
        assertEquals(4,saved.curseDeaths());assertEquals(123456,saved.cooldownUntil());
        assertEquals(c.json(),saved.character().json());
        assertFalse(RaceState.read("{broken").character().selected);
    }
}
