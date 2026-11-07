package team.civitatum.charactercreation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EarStylesTest {
    @Test
    void racesWithTheirOwnEarsKeepThem() {
        assertEquals(5, RaceCatalog.defaults("firbolg", "male").ears);
        assertEquals(6, RaceCatalog.defaults("goblin", "female").ears);
        assertEquals(7, RaceCatalog.defaults("forest_algnor", "male").ears);
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5}, RaceCatalog.earOptions(RaceCatalog.get("firbolg")));
    }

    @Test
    void anotherRacesOwnEarsAreNotAllowed() {
        var orc = RaceCatalog.defaults("orc", "male");
        orc.ears = 6;
        assertEquals(4, RaceCatalog.validate(orc).ears);
        var elf = RaceCatalog.defaults("wood_elf", "female");
        elf.ears = 0;
        assertEquals(1, RaceCatalog.validate(elf).ears);
        assertArrayEquals(new int[] {1, 2, 3, 4}, RaceCatalog.earOptions(RaceCatalog.get("wood_elf")));
    }

    @Test
    void racesWithoutEarsStayWithout() {
        var human = RaceCatalog.defaults("human", "male");
        human.ears = 5;
        assertEquals(0, RaceCatalog.validate(human).ears);
    }
}
