package team.civitatum.starclock.data;

import java.util.UUID;

public record TowerKeyState(UUID player, int charges) {
    public static final int MAX_CHARGES = 5;

    public TowerKeyState {
        if (player == null || charges < 0 || charges > MAX_CHARGES) {
            throw new IllegalArgumentException("Неверный запас Ключа Башни");
        }
    }

    public TowerKeyState consume() {
        if (charges == 0) throw new IllegalStateException("Ключ Башни исчерпан");
        return new TowerKeyState(player, charges - 1);
    }

    public TowerKeyState replenish() {
        return new TowerKeyState(player, MAX_CHARGES);
    }
}
