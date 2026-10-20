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
        BookNetwork.onOpen = packet -> {
            if (activeBook != null) activeBook.releaseImages();
            activeBook = new BookScreen(packet);
            Minecraft.getInstance().setScreen(activeBook);
        };
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            if (activeBook != null) activeBook.releaseImages();
            activeBook = null;
        });
        BookNetwork.onReply = packet -> {
            BookScreen screen = currentBook();
            if (screen != null) screen.receive(packet);
        };
        BookNetwork.onImage = packet -> {
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
