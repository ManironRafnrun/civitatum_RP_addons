package team.civitatum.menu;

import com.terraformersmc.mod_menu.gui.ModsScreen;
import com.terraformersmc.mod_menu.gui.widget.ModMenuButtonWidget;
import com.terraformersmc.mod_menu.gui.widget.UpdateCheckerTexturedButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ModListScreen;

/** Opens the Better ModList screen when that mod is present, otherwise the NeoForge mod list. */
public final class ModsMenu {
    /** Better ModList is a port of Fabric's Mod Menu and keeps its mod id. */
    private static final String BETTER_MODLIST = "mod_menu";

    private ModsMenu() {
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(
            isBetterModListLoaded() ? BetterModList.screen(parent) : new ModListScreen(parent));
    }

    /**
     * Better ModList adds its own mods button (a gear or a text button, by its config) to every {@code TitleScreen},
     * our subclass included, next to our button that already opens its screen.
     */
    public static boolean isBetterModListButton(GuiEventListener widget) {
        return isBetterModListLoaded() && BetterModList.isModsButton(widget);
    }

    private static boolean isBetterModListLoaded() {
        return ModList.get().isLoaded(BETTER_MODLIST);
    }

    /** The only place that touches Better ModList classes, so it is loaded only when that mod is present. */
    private static final class BetterModList {
        static Screen screen(Screen parent) {
            return new ModsScreen(parent);
        }

        static boolean isModsButton(GuiEventListener widget) {
            return widget instanceof ModMenuButtonWidget || widget instanceof UpdateCheckerTexturedButtonWidget;
        }
    }
}
