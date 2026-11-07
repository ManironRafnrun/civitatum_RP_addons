package team.civitatum.starclock;

import org.junit.jupiter.api.Test;
import team.civitatum.starclock.data.PeaceZone;
import team.civitatum.starclock.prediction.SafeNumbers;
import static org.junit.jupiter.api.Assertions.*;

class EventSafetyTest {
    @Test
    void directDamageAlwaysLeavesAtLeastOneHealthPoint() {
        assertEquals(4, SafeNumbers.damage(8, 5, 1));
        assertEquals(0, SafeNumbers.damage(8, 1, 3));
        assertEquals(12, SafeNumbers.damage(100, 20, 3));
        assertEquals(8, SafeNumbers.damage(100, 20, 1));
    }

    @Test
    void tiersScaleCountsWithoutScalingWearPercentages() {
        assertEquals(12, SafeNumbers.scaled(4, 3));
        assertEquals(70, SafeNumbers.durability(100, 20, 0.5, false));
        assertEquals(30, SafeNumbers.durability(100, 80, 0.5, true));
        assertEquals(100, SafeNumbers.durability(100, 90, 0.5, false));
    }

    @Test
    void peaceZoneUsesItsOwnDimensionAndExpiresAtTheBoundary() {
        var zone = new PeaceZone("minecraft:overworld", 0, 64, 0, 32, 1000);
        assertTrue(zone.contains("minecraft:overworld", 32.5, 64.5, 0.5, 999));
        assertFalse(zone.contains("minecraft:overworld", 32.6, 64.5, 0.5, 999));
        assertFalse(zone.contains("minecraft:the_nether", 0.5, 64.5, 0.5, 999));
        assertFalse(zone.contains("minecraft:overworld", 0.5, 64.5, 0.5, 1000));
    }
}
