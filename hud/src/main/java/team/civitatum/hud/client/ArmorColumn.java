package team.civitatum.hud.client;

/**
 * The armour slots beside the character. A tall window has a tall character, but the slots keep close together and are
 * centred on it instead of spreading along its whole height.
 */
final class ArmorColumn {
    /** The step at the usual window height on a small screen: a slot of 18 and a gap of about 4. */
    private static final int MAX_STEP = 22;
    private static final int SLOTS = 4;

    private ArmorColumn() {
    }

    /**
     * Offset of a slot, head (0) to feet (3), from the top of the column.
     *
     * @param span distance between the top of the first slot and the top of the last one when spread out
     */
    static int offset(int row, int span) {
        int gaps = SLOTS - 1, used = Math.min(span, MAX_STEP * gaps);
        return (span - used) / 2 + used * row / gaps;
    }
}
