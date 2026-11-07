package team.civitatum.charactercreation;

import java.util.*;

/** Initial Civitatum balance templates inspired by TES III archetypes, not copied game statistics. */
public final class BirthsignCatalog {
    /** Texts live in the lang files: {@code birthsign.civitatum.<id>.name|description|effect}. */
    public record Sign(String id) {
        public String key(String field) { return "birthsign.civitatum." + id + "." + field; }
    }
    private static final List<Sign> ALL=List.of(
        new Sign("none"),
        new Sign("warrior"),
        new Sign("mage"),
        new Sign("thief"),
        new Sign("lady"),
        new Sign("steed"),
        new Sign("lord")
    );
    private static final Map<String, Sign> BY_ID = byId();
    private static Map<String, Sign> byId() {
        var byId = new HashMap<String, Sign>();
        for (var sign : ALL) byId.putIfAbsent(sign.id, sign);
        return byId;
    }
    public static List<Sign> all(){return ALL;}
    public static Sign get(String id){return BY_ID.get(id);}
}
