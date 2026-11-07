package team.civitatum.starclock.prediction;

import java.util.random.RandomGenerator;

public final class PredictionTimer {
    public static final int MIN_TICKS = 6000;
    public static final int MAX_TICKS = 96000;
    public static final int THIRD_TIER_MAX_TICKS = 72000;

    public static long choose(RandomGenerator random, int tier, int minimum, int maximum, int thirdMaximum) {
        validate(minimum, maximum, thirdMaximum);
        if (tier < 1 || tier > 3) throw new IllegalArgumentException("Неизвестная ступень");
        int upper = tier == 3 ? Math.min(maximum, thirdMaximum) : maximum;
        return random.nextLong(minimum, (long) upper + 1L);
    }

    public static void validate(int minimum, int maximum, int thirdMaximum) {
        if (minimum < 1 || maximum < minimum || thirdMaximum < minimum) {
            throw new IllegalArgumentException("Минимальный таймер должен быть положительным и не превышать обе верхние границы");
        }
    }

    private PredictionTimer() {}
}
