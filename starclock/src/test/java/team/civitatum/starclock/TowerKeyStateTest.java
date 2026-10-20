package team.civitatum.starclock;

import com.google.gson.Gson;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import team.civitatum.starclock.data.TowerKeyState;
import static org.junit.jupiter.api.Assertions.*;

class TowerKeyStateTest {
    @Test
    void fiveSuccessfulCastsExhaustTheGift() {
        var state = new TowerKeyState(UUID.randomUUID(), 5);
        for (int expected = 4; expected >= 0; expected--) {
            state = state.consume();
            assertEquals(expected, state.charges());
        }
        TowerKeyState exhausted = state;
        assertThrows(IllegalStateException.class, exhausted::consume);
    }

    @Test
    void newGiftRestoresFiveInsteadOfStackingCharges() {
        var state = new TowerKeyState(UUID.randomUUID(), 2).replenish();
        assertEquals(5, state.charges());
        assertEquals(5, state.replenish().charges());
    }

    @Test
    void serializationKeepsTheOwnerAndRemainingCharges() {
        var state = new TowerKeyState(UUID.randomUUID(), 3);
        var gson = new Gson();
        assertEquals(state, gson.fromJson(gson.toJson(state), TowerKeyState.class));
    }

    @Test
    void impossibleChargeCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TowerKeyState(UUID.randomUUID(), 6));
        assertThrows(IllegalArgumentException.class, () -> new TowerKeyState(UUID.randomUUID(), -1));
    }
}
