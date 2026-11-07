package team.civitatum.menu;

import java.util.List;
import java.util.Set;
import net.mehvahdjukaar.supplementaries.client.screens.ConfigButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.violetmoon.quark.base.client.config.QButton;

/**
 * Tidies the buttons other mods put on every {@code TitleScreen}, our subclass included.
 * Removes Better ModList's mods button, Supplementaries' gear (by its config it opens a mod list),
 * Cumulus' "Menu List", "W" (world preview toggle) and "Q" (quick load);
 * replaces Quark's "q" by our own in the top-right row.
 * Must run after their own {@code ScreenEvent.Init.Post} listeners.
 */
public final class ForeignButtons {
    private static final String SUPPLEMENTARIES = "supplementaries";
    /** Cumulus comes inside The Aether. */
    private static final String CUMULUS = "cumulus_menus";
    private static final String QUARK = "quark";
    /** The label of Quark's own button. */
    private static final String QUARK_LABEL = "q";
    /** Cumulus builds its buttons as plain vanilla ones, so only their labels tell them apart. */
    private static final Set<String> CUMULUS_BUTTONS =
        Set.of("gui.cumulus_menus.button.menu_list", "gui.cumulus_menus.menu.button.world_preview",
            "gui.cumulus_menus.menu.button.quick_load");

    private ForeignButtons() {
    }

    public static void arrange(SeasonalTitleScreen screen, ScreenEvent.Init.Post event) {
        boolean supplementaries = ModList.get().isLoaded(SUPPLEMENTARIES);
        boolean cumulus = ModList.get().isLoaded(CUMULUS);
        boolean quark = ModList.get().isLoaded(QUARK);
        for (GuiEventListener widget : List.copyOf(event.getListenersList())) {
            if (ModsMenu.isBetterModListButton(widget)
                || supplementaries && Supplementaries.isConfigButton(widget)
                || cumulus && isCumulusButton(widget)) {
                event.removeListener(widget);
            } else if (quark && Quark.isQButton(widget)) {
                // Quark pins it to the left of the button labelled like NeoForge's "Mods", which is our pair;
                // a framed vanilla button, so it is replaced by our own that does the same
                event.removeListener(widget);
                event.addListener(screen.topRightText(Component.literal(QUARK_LABEL), () -> Quark.click(widget)));
            }
        }
    }

    private static boolean isCumulusButton(GuiEventListener widget) {
        return widget instanceof AbstractWidget button
            && button.getMessage().getContents() instanceof TranslatableContents label
            && CUMULUS_BUTTONS.contains(label.getKey());
    }

    /** The only place that touches Supplementaries classes, so it is loaded only when that mod is present. */
    private static final class Supplementaries {
        static boolean isConfigButton(GuiEventListener widget) {
            return widget instanceof ConfigButton;
        }
    }

    /** The only place that touches Quark classes, so it is loaded only when that mod is present. */
    private static final class Quark {
        static boolean isQButton(GuiEventListener widget) {
            return widget instanceof QButton;
        }

        /** Quark's own handler, given its button so that it still clears the "new features" bubble. */
        static void click(GuiEventListener widget) {
            QButton.click((QButton)widget);
        }
    }
}
