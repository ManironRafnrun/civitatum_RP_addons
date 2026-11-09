package team.civitatum.charactercreation.power;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import team.civitatum.charactercreation.CharacterData;
import team.civitatum.charactercreation.Civitatum;

/**
 * Who has which passive. The race and sign come from the synced character, so the same answer is given on the client
 * for what the client predicts (speed in powder snow, air bubbles, leashing), and nothing for a player without one.
 */
public final class Traits {
    /** The algnor family: forest and city borianins share every ability ("в каноне это один народ"). */
    public static final String ALGNOR_PARENT = "algnor";

    public static boolean isRace(Entity entity, String race) {
        var c = character(entity);
        return c != null && c.race.equals(race);
    }

    public static boolean isAlgnor(Entity entity) {
        var c = character(entity);
        return c != null && (c.race.equals("forest_algnor") || c.race.equals("city_algnor"));
    }

    /** The dragonborn of every element. */
    public static boolean isDragonborn(Entity entity) {
        var c = character(entity);
        return c != null && c.race.endsWith("_dragonborn");
    }

    public static boolean isSign(Entity entity, String sign) {
        var c = character(entity);
        return c != null && c.birthsign.equals(sign);
    }

    public static String race(Entity entity) {
        var c = character(entity);
        return c == null ? "" : c.race;
    }

    public static String sign(Entity entity) {
        var c = character(entity);
        return c == null ? "" : c.birthsign;
    }

    /** The chosen character of a player, or {@code null} for a player without one and for every other entity. */
    public static CharacterData character(Entity entity) {
        if (!(entity instanceof Player p)) return null;
        var c = p.getData(Civitatum.STATE).character();
        return c.selected ? c : null;
    }

    private Traits() {}
}
