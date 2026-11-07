package team.civitatum.hud.tools;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Restyles the FTB Library sidebar for the windows' look: each button becomes a stone tile in a gold bevel with its
 * drawing recoloured to gold. The files go to the built-in pack: new pictures under {@code civitatum_hud}, and copies
 * of the mods' button definitions that differ only in {@code icon}.
 *
 * <p>Run with {@code ./gradlew :hud:generateSidebarIcons -Pmods=<folder>} after the module has been set up once
 * (vanilla's sword comes from the resources jar that ModDevGradle unpacks).
 */
public final class SidebarIcons {
    /** The sidebar draws a button in 16 units. Four texels per unit match the usual GUI scale at 1080p exactly. */
    private static final int TEXELS_PER_UNIT = 4;
    private static final int UNITS = 16;
    private static final int SIZE = UNITS * TEXELS_PER_UNIT;
    /** Each source pixel takes three texels, which leaves two units around the drawing for the bevel. */
    private static final int ICON_SCALE = 3;
    private static final int ICON_START = (SIZE - UNITS * ICON_SCALE) / 2;
    /** The colours of {@code MorrowindSkin.frame}. */
    private static final int OUTLINE = 0xFF17130E, SHADOW = 0xFF54422C, GOLD = 0xFFB29A6B;
    /** The drawing's darkest and lightest colours: both stand out on the dark stone, as the window titles do. */
    private static final int ICON_DARK = 0x8F7A52, ICON_LIGHT = 0xF4DDAF;
    /** The config button's hint "right click to edit": the HUD fixes the layout and switches the editor off. */
    private static final Pattern EDIT_HINT = Pattern.compile(",\\s*\"tooltip\"\\s*:\\s*\\[[^\\]]*\\]");
    private static final Pattern ICON = Pattern.compile("\"icon\"\\s*:\\s*(\\[[^\\]]*\\]|\"[^\"]*\")");
    private static final Path TEXTURES = VanillaStyle.PACK.resolve("assets/civitatum_hud/textures/gui/sidebar");
    private static final Path STONE = Path.of("src/main/resources/assets/civitatum_hud/textures/gui/stone.png");

    private record Button(String jarPrefix, String namespace, String definition, String source) {
        String name() {
            return definition.substring(definition.lastIndexOf('/') + 1);
        }
    }

    private static final List<Button> BUTTONS = List.of(
        new Button("ftb-library", "ftblibrary", "config", "assets/ftblibrary/textures/icons/settings.png"),
        new Button("ftb-library", "ftblibrary", "toggle/rain", "assets/ftblibrary/textures/icons/toggle_rain.png"),
        new Button("ftb-library", "ftblibrary", "toggle/day", "assets/ftblibrary/textures/icons/toggle_day.png"),
        new Button("ftb-library", "ftblibrary", "toggle/night", "assets/ftblibrary/textures/icons/toggle_night.png"),
        new Button("ftb-library", "ftblibrary", "toggle/gamemode", "assets/minecraft/textures/item/diamond_sword.png"),
        new Button("ftb-quests", "ftbquests", "quests", "assets/ftbquests/textures/item/book.png"),
        new Button("ftb-teams", "ftbteams", "my_team", "assets/ftbteams/textures/teams.png"),
        new Button("ftb-teams", "ftbteams", "team_lives", "assets/ftblibrary/textures/icons/heart.png"));

    private SidebarIcons() {
    }

    public static void main(String[] args) throws IOException {
        Path vanilla = Path.of(args[0]), mods = Path.of(args[1]);
        BufferedImage stone = ImageIO.read(STONE.toFile());
        Files.createDirectories(TEXTURES);
        for (Button button : BUTTONS) {
            Path jar = jarOf(mods, button.jarPrefix());
            String definition = new String(read(jar, "assets/" + button.namespace() + "/sidebar_buttons/"
                + button.definition() + ".json"), StandardCharsets.UTF_8);
            byte[] source = button.source().startsWith("assets/minecraft/")
                ? read(vanilla, button.source())
                : read(button.source().startsWith("assets/ftblibrary/") ? jarOf(mods, "ftb-library") : jar,
                    button.source());
            ImageIO.write(tile(stone, ImageIO.read(new ByteArrayInputStream(source))), "png",
                TEXTURES.resolve(button.name() + ".png").toFile());
            Path json = VanillaStyle.PACK.resolve("assets/" + button.namespace() + "/sidebar_buttons/"
                + button.definition() + ".json");
            Files.createDirectories(json.getParent());
            Files.writeString(json, withIcon(definition, button.name()));
        }
    }

    private static String withIcon(String definition, String name) {
        Matcher matcher = ICON.matcher(definition);
        if (!matcher.find()) throw new IllegalStateException("No icon in " + definition);
        String restyled = matcher.replaceFirst(Matcher.quoteReplacement(
            "\"icon\": \"civitatum_hud:textures/gui/sidebar/" + name + ".png\""));
        return EDIT_HINT.matcher(restyled).replaceFirst("");
    }

    private static Path jarOf(Path mods, String prefix) throws IOException {
        try (var files = Files.list(mods)) {
            return files.filter(file -> file.getFileName().toString().startsWith(prefix)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + prefix + " jar in " + mods));
        }
    }

    private static byte[] read(Path jar, String entry) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var found = zip.getEntry(entry);
            if (found == null) throw new IllegalStateException("No " + entry + " in " + jar);
            try (var input = zip.getInputStream(found)) {
                return input.readAllBytes();
            }
        }
    }

    private static BufferedImage tile(BufferedImage stone, BufferedImage icon) {
        var tile = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                tile.setRGB(x, y, stone.getRGB(x * stone.getWidth() / SIZE, y * stone.getHeight() / SIZE) | 0xFF000000);
            }
        }
        drawIcon(tile, icon);
        bevel(tile);
        return tile;
    }

    /** Lightness sets the shade between the two gold colours, stretched over the range the drawing really uses. */
    private static void drawIcon(BufferedImage tile, BufferedImage icon) {
        double min = 1, max = 0;
        for (int y = 0; y < UNITS; y++) {
            for (int x = 0; x < UNITS; x++) {
                int rgb = icon.getRGB(x, y);
                if (rgb >>> 24 == 0) continue;
                min = Math.min(min, lightness(rgb));
                max = Math.max(max, lightness(rgb));
            }
        }
        double range = Math.max(max - min, 1e-3);
        for (int y = 0; y < UNITS; y++) {
            for (int x = 0; x < UNITS; x++) {
                int rgb = icon.getRGB(x, y);
                if (rgb >>> 24 == 0) continue;
                int colour = mix(ICON_DARK, ICON_LIGHT, (lightness(rgb) - min) / range);
                for (int dy = 0; dy < ICON_SCALE; dy++) {
                    for (int dx = 0; dx < ICON_SCALE; dx++) {
                        int px = ICON_START + x * ICON_SCALE + dx, py = ICON_START + y * ICON_SCALE + dy;
                        tile.setRGB(px, py, over(colour, rgb >>> 24, tile.getRGB(px, py)));
                    }
                }
            }
        }
    }

    private static double lightness(int rgb) {
        return (0.299 * (rgb >> 16 & 255) + 0.587 * (rgb >> 8 & 255) + 0.114 * (rgb & 255)) / 255;
    }

    private static int mix(int from, int to, double t) {
        return channel(from >> 16, to >> 16, t) << 16 | channel(from >> 8, to >> 8, t) << 8 | channel(from, to, t);
    }

    private static int channel(int from, int to, double t) {
        return (int) Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
    }

    private static int over(int colour, int alpha, int below) {
        return 0xFF000000 | mix(below, colour, alpha / 255.0);
    }

    /** {@code MorrowindSkin.frame}, one unit wide per ring. */
    private static void bevel(BufferedImage tile) {
        int t = TEXELS_PER_UNIT;
        fill(tile, t, t, SIZE - t, 2 * t, GOLD);
        fill(tile, t, t, 2 * t, SIZE - t, GOLD);
        fill(tile, 2 * t, SIZE - 2 * t, SIZE - t, SIZE - t, SHADOW);
        fill(tile, SIZE - 2 * t, 2 * t, SIZE - t, SIZE - t, SHADOW);
        fill(tile, 0, 0, SIZE, t, OUTLINE);
        fill(tile, 0, SIZE - t, SIZE, SIZE, OUTLINE);
        fill(tile, 0, 0, t, SIZE, OUTLINE);
        fill(tile, SIZE - t, 0, SIZE, SIZE, OUTLINE);
    }

    private static void fill(BufferedImage image, int x0, int y0, int x1, int y1, int colour) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                image.setRGB(x, y, colour);
            }
        }
    }
}
