package team.civitatum.books.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import team.civitatum.books.CivitatumBooks;
import team.civitatum.books.network.BookNetwork;

@Mod(value = CivitatumBooks.ID, dist = Dist.CLIENT)
public final class BooksClient {
    private BookScreen activeBook;

    public BooksClient() {
        new InventoryLibraryButton().register();
        BookNetwork.onOpen = packet -> {
            if (activeBook != null) activeBook.releaseImages();
            activeBook = new BookScreen(packet);
            Minecraft.getInstance().setScreen(activeBook);
        };
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            BookScreen visible = currentBook();
            if (visible != null && visible != activeBook) visible.releaseImages();
            if (activeBook != null) activeBook.releaseImages();
            activeBook = null;
            BookLibrary.logout();
        });
        BookNetwork.onReply = packet -> {
            BookScreen screen = currentBook();
            if (screen != null) screen.receive(packet);
        };
        BookNetwork.onImage = packet -> {
            BookLibrary.image(packet);
            BookScreen screen = currentBook();
            if (screen != null) screen.receive(packet);
        };
    }

    private static BookScreen currentBook() {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof BookScreen book) return book;
        if (current instanceof BookChildScreen dialog) return dialog.book();
        return null;
    }
}
