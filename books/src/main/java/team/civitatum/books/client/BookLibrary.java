package team.civitatum.books.client;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import team.civitatum.books.CivitatumBooks;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.network.BookNetwork;

/** Network reception stays on the client thread; all library disk work is serialized off that thread. */
final class BookLibrary {
    static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Civitatum Books library");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<UUID, Capture> sessions = new LinkedHashMap<>();

    static BookShelf shelf() {
        Minecraft client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        String world;
        if (server != null) {
            Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            world = "local:" + root;
        } else if (client.getCurrentServer() != null) {
            world = "server:" + client.getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT);
        } else {
            throw new IllegalStateException("No active world");
        }
        UUID player = client.player == null ? client.getGameProfile().getId() : client.player.getUUID();
        return new BookShelf(client.gameDirectory.toPath(), player, world);
    }

    static Capture opened(BookNetwork.OpenBook packet) {
        Capture capture = new Capture(shelf(), packet.book());
        sessions.put(packet.session(), capture);
        while (sessions.size() > 8) sessions.remove(sessions.keySet().iterator().next());
        capture.remember(BookDocument.parse(packet.document()));
        return capture;
    }

    static void image(BookNetwork.Image packet) {
        Capture capture = sessions.get(packet.session());
        if (capture == null) return;
        byte[] bytes = packet.data().clone();
        run(() -> capture.shelf.image(packet.hash(), bytes));
    }

    static void logout() { sessions.clear(); }

    private static void run(DiskTask task) {
        CompletableFuture.runAsync(() -> {
            try { task.run(); }
            catch (Exception exception) { throw new java.util.concurrent.CompletionException(exception); }
        }, IO).exceptionally(failure -> {
            CivitatumBooks.LOG.warn("Cannot retain a local book copy", failure);
            Minecraft.getInstance().execute(() -> {
                var player = Minecraft.getInstance().player;
                if (player != null) player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("books.library_save_failed"), false);
            });
            return null;
        });
    }

    @FunctionalInterface private interface DiskTask { void run() throws Exception; }

    static final class Capture {
        private final BookShelf shelf;
        private final UUID book;

        private Capture(BookShelf shelf, UUID book) { this.shelf = shelf; this.book = book; }

        void remember(BookDocument document) {
            BookDocument snapshot = document.copy();
            run(() -> shelf.remember(book, snapshot));
        }
    }

    private BookLibrary() {}
}
