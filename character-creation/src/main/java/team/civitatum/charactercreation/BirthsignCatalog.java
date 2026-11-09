package team.civitatum.charactercreation;

import java.util.*;

/**
 * The thirteen figures of the sky ("10b. Народы и небо", part III), with the ids of Civitatum Starclock: a birthsign
 * and a reading name the same figure by the same word. Listed in the order of the months.
 */
public final class BirthsignCatalog {
    /** What a figure gives and how it is obtained. */
    public enum Kind {
        /** No sign yet: a character saved before the thirteen figures, or one that rewrote its sky. */
        NONE,
        /** A pure passive without a price or a power. */
        GUARDIAN,
        /** A passive, a price and a power. */
        PATRON,
        /** The Serpent: never chosen, only drawn by lot or given by an administrator. */
        CURSED,
        /** Not a figure: the editor's request to draw a month by lot, resolved by the server. */
        LOT
    }

    /**
     * Texts live in the lang files: {@code birthsign.civitatum.<id>.name|description|effect}, and {@code .power} for a
     * sign with a power.
     *
     * @param cooldown seconds before the power works again; 0 when the sign has no power on the key
     */
    public record Sign(String id, Kind kind, int cooldown) {
        public String key(String field) { return "birthsign.civitatum." + id + "." + field; }
        /** A figure the editor offers: the twelve months and the lot. */
        public boolean isChoosable() { return kind == Kind.GUARDIAN || kind == Kind.PATRON || kind == Kind.LOT; }
        public boolean isMonth() { return kind == Kind.GUARDIAN || kind == Kind.PATRON; }
        public boolean hasPower() { return cooldown > 0; }
    }

    public static final String NONE = "none";
    public static final String LOT = "lot";
    public static final String SERPENT = "serpent";
    /** One draw in twenty is the Serpent: "с шансом 1 к 20 вместо месяца выпадает Змей". */
    public static final int SERPENT_ODDS = 20;
    /** The power of the Ancestor works by itself, once per game day; it has no key, so its clock is separate. */
    public static final int REPRIEVE_TICKS = 24000;

    private static final List<Sign> ALL = List.of(
        new Sign(NONE, Kind.NONE, 0),
        new Sign("tower", Kind.PATRON, 600),
        new Sign("spear", Kind.GUARDIAN, 0),
        new Sign("hearth", Kind.PATRON, 180),
        new Sign("beast", Kind.PATRON, 300),
        new Sign("mother", Kind.PATRON, 120),
        new Sign("smith", Kind.GUARDIAN, 0),
        new Sign("merchant", Kind.PATRON, 180),
        new Sign("spinner", Kind.PATRON, 90),
        new Sign("messenger", Kind.GUARDIAN, 0),
        new Sign("ancestor", Kind.PATRON, 0),
        new Sign("shepherd", Kind.PATRON, 90),
        new Sign("wanderer", Kind.PATRON, 1200),
        new Sign(SERPENT, Kind.CURSED, 45),
        new Sign(LOT, Kind.LOT, 0)
    );
    private static final Map<String, Sign> BY_ID = byId();
    private static final List<Sign> MONTHS = ALL.stream().filter(Sign::isMonth).toList();

    private static Map<String, Sign> byId() {
        var byId = new HashMap<String, Sign>();
        for (var sign : ALL) byId.put(sign.id, sign);
        return Map.copyOf(byId);
    }

    public static List<Sign> all() { return ALL; }
    public static Sign get(String id) { return BY_ID.get(id); }
    public static List<Sign> months() { return MONTHS; }

    /** A lot drawn with {@code roll} in [0, 1): the Serpent one time in twenty, otherwise an equally likely month. */
    public static String draw(double roll) {
        double serpent = 1d / SERPENT_ODDS;
        if (roll < serpent) return SERPENT;
        int month = (int) ((roll - serpent) / (1 - serpent) * MONTHS.size());
        return MONTHS.get(Math.min(month, MONTHS.size() - 1)).id();
    }

    private BirthsignCatalog() {}
}
