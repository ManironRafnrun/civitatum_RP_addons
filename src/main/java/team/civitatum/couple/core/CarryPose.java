package team.civitatum.couple.core;

/** Vanilla player pose, expressed in the carrier's right/up/forward frame. */
public final class CarryPose {
    public static final float BANK_DEGREES = -65.0F;
    public static final float LOCAL_YAW_DEGREES = 90.0F;
    public static final double LIFT = 0.28;
    public static final double PIVOT_HEIGHT = 0.82;
    public static final double MODEL_SCALE = 0.9375;
    public static final double MODEL_ORIGIN = 1.501;
    public static final float HEAD_PITCH = -0.12F;
    public static final float HEAD_YAW = 0.25F;
    public static final float HEAD_ROLL = -0.08F;

    public record Point(double right, double up, double forward) {}

    /** A model point after the vanilla scale/flip and our local rotations. */
    public static Point modelPoint(double x, double y, double z) {
        return root(-MODEL_SCALE * x, MODEL_SCALE * (MODEL_ORIGIN - y), MODEL_SCALE * z, true);
    }

    /** A model direction, useful for checking the facing of the posed body. */
    public static Point modelDirection(double x, double y, double z) {
        return root(-x, -y, z, false);
    }

    private static Point root(double x, double y, double z, boolean position) {
        if (position) y -= PIVOT_HEIGHT;
        double yaw = Math.toRadians(LOCAL_YAW_DEGREES);
        double bank = Math.toRadians(BANK_DEGREES);
        double sideways = Math.cos(yaw) * x + Math.sin(yaw) * z;
        double depth = -Math.sin(yaw) * x + Math.cos(yaw) * z;
        double right = Math.cos(bank) * sideways - Math.sin(bank) * y;
        double up = Math.sin(bank) * sideways + Math.cos(bank) * y;
        // PlayerRenderer's 180-degree base rotation reverses local Z.
        return new Point(right, up + (position ? LIFT : 0), -depth);
    }

    /** Eye position on the front of the head, including its fixed local pose. */
    public static Point eye() {
        double x = 0, y = -4.0 / 16.0, z = -4.1 / 16.0;
        double y1 = Math.cos(HEAD_PITCH) * y - Math.sin(HEAD_PITCH) * z;
        double z1 = Math.sin(HEAD_PITCH) * y + Math.cos(HEAD_PITCH) * z;
        double x2 = Math.sin(HEAD_YAW) * z1;
        double z2 = Math.cos(HEAD_YAW) * z1;
        return modelPoint(Math.cos(HEAD_ROLL) * x2 - Math.sin(HEAD_ROLL) * y1,
                          Math.sin(HEAD_ROLL) * x2 + Math.cos(HEAD_ROLL) * y1, z2);
    }

    private CarryPose() {}
}
