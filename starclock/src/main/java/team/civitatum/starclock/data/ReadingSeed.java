package team.civitatum.starclock.data;

import java.util.UUID;

/**
 * Выбор хранится у каждого игрока свой: при общем выборе чужой завершённый обряд сбрасывал бы его,
 * и игрок мог бы отменить неудачное чтение и получить новый результат, ведь чужой кулдаун его больше не держит.
 */
public record ReadingSeed(UUID player, long seed) {
    public ReadingSeed {
        if (player == null) throw new IllegalArgumentException("Неверный выбор обряда");
    }
}
