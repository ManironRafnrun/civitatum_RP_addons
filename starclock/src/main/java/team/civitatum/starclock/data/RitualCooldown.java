package team.civitatum.starclock.data;

import java.util.UUID;

/**
 * Кулдаун игрока измеряется в игровых сутках, поэтому идёт по dayTime: сон и /time двигают только его,
 * а gameTime всегда растёт на 1 за тик. Время копится приращениями, а не сравнением с моментом обряда:
 * /time set возвращает dayTime к первым суткам мира, и сравнение заморозило бы кулдаун на все отмотанные дни.
 */
public record RitualCooldown(UUID player, long elapsed, long seenDayTime) {
    public static final long DAY_TICKS = 24_000L;

    public RitualCooldown {
        if (player == null || elapsed < 0) throw new IllegalArgumentException("Неверный кулдаун обряда");
    }

    public static RitualCooldown start(UUID player, long dayTime) {
        return new RitualCooldown(player, 0, dayTime);
    }

    public RitualCooldown advance(long dayTime) {
        long passed = dayTime - seenDayTime;
        // Отмотка выглядит для игрока как переход вперёд к тому же времени суток.
        if (passed < 0) passed = Math.floorMod(passed, DAY_TICKS);
        return new RitualCooldown(player, elapsed + passed, dayTime);
    }

    public long remaining(long total) {
        return Math.max(0, total - elapsed);
    }
}
