package team.civitatum.starclock.prediction;

public final class SafeNumbers {
    public static float damage(float requested, float health, int tier) {
        if (!Float.isFinite(requested) || !Float.isFinite(health) || requested < 0) {
            throw new IllegalArgumentException("Неверный урон");
        }
        return Math.max(0, Math.min(Math.min(requested, tier == 3 ? 12 : 8), health - 1));
    }

    public static int scaled(int count, int tier) {
        if (count < 0 || count > 12000 || tier < 1 || tier > 3) {
            throw new IllegalArgumentException("Неверный масштаб");
        }
        return Math.multiplyExact(count, tier);
    }

    public static int durability(int maximum, int current, double fraction, boolean repair) {
        if (maximum < 1 || current < 0 || !Double.isFinite(fraction) || fraction < 0 || fraction > 1) {
            throw new IllegalArgumentException("Неверная прочность");
        }
        int change = (int) Math.ceil(maximum * fraction);
        return Math.clamp(repair ? current - change : current + change, 0, maximum);
    }

    private SafeNumbers() {}
}
