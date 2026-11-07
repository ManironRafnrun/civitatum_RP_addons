package team.civitatum.starclock.ritual;

public final class RitualRules {
    public static final int PREPARATION_TICKS = 40;
    public static final int DRAWING_TICKS = 40;
    public static final float MAX_LOOK_PITCH = -35.0F;

    public static boolean isNight(long dayTime) {
        long time = Math.floorMod(dayTime, 24_000L);
        return time >= 13_000L && time <= 23_000L;
    }

    private RitualRules() {}
}
