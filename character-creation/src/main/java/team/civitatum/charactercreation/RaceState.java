package team.civitatum.charactercreation;

import com.mojang.serialization.Codec;

/**
 * Everything the server keeps for a player: the character and the clocks of its powers. Times are overworld game
 * ticks ({@link RaceService#time}), so they keep counting while the player is offline.
 *
 * @param cooldownUntil     when the racial power is ready again
 * @param signCooldownUntil when the birthsign's power is ready again
 * @param reprieveUntil     when the Ancestor's reprieve may save the player again
 * @param skyRewritten      the character has already rewritten its sky; a character may do it once
 * @param sleeplessUntil    the player may not sleep before this: rewriting the sky costs a night without sleep
 * @param mark              the Wanderer's mark, or {@code null}
 */
public record RaceState(CharacterData character, long cooldownUntil, long signCooldownUntil, long reprieveUntil,
                        boolean skyRewritten, long sleeplessUntil, Mark mark) {
    public static final RaceState EMPTY = of(new CharacterData());
    public static final Codec<RaceState> CODEC = Codec.STRING.xmap(RaceState::read, RaceState::json);

    /** A place the Wanderer returns to: the dimension's id and the position. */
    public record Mark(String dimension, double x, double y, double z) {}

    public static RaceState of(CharacterData character) {
        return new RaceState(character, 0, 0, 0, false, 0, null);
    }

    public RaceState withCharacter(CharacterData c) {
        return new RaceState(c, cooldownUntil, signCooldownUntil, reprieveUntil, skyRewritten, sleeplessUntil, mark);
    }

    public RaceState withCooldown(long until) {
        return new RaceState(character, until, signCooldownUntil, reprieveUntil, skyRewritten, sleeplessUntil, mark);
    }

    public RaceState withSignCooldown(long until) {
        return new RaceState(character, cooldownUntil, until, reprieveUntil, skyRewritten, sleeplessUntil, mark);
    }

    public RaceState withReprieve(long until) {
        return new RaceState(character, cooldownUntil, signCooldownUntil, until, skyRewritten, sleeplessUntil, mark);
    }

    public RaceState withMark(Mark m) {
        return new RaceState(character, cooldownUntil, signCooldownUntil, reprieveUntil, skyRewritten, sleeplessUntil,
            m);
    }

    /** The sky is rewritten: the sign is cleared to be chosen again, and the night until {@code until} is sleepless. */
    public RaceState withSkyRewritten(CharacterData c, long until) {
        return new RaceState(c, cooldownUntil, signCooldownUntil, reprieveUntil, true, until, mark);
    }

    public String json() { return DataJson.GSON.toJson(this); }

    public static RaceState read(String json) {
        try {
            var s = DataJson.GSON.fromJson(json, RaceState.class);
            if (s == null || s.character == null) return EMPTY;
            var c = s.character.selected ? RaceCatalog.validate(s.character) : new CharacterData();
            return new RaceState(c, Math.max(0, s.cooldownUntil), Math.max(0, s.signCooldownUntil),
                Math.max(0, s.reprieveUntil), s.skyRewritten, Math.max(0, s.sleeplessUntil), s.mark);
        } catch (RuntimeException e) { return EMPTY; }
    }
}
