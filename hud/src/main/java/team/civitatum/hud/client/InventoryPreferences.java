package team.civitatum.hud.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InventoryPreferences {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "civitatum-hud.json";
    /** The file of the mod's earlier name, Civitatum Inventory: read while the new file does not exist yet. */
    private static final String LEGACY_FILE = "civitatum-inventory.json";
    private static final HotbarStyle DEFAULT_HOTBAR_STYLE = HotbarStyle.MORROWIND;
    /** The HUD blocks' sizes before the setting existed; the range lets each shrink or grow from it. */
    private static final double DEFAULT_HUD_SCALE = 1.0;
    public static final double MIN_HUD_SCALE = 0.5, MAX_HUD_SCALE = 2.0;
    private static InventoryPreferences instance;
    public HotbarStyle hotbarStyle = DEFAULT_HOTBAR_STYLE;
    /** Health, mana and food bars. */
    public double barsScale = DEFAULT_HUD_SCALE;
    public double hotbarScale = DEFAULT_HUD_SCALE;
    public double effectsScale = DEFAULT_HUD_SCALE;
    public String selectedAbility = "civitatum:racial_gift";
    public boolean powersVisible = true;
    /** The cosmetic column of the accessories window, for menus that leave it to this screen. */
    public boolean accessoryCosmetics = false;
    public Map<String, SavedPanel> panels = new LinkedHashMap<>();
    public record SavedPanel(float x, float y, int width, int height) {}

    /**
     * Loaded once. Only the file can hold out-of-range values: the sliders write values inside their own range,
     * so the bounds are applied on load and not on every call.
     */
    public static InventoryPreferences get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static InventoryPreferences load() {
        var result = new InventoryPreferences();
        var path = path();
        if (!Files.isRegularFile(path)) path = path.resolveSibling(LEGACY_FILE);
        if (Files.isRegularFile(path)) try {
            var loaded = JSON.fromJson(Files.readString(path), InventoryPreferences.class);
            if (loaded != null) {
                result = loaded;
                if (result.panels == null) result.panels = new LinkedHashMap<>();
            }
        } catch (Exception ex) {
            LogUtils.getLogger().warn("Civitatum HUD: could not load layout", ex);
        }
        result.barsScale = bounded(result.barsScale, MIN_HUD_SCALE, MAX_HUD_SCALE, DEFAULT_HUD_SCALE);
        result.hotbarScale = bounded(result.hotbarScale, MIN_HUD_SCALE, MAX_HUD_SCALE, DEFAULT_HUD_SCALE);
        result.effectsScale = bounded(result.effectsScale, MIN_HUD_SCALE, MAX_HUD_SCALE, DEFAULT_HUD_SCALE);
        // Gson reads an unknown style name as null.
        if (result.hotbarStyle == null) result.hotbarStyle = DEFAULT_HOTBAR_STYLE;
        return result;
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    private static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }

    public void save() {
        var path = path();
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temporary, JSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            LogUtils.getLogger().warn("Civitatum HUD: could not save layout", ex);
        }
    }
}
