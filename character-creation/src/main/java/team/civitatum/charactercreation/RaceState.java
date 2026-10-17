package team.civitatum.charactercreation;

import com.mojang.serialization.Codec;

public record RaceState(CharacterData character, int curseDeaths, long cooldownUntil) {
    public static final RaceState EMPTY = new RaceState(new CharacterData(), 0, 0);
    public static final Codec<RaceState> CODEC = Codec.STRING.xmap(RaceState::read, RaceState::json);
    public String json() { return DataJson.GSON.toJson(this); }
    public static RaceState read(String json) {
        try {
            var s = DataJson.GSON.fromJson(json, RaceState.class);
            if (s == null || s.character == null) return EMPTY;
            var c = s.character.selected ? RaceCatalog.validate(s.character) : new CharacterData();
            return new RaceState(c, Math.max(0, Math.min(s.curseDeaths, 10000)), Math.max(0, s.cooldownUntil));
        } catch (RuntimeException e) { return EMPTY; }
    }
}
