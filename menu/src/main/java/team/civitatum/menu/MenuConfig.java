package team.civitatum.menu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/** {@code config/civitatum-menu.json}; created with defaults on first launch, out-of-range values are clamped. */
public final class MenuConfig {
    public boolean enabled = true;
    public String season = "auto";
    public boolean southernHemisphere = false;
    public boolean parallax = true;
    public double parallaxIntensity = 0.02;
    public double parallaxResponse = 8.0;
    public boolean music = true;
    public double musicVolume = 1.0;
    public String footer = "Civitatum Team – Право на Возвращение";
    public int menuLeft = 51;
    public int menuVerticalOffset = -39;
    public int logoWidth = 351;
    public int logoHeight = 70;
    public int logoTop = 12;
    public boolean showLanguageAndAccessibility = true;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static MenuConfig current = new MenuConfig();

    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("civitatum-menu.json");

        try {
            if (Files.isRegularFile(path)) {
                MenuConfig read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), MenuConfig.class);
                if (read != null) {
                    current = read;
                }
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(current), StandardCharsets.UTF_8);
            }
        } catch (RuntimeException | IOException e) {
            CivitatumMenu.LOG.warn("Cannot read menu configuration; using defaults", e);
            current = new MenuConfig();
        }

        current.parallaxIntensity = finiteClamp(current.parallaxIntensity, 0.0, 0.1, 0.02);
        current.parallaxResponse = finiteClamp(current.parallaxResponse, 0.1, 40.0, 8.0);
        current.musicVolume = finiteClamp(current.musicVolume, 0.0, 1.0, 1.0);
        current.logoWidth = Math.max(64, Math.min(700, current.logoWidth));
        current.logoHeight = Math.max(16, Math.min(200, current.logoHeight));
        current.logoTop = Math.max(0, Math.min(150, current.logoTop));
        current.menuLeft = Math.max(4, Math.min(500, current.menuLeft));
        current.menuVerticalOffset = Math.max(-150, Math.min(100, current.menuVerticalOffset));
        if (current.footer == null) {
            current.footer = "Civitatum Team – Право на Возвращение";
        }
    }

    private static double finiteClamp(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
