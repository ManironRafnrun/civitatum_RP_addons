package team.civitatum.hud.client;

import dev.ftb.mods.ftblibrary.sidebar.RegisteredSidebarButton;
import dev.ftb.mods.ftblibrary.sidebar.SidebarButtonManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * Optional adapter to FTB Quests through FTB Library's sidebar: the inventory's "Quests" button presses FTB Quests'
 * own sidebar button, so the book opens exactly as from the sidebar, which no longer shows it. FTB Library is a
 * compile-only dependency; its classes are touched only in {@link Linked}, after both mods are confirmed present.
 */
public final class QuestsBridge {
    /** FTB Quests' sidebar button, {@code assets/ftbquests/sidebar_buttons/quests.json} (FTB Quests 2101.1.36). */
    public static final ResourceLocation QUESTS = ResourceLocation.fromNamespaceAndPath("ftbquests", "quests");
    private static final boolean LOADED = ModList.get().isLoaded("ftblibrary") && ModList.get().isLoaded("ftbquests");

    /** The quest book can be opened: the button is registered and its conditions hold, the server has FTB Quests. */
    public static boolean available() { return LOADED && Linked.available(); }

    public static void open() { if (LOADED) Linked.open(); }

    private static final class Linked {
        static boolean available() {
            return SidebarButtonManager.INSTANCE.getButton(QUESTS).map(RegisteredSidebarButton::canSee).orElse(false);
        }

        static void open() {
            SidebarButtonManager.INSTANCE.getButton(QUESTS).ifPresent(button -> button.clickButton(false));
        }
    }
    private QuestsBridge() {}
}
