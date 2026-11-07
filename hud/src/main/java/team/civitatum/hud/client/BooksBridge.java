package team.civitatum.hud.client;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import team.civitatum.books.client.BookLibraryScreen;

/**
 * Optional adapter to Civitatum Books. The mod is a compile-only dependency and is not bundled; its classes are
 * touched only in {@link Linked}, which loads after the {@code civitatum_books} mod is confirmed present.
 */
public final class BooksBridge {
    public static final boolean LOADED = ModList.get().isLoaded("civitatum_books");

    /** The catalogue of books the player has read; closing it returns to {@code parent}. */
    public static void openLibrary(Screen parent) { if (LOADED) Linked.openLibrary(parent); }

    private static final class Linked {
        static void openLibrary(Screen parent) { BookLibraryScreen.open(parent); }
    }
    private BooksBridge() {}
}
