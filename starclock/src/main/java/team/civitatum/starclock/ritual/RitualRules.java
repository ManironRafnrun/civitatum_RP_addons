package team.civitatum.starclock.ritual;

public final class RitualRules {
    public static final int PREPARATION_TICKS = 40;
    public static final int DRAWING_TICKS = 40;
    public static final long COOLDOWN_TICKS = 192_000L;
    public static final float MAX_LOOK_PITCH = -35.0F;

    public static boolean isNight(long dayTime) {
        long time = Math.floorMod(dayTime, 24_000L);
        return time >= 13_000L && time <= 23_000L;
    }

    public static long remainingCooldown(long lastRitual, long now) {
        if (lastRitual < 0) return 0;
        // Перенос данных из мира с другим временем не должен снимать кулдаун.
        long elapsed = Math.max(0, now - lastRitual);
        return Math.max(0, COOLDOWN_TICKS - elapsed);
    }

    private RitualRules() {}
}
