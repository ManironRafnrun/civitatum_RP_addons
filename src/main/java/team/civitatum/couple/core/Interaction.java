package team.civitatum.couple.core;

public enum Interaction {
    CARRY, KISS, RELEASE, HUG, REST;

    public static Interaction fromWire(int value) {
        return value >= 0 && value < values().length ? values()[value] : null;
    }

    public String key() { return "action.civitatum_couple." + name().toLowerCase(java.util.Locale.ROOT); }
}
