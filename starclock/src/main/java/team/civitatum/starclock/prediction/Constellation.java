package team.civitatum.starclock.prediction;

import java.util.random.RandomGenerator;

public enum Constellation {
    TOWER("tower"), SPEAR("spear"), HEARTH("hearth"), BEAST("beast"),
    MOTHER("mother"), SMITH("smith"), MERCHANT("merchant"), SPINNER("spinner"),
    MESSENGER("messenger"), ANCESTOR("ancestor"), SHEPHERD("shepherd"),
    WANDERER("wanderer"), SERPENT("serpent");

    private static final Constellation[] SIGNS = values();
    private final String id;

    Constellation(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "sign.civitatum_starclock." + id;
    }

    public static Constellation draw(RandomGenerator random) {
        return SIGNS[random.nextInt(SIGNS.length)];
    }

    public Orientation drawOrientation(RandomGenerator random) {
        return this == SERPENT ? Orientation.NONE
                : random.nextBoolean() ? Orientation.UPRIGHT : Orientation.REVERSED;
    }
}
