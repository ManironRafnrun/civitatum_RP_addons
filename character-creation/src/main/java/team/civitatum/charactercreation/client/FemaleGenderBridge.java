package team.civitatum.charactercreation.client;

import java.util.*;

import net.neoforged.fml.ModList;
import team.civitatum.charactercreation.CharacterData;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.ModConfig;

/**
 * Public Female Gender Mod 3.2.2 methods; neither its implementation nor assets are bundled.
 */
public final class FemaleGenderBridge {
    private static boolean failed;
    private static final Map<UUID, String> APPLIED = new HashMap<>();
    /**
     * The character object last applied per player. The layer calls {@link #apply} every frame; a synced character
     * is never modified, so the same object needs neither a JSON comparison nor another call into the mod.
     */
    private static final Map<UUID, CharacterData> APPLIED_DATA = new HashMap<>();

    public static boolean available() {
        return ModConfig.INSTANCE.femaleGenderAdapter && !failed && ModList.get().isLoaded("wildfire_gender");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void apply(UUID uuid, CharacterData c, boolean force) {
        if (!available()) return;
        if (!force && APPLIED_DATA.get(uuid) == c) return;
        String key = c.json();
        if (!force && key.equals(APPLIED.get(uuid))) {
            APPLIED_DATA.put(uuid, c);
            return;
        }
        try {
            Class<?> mod = Class.forName("com.wildfire.main.WildfireGender");
            Object player = mod.getMethod("getOrAddPlayerById", UUID.class).invoke(null, uuid);
            Class gender = Class.forName("com.wildfire.main.Gender");
            player.getClass().getMethod("updateGender", gender).invoke(player, Enum.valueOf(gender, c.female() && !c.race.equals("forged") ? "FEMALE" : "MALE"));
            call(player, "updateBustSize", c.bust);
            call(player, "updateBreastPhysics", c.breastPhysics);
            call(player, "updateArmorPhysicsOverride", c.armorPhysics);
            call(player, "updateShowBreastsInArmor", c.showInArmor);
            call(player, "updateBounceMultiplier", c.bounce);
            call(player, "updateFloppiness", c.floppiness);
            Object breasts = player.getClass().getMethod("getBreasts").invoke(player);
            call(breasts, "updateXOffset", c.bustX);
            call(breasts, "updateYOffset", c.bustY);
            call(breasts, "updateZOffset", c.bustZ);
            call(breasts, "updateCleavage", c.cleavage);
            call(breasts, "updateUniboob", c.uniboob);
            APPLIED.put(uuid, key);
            APPLIED_DATA.put(uuid, c);
        } catch (ReflectiveOperationException ex) {
            failed = true;
            Civitatum.LOGGER.warn("Female Gender Mod API differs from 3.2.2; using built-in chest model: {}", ex.toString());
        }
    }

    private static void call(Object object, String method, Object value) throws ReflectiveOperationException {
        object.getClass().getMethod(method, value instanceof Boolean ? boolean.class : float.class).invoke(object, value);
    }

    public static void clear() {
        APPLIED.clear();
        APPLIED_DATA.clear();
        failed = false;
    }
}
