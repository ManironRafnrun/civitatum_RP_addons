package team.civitatum.starclock;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import team.civitatum.starclock.data.RitualCooldown;
import static org.junit.jupiter.api.Assertions.*;

class RitualCooldownTest {
    private static final long TOTAL = 8 * RitualCooldown.DAY_TICKS;
    private static final UUID PLAYER = UUID.fromString("7b3f1c0f-1d94-4f92-bbd2-fd3af56346d8");

    @Test
    void expiresAtExactlyTheConfiguredTime() {
        var start = RitualCooldown.start(PLAYER, 15000);
        assertEquals(TOTAL, start.remaining(TOTAL));
        assertEquals(1, start.advance(15000 + TOTAL - 1).remaining(TOTAL));
        assertEquals(0, start.advance(15000 + TOTAL).remaining(TOTAL));
        assertEquals(0, start.advance(15000 + TOTAL + 500000).remaining(TOTAL));
    }

    @Test
    void forwardJumpCountsInFull() {
        // Сон и /time add двигают dayTime вперёд, в том числе сразу на несколько суток.
        assertEquals(5 * RitualCooldown.DAY_TICKS,
                RitualCooldown.start(PLAYER, 0).advance(5 * RitualCooldown.DAY_TICKS).elapsed());
    }

    @Test
    void timeSetBackDoesNotFreezeCooldown() {
        // Обряд на 100-х сутках, затем /time set 14000: момент обряда теперь «в будущем» на 100 суток.
        long ritual = 100 * RitualCooldown.DAY_TICKS + 15000;
        var cooldown = RitualCooldown.start(PLAYER, ritual).advance(14000);
        assertEquals(RitualCooldown.DAY_TICKS - 1000, cooldown.elapsed());
        assertEquals(TOTAL - RitualCooldown.DAY_TICKS, cooldown.advance(14000 + 1000).remaining(TOTAL));
    }

    @Test
    void timeSetToTheSameMomentCountsAsNothing() {
        assertEquals(0, RitualCooldown.start(PLAYER, 14000).advance(14000).elapsed());
    }

    @Test
    void stoppedClockKeepsTheCooldown() {
        var cooldown = RitualCooldown.start(PLAYER, 14000).advance(14000).advance(14000);
        assertEquals(TOTAL, cooldown.remaining(TOTAL));
    }

    @Test
    void advanceKeepsTheOwner() {
        assertEquals(PLAYER, RitualCooldown.start(PLAYER, 0).advance(100).player());
    }

    @Test
    void cooldownWithoutOwnerIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RitualCooldown.start(null, 0));
    }
}
