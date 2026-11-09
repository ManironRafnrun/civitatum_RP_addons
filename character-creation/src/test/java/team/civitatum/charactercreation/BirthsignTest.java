package team.civitatum.charactercreation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/** The thirteen figures of "10b" and how a character gets one. */
class BirthsignTest {
    @Test void thirteenFiguresWithStarclockIds() {
        var ids = BirthsignCatalog.all().stream().filter(s -> s.isMonth() || s.kind() == BirthsignCatalog.Kind.CURSED)
            .map(BirthsignCatalog.Sign::id).toList();
        assertEquals(List.of("tower", "spear", "hearth", "beast", "mother", "smith", "merchant", "spinner",
            "messenger", "ancestor", "shepherd", "wanderer", "serpent"), ids);
        assertEquals(12, BirthsignCatalog.months().size());
    }

    @Test void guardiansHaveNoPowerAndTheAncestorWorksByItself() {
        for (var id : List.of("spear", "smith", "messenger", "ancestor"))
            assertFalse(BirthsignCatalog.get(id).hasPower(), id);
        for (var id : List.of("tower", "hearth", "beast", "mother", "merchant", "spinner", "shepherd", "wanderer",
                "serpent")) assertTrue(BirthsignCatalog.get(id).hasPower(), id);
    }

    /** One draw in twenty is the Serpent; the rest are months, each about as likely. */
    @Test void lotGivesTheSerpentOneTimeInTwenty() {
        assertEquals("serpent", BirthsignCatalog.draw(0));
        assertEquals("serpent", BirthsignCatalog.draw(0.0499));
        assertNotEquals("serpent", BirthsignCatalog.draw(0.05));
        assertEquals("wanderer", BirthsignCatalog.draw(0.999999));
        var counts = new HashMap<String, Integer>();
        int n = 120000;
        for (int i = 0; i < n; i++) counts.merge(BirthsignCatalog.draw((i + .5) / n), 1, Integer::sum);
        assertEquals(n / 20, counts.get("serpent"), n / 200);
        for (var month : BirthsignCatalog.months())
            assertEquals(n * 19 / 20 / 12, counts.get(month.id()), n / 200, month.id());
    }

    private static CharacterData saved(String sign) {
        var c = RaceCatalog.defaults("human", "male");
        c.birthsign = sign;
        return c;
    }

    @Test void aChosenSignIsLocked() {
        assertEquals("tower", SignChoice.resolve(saved("tower"), "tower", EditorMode.FULL, 0));
        var e = assertThrows(IllegalArgumentException.class,
            () -> SignChoice.resolve(saved("tower"), "spear", EditorMode.CREATE, 0));
        assertTrue(e.getMessage().endsWith("sign_locked"));
        assertEquals("serpent", SignChoice.resolve(saved("serpent"), "serpent", EditorMode.FULL, 0));
    }

    @Test void aCharacterWithoutSignChoosesOrDraws() {
        assertEquals("smith", SignChoice.resolve(saved("none"), "smith", EditorMode.FULL, .5));
        assertEquals("serpent", SignChoice.resolve(saved("none"), "lot", EditorMode.CREATE, .01));
        assertTrue(BirthsignCatalog.get(SignChoice.resolve(saved("none"), "lot", EditorMode.CREATE, .5)).isMonth());
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> SignChoice.resolve(saved("none"), "serpent", EditorMode.CREATE, 0)).getMessage()
            .endsWith("sign_serpent"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> SignChoice.resolve(saved("none"), "none", EditorMode.CREATE, 0)).getMessage()
            .endsWith("sign_required"));
        // The mirror changes only looks: an old character without a sign keeps going.
        assertEquals("none", SignChoice.resolve(saved("none"), "none", EditorMode.COSMETIC, 0));
    }

    @Test void oldTesSignsBecomeNone() {
        var c = saved("warrior");
        assertEquals("none", RaceCatalog.validate(c).birthsign);
    }

    @Test void everyRacialPowerIsOncePerDay() {
        for (var r : RaceCatalog.all()) if (r.hasAbility()) assertEquals(1200, r.cooldown, r.id);
    }
}
