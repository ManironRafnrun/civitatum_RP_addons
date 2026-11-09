package team.civitatum.charactercreation.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import team.civitatum.charactercreation.CharacterData;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.ModConfig;

/**
 * Public Female Gender Mod 3.2.2 methods; neither its implementation nor assets are bundled. With the mod installed it
 * draws the chest everywhere, in the editor too, so the editor shows the shape the world shows; the built-in model is
 * only for a game without it. The methods are looked up once.
 */
public final class FemaleGenderBridge {
    /** The editor's figure has the player's own UUID; the mod finds its settings by UUID, so it gets its own set. */
    private static final UUID PREVIEW_ID =
            UUID.nameUUIDFromBytes("civitatum:editor_preview".getBytes(StandardCharsets.UTF_8));
    /**
     * Physics ticks to settle the editor's chest at once: the mod moves its size halfway to the setting each tick,
     * 16 halvings leave 1/65536 of a change.
     */
    private static final int SETTLE_TICKS = 16;

    private static boolean failed;
    private static Api api;
    private static final Map<UUID, String> APPLIED = new HashMap<>();
    /**
     * The character object last applied per player. The layer calls {@link #apply} every frame; a synced character
     * is never modified, so the same object needs neither a JSON comparison nor another call into the mod.
     */
    private static final Map<UUID, CharacterData> APPLIED_DATA = new HashMap<>();
    /**
     * Set when the mod overwrote a player's settings: its own sync from the server, its settings file, its screen.
     * Set from its loading thread too, so the maps are cleared on the render thread, at the next apply.
     */
    private static volatile boolean stale;

    private static Object previewConfig;
    private static String previewKey;
    private static int previewTick;
    /** The player's own settings while the editor's figure is drawn in their place. */
    private static Object swapped;

    public static boolean available() {
        return ModConfig.INSTANCE.femaleGenderAdapter && !failed && ModList.get().isLoaded("wildfire_gender");
    }

    public static void apply(UUID uuid, CharacterData c, boolean force) {
        if (!available()) return;
        if (stale) {
            stale = false;
            APPLIED.clear();
            APPLIED_DATA.clear();
        }
        if (!force && APPLIED_DATA.get(uuid) == c) return;
        String key = c.json();
        if (!force && key.equals(APPLIED.get(uuid))) {
            APPLIED_DATA.put(uuid, c);
            return;
        }
        try {
            Api mod = api();
            mod.write(mod.getOrAddPlayerById.invoke(null, uuid), c);
            APPLIED.put(uuid, key);
            APPLIED_DATA.put(uuid, c);
        } catch (ReflectiveOperationException ex) {
            fail(ex);
        }
    }

    /**
     * Before the mod draws the editor's figure: its own settings get the editor's values and stand in for the
     * player's, under the player's UUID. Their physics never ticks with the world, the figure is not in it, so it is
     * ticked here once a game tick, and settled at once when a value changes.
     */
    public static void beginPreview(LivingEntity entity, CharacterData draft) {
        if (!available() || draft == null) return;
        try {
            Api mod = api();
            if (previewConfig == null) previewConfig = mod.newPlayerConfig.newInstance(PREVIEW_ID);
            String key = draft.json();
            int ticks = 0;
            if (!key.equals(previewKey)) {
                mod.write(previewConfig, draft);
                previewKey = key;
                ticks = SETTLE_TICKS;
            } else if (entity.tickCount != previewTick) {
                ticks = 1;
            }
            previewTick = entity.tickCount;
            for (int i = 0; i < ticks; i++) mod.tickBreastPhysics.invoke(previewConfig, entity);
            swapped = mod.playerCache.put(entity.getUUID(), previewConfig);
        } catch (ReflectiveOperationException ex) {
            fail(ex);
        }
    }

    /** After the mod drew the editor's figure: the player's own settings are back. */
    public static void endPreview(LivingEntity entity) {
        if (api == null || previewConfig == null) return;
        if (swapped != null) api.playerCache.put(entity.getUUID(), swapped);
        else api.playerCache.remove(entity.getUUID(), previewConfig);
        swapped = null;
    }

    /** The mod overwrote someone's settings; called by its hooks (FemaleGenderSyncMixin, FemaleGenderConfigMixin). */
    public static void invalidate() {
        stale = true;
    }

    public static void clear() {
        APPLIED.clear();
        APPLIED_DATA.clear();
        previewConfig = null;
        previewKey = null;
        failed = false;
    }

    private static void fail(ReflectiveOperationException ex) {
        failed = true;
        Civitatum.LOGGER.warn("Female Gender Mod API differs from 3.2.2; using built-in chest model: {}",
                ex.toString());
    }

    private static Api api() throws ReflectiveOperationException {
        if (api == null) api = new Api();
        return api;
    }

    /** The mod's classes and methods, found once. */
    private static final class Api {
        final Method getOrAddPlayerById;
        final Constructor<?> newPlayerConfig;
        final Map<UUID, Object> playerCache;
        final Method tickBreastPhysics;
        @SuppressWarnings("rawtypes")
        final Class<? extends Enum> gender;
        final Method updateGender, updateBustSize, updateHurtSounds, updateBreastPhysics, updateArmorPhysicsOverride,
                updateShowBreastsInArmor, updateBounceMultiplier, updateFloppiness, getBreasts;
        final Method updateXOffset, updateYOffset, updateZOffset, updateCleavage, updateUniboob;

        @SuppressWarnings({"unchecked", "rawtypes"})
        Api() throws ReflectiveOperationException {
            Class<?> mod = Class.forName("com.wildfire.main.WildfireGender");
            Class<?> config = Class.forName("com.wildfire.main.entitydata.PlayerConfig");
            Class<?> breasts = Class.forName("com.wildfire.main.entitydata.Breasts");
            gender = (Class) Class.forName("com.wildfire.main.Gender");
            getOrAddPlayerById = mod.getMethod("getOrAddPlayerById", UUID.class);
            playerCache = (Map<UUID, Object>) mod.getField("PLAYER_CACHE").get(null);
            newPlayerConfig = config.getConstructor(UUID.class);
            tickBreastPhysics = config.getMethod("tickBreastPhysics", LivingEntity.class);
            updateGender = config.getMethod("updateGender", gender);
            updateBustSize = config.getMethod("updateBustSize", float.class);
            updateHurtSounds = config.getMethod("updateHurtSounds", boolean.class);
            updateBreastPhysics = config.getMethod("updateBreastPhysics", boolean.class);
            updateArmorPhysicsOverride = config.getMethod("updateArmorPhysicsOverride", boolean.class);
            updateShowBreastsInArmor = config.getMethod("updateShowBreastsInArmor", boolean.class);
            updateBounceMultiplier = config.getMethod("updateBounceMultiplier", float.class);
            updateFloppiness = config.getMethod("updateFloppiness", float.class);
            getBreasts = config.getMethod("getBreasts");
            updateXOffset = breasts.getMethod("updateXOffset", float.class);
            updateYOffset = breasts.getMethod("updateYOffset", float.class);
            updateZOffset = breasts.getMethod("updateZOffset", float.class);
            updateCleavage = breasts.getMethod("updateCleavage", float.class);
            updateUniboob = breasts.getMethod("updateUniboob", boolean.class);
        }

        /** Every setting of the mod from the character; a forged one is built without a chest. */
        @SuppressWarnings("unchecked")
        void write(Object player, CharacterData c) throws ReflectiveOperationException {
            String sex = c.female() && !c.race.equals("forged") ? "FEMALE" : "MALE";
            updateGender.invoke(player, Enum.valueOf(gender, sex));
            updateBustSize.invoke(player, c.bust);
            updateHurtSounds.invoke(player, c.hurtSounds);
            updateBreastPhysics.invoke(player, c.breastPhysics);
            updateArmorPhysicsOverride.invoke(player, c.armorPhysics);
            updateShowBreastsInArmor.invoke(player, c.showInArmor);
            updateBounceMultiplier.invoke(player, c.bounce);
            updateFloppiness.invoke(player, c.floppiness);
            Object breasts = getBreasts.invoke(player);
            updateXOffset.invoke(breasts, c.bustX);
            updateYOffset.invoke(breasts, c.bustY);
            updateZOffset.invoke(breasts, c.bustZ);
            updateCleavage.invoke(breasts, c.cleavage);
            updateUniboob.invoke(breasts, c.uniboob);
        }
    }
}
