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
    private static final String FILE = "civitatum-menu.json";
    private static final double DEFAULT_PARALLAX_INTENSITY = 0.02;
    private static final double DEFAULT_PARALLAX_RESPONSE = 8.0;
    private static final double DEFAULT_MUSIC_VOLUME = 1.0;
    /** A translation key; any other text in the config is shown as written. */
    private static final String DEFAULT_FOOTER = "menu.civitatum_menu.footer";
    /** The footer written by 0.1.0, before translations; it is replaced by the translated default. */
    private static final String OLD_DEFAULT_FOOTER = "Civitatum Team – Право на Возвращение";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static MenuConfig current = new MenuConfig();

    public boolean enabled = true;
    public String season = "auto";
    public boolean southernHemisphere = false;
    public boolean parallax = true;
    public double parallaxIntensity = DEFAULT_PARALLAX_INTENSITY;
    public double parallaxResponse = DEFAULT_PARALLAX_RESPONSE;
    public boolean music = true;
    public double musicVolume = DEFAULT_MUSIC_VOLUME;
    public String footer = DEFAULT_FOOTER;
    public int menuLeft = 51;
    public int menuVerticalOffset = -39;
    public int logoWidth = 351;
    public int logoHeight = 70;
    public int logoTop = 12;
    public boolean showLanguageAndAccessibility = true;

    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE);

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

        current.parallaxIntensity = finiteClamp(current.parallaxIntensity, 0.0, 0.1, DEFAULT_PARALLAX_INTENSITY);
        current.parallaxResponse = finiteClamp(current.parallaxResponse, 0.1, 40.0, DEFAULT_PARALLAX_RESPONSE);
        current.musicVolume = finiteClamp(current.musicVolume, 0.0, 1.0, DEFAULT_MUSIC_VOLUME);
        current.logoWidth = Math.clamp(current.logoWidth, 64, 700);
        current.logoHeight = Math.clamp(current.logoHeight, 16, 200);
        current.logoTop = Math.clamp(current.logoTop, 0, 150);
        current.menuLeft = Math.clamp(current.menuLeft, 4, 500);
        current.menuVerticalOffset = Math.clamp(current.menuVerticalOffset, -150, 100);
        if (current.footer == null || current.footer.equals(OLD_DEFAULT_FOOTER)) {
            current.footer = DEFAULT_FOOTER;
        }
    }

    private static double finiteClamp(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
