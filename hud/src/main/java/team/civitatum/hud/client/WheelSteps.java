package team.civitatum.hud.client;

/**
 * Turns wheel movement into whole rows. Touchpads and smooth scrolling send fractions of a notch, which a plain
 * {@code (int)} cast drops; the remainder is kept until it adds up to a row.
 */
final class WheelSteps {
    private double remainder;

    /** Whole rows to move for this wheel event, with the sign of {@code vertical}. */
    int rows(double vertical) {
        // a turn the other way starts afresh instead of first paying back the opposite remainder
        if (remainder != 0 && Math.signum(remainder) != Math.signum(vertical)) remainder = 0;
        remainder += vertical;
        int rows = (int) remainder;
        remainder -= rows;
        return rows;
    }
}
