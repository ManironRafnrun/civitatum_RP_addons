package team.civitatum.coupleinteractions.core;

/** Screen coordinates (positive Y points down). Keeps selection identical to drawing. */
public final class RadialLayout {
    public static double centerAngle(int index, int count) {
        return -Math.PI / 2 + index * Math.PI * 2 / count;
    }

    public static int select(double x, double y, double inner, double outer, int count) {
        double distance = Math.hypot(x, y);
        if (count < 1 || distance < inner || distance > outer + 24) return -1;
        double a = Math.atan2(y, x) + Math.PI / 2 + Math.PI / count;
        a = (a % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        return Math.min(count - 1, (int) (a / (Math.PI * 2 / count)));
    }
    private RadialLayout() {}
}
