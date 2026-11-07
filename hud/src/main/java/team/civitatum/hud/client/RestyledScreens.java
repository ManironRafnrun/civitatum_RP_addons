package team.civitatum.hud.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;

/**
 * Screens whose backgrounds this mod paints dark (the built-in pack {@code resourcepacks/civitatum_style}): vanilla's
 * block screens and the mods' screens listed by the style generator in {@code civitatum_hud/restyled_screens.txt}.
 * Their labels are drawn in dark colours, unreadable on stone, so those are swapped for our light colour there.
 *
 * <p>Exact classes only: a subclass may draw its own light background under the same labels.
 */
public final class RestyledScreens {
    private static final String LIST = "/civitatum_hud/restyled_screens.txt";
    /** The window titles of the inventory screen, drawn with a shadow as there. */
    public static final int LABEL = 0xFFE9E3D4;
    private static final Set<Class<? extends Screen>> SCREENS = Set.of(ContainerScreen.class,
        ShulkerBoxScreen.class, DispenserScreen.class, HopperScreen.class, FurnaceScreen.class,
        BlastFurnaceScreen.class, SmokerScreen.class, CraftingScreen.class, BrewingStandScreen.class,
        AnvilScreen.class, EnchantmentScreen.class, GrindstoneScreen.class, SmithingScreen.class,
        StonecutterScreen.class, LoomScreen.class, CartographyTableScreen.class, BeaconScreen.class,
        CrafterScreen.class, CreativeModeInventoryScreen.class);
    private static final Set<String> MOD_SCREENS = read();

    /** Whether text of this colour is a label on a dark background now. Labels are passed without alpha or opaque. */
    public static boolean darkLabel(int color) {
        int alpha = color >>> 24;
        if (alpha != 0 && alpha != 0xFF || !LabelColours.dark(color)) return false;
        return restyled(Minecraft.getInstance().screen);
    }

    /** The text with dark colours of its own style ({@link LabelColours}) drawn as {@link #LABEL}. */
    public static net.minecraft.util.FormattedCharSequence lightStyles(net.minecraft.util.FormattedCharSequence text) {
        return sink -> text.accept((index, style, codePoint) -> {
            var colour = style.getColor();
            boolean dark = colour != null && LabelColours.dark(colour.getValue());
            return sink.accept(index, dark ? style.withColor(LABEL & 0xFFFFFF) : style, codePoint);
        });
    }

    public static boolean restyled(Screen screen) {
        if (screen == null) return false;
        return SCREENS.contains(screen.getClass()) || MOD_SCREENS.contains(screen.getClass().getName());
    }

    private static Set<String> read() {
        var names = new HashSet<String>();
        var in = RestyledScreens.class.getResourceAsStream(LIST);
        if (in == null) return names;
        try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).forEach(names::add);
        } catch (IOException ex) {
            org.slf4j.LoggerFactory.getLogger("Civitatum HUD").warn("Cannot read {}: {}", LIST, ex.toString());
        }
        return names;
    }

    private RestyledScreens() {}
}
