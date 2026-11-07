package team.civitatum.starclock.prediction;

import com.google.gson.JsonObject;
import java.util.Set;

public record ActionSpec(String type, JsonObject params) {
    private static final Set<String> TYPES = Set.of("spawn", "effect", "loot", "block", "teleport",
            "damage", "durability", "weather", "trader");

    public ActionSpec {
        if (!TYPES.contains(type) || params == null) throw new IllegalArgumentException("Неизвестное действие: " + type);
        params = params.deepCopy();
    }

    @Override
    public JsonObject params() {
        return params.deepCopy();
    }
}
