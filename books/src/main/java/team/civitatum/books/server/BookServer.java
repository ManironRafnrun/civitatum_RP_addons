package team.civitatum.books.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import team.civitatum.books.CivitatumBooks;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.document.PngImage;
import team.civitatum.books.network.BookNetwork;
import team.civitatum.books.network.UploadBuffer;
import team.civitatum.books.network.UploadKind;

/** Sessions and inventory mutations run on the logical server thread. Only PNG decoding uses a worker. */
public final class BookServer {
    private static final Map<UUID, BookSession> SESSIONS = new HashMap<>();
    private static final ThreadPoolExecutor IMAGE_WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), task -> {
                Thread thread = new Thread(task, "Civitatum book images");
                thread.setDaemon(true);
                return thread;
            });
    private static MinecraftServer currentServer;
    private static BookStore store;

    @SubscribeEvent
    public static void useBook(PlayerInteractEvent.RightClickItem event) {
        if (!BookItems.supported(event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            int slot = event.getHand() == InteractionHand.OFF_HAND ? 40 : player.getInventory().selected;
            open(player, slot, null);
        }
    }

    @SubscribeEvent
    public static void useLectern(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isShiftKeyDown()) return;
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof LecternBlockEntity lectern
                && BookItems.supported(lectern.getBook())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (event.getEntity() instanceof ServerPlayer player) open(player, -1, event.getPos());
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        currentServer = null;
        store = null;
    }

    private static BookStore storage(MinecraftServer server) throws IOException {
        if (server != currentServer) {
            store = new BookStore(server.getWorldPath(LevelResource.ROOT).resolve("data/civitatum_books"));
            currentServer = server;
            SESSIONS.clear();
        }
        return store;
    }

    public static void open(ServerPlayer player, int slot, BlockPos lecternPosition) {
        open(player, slot, lecternPosition, false);
    }

    public static void open(ServerPlayer player, int slot, BlockPos lecternPosition, boolean administrative) {
        try {
            if (administrative && !player.hasPermissions(2)) throw new IllegalArgumentException("permission");
            BookStore storage = storage(player.server);
            BookSession previous = SESSIONS.get(player.getUUID());
            if (previous != null && previous.processingImage) return;
            if (lecternPosition != null && (!player.level().hasChunkAt(lecternPosition)
                    || player.distanceToSqr(lecternPosition.getCenter()) > 64)) return;
            ItemStack stack = stack(player, slot, lecternPosition);
            if (!BookItems.supported(stack)) return;
            UUID id = identify(player, stack, storage, lecternPosition);
            BookDocument document = storage.read(id);
            BookSession session = new BookSession(id, slot, lecternPosition == null ? null : lecternPosition.immutable(),
                    player.level().dimension(), document, administrative);
            SESSIONS.put(player.getUUID(), session);
            PacketDistributor.sendToPlayer(player, new BookNetwork.OpenBook(session.token, id, document.bytes(),
                    administrative || (!document.signed && stack.is(Items.WRITABLE_BOOK)), lecternPosition != null, administrative));
        } catch (Exception exception) {
            CivitatumBooks.LOG.warn("Cannot open book for {}", player.getGameProfile().getName(), exception);
            player.displayClientMessage(Component.translatable("books.error.open"), false);
        }
    }

    private static UUID identify(ServerPlayer player, ItemStack stack, BookStore storage, BlockPos position) throws IOException {
        UUID id = BookItems.id(stack);
        if (id == null) {
            id = UUID.randomUUID();
            storage.write(id, BookItems.importVanilla(stack, player));
            BookItems.identify(stack, id);
            markChanged(player, position);
        }
        return id;
    }

    public static void changeAuthor(ServerPlayer player, String author) {
        try {
            if (!player.hasPermissions(2)) throw new IllegalArgumentException("permission");
            String name = BookSigning.clean(author);
            if (name.isEmpty()) throw new IllegalArgumentException("author");
            BookSession previous = SESSIONS.get(player.getUUID());
            if (previous != null && previous.processingImage) throw new IllegalArgumentException("session");
            ItemStack held = player.getMainHandItem();
            if (!BookItems.supported(held)) throw new IllegalArgumentException("session");
            BookStore storage = storage(player.server);
            UUID id = identify(player, held, storage, null);
            BookDocument document = storage.read(id);
            document.author = name;
            document.revision++;
            storage.write(id, document);
            if (document.signed) player.getInventory().setItem(player.getInventory().selected, BookItems.sign(held, id, document));
            markChanged(player, null);
            open(player, player.getInventory().selected, null, true);
        } catch (Exception exception) {
            player.displayClientMessage(Component.translatable("books.error." + reason(exception)), false);
        }
    }

    private static boolean canEdit(ServerPlayer player, BookSession session) {
        if (session.administrative) return player.hasPermissions(2);
        return !session.document.signed && stack(player, session.slot, session.lectern).is(Items.WRITABLE_BOOK);
    }

    private static ItemStack stack(ServerPlayer player, int slot, BlockPos position) {
        if (position == null) return player.getInventory().getItem(slot);
        return player.level().getBlockEntity(position) instanceof LecternBlockEntity lectern
                ? lectern.getBook() : ItemStack.EMPTY;
    }

    private static boolean valid(ServerPlayer player, BookSession session) {
        if (!player.isAlive() || player.isRemoved() || !player.level().dimension().equals(session.dimension)) return false;
        if (session.lectern != null) {
            if (!player.level().hasChunkAt(session.lectern) || player.distanceToSqr(session.lectern.getCenter()) > 64) return false;
        } else if (session.slot != 40 && player.getInventory().selected != session.slot) {
            return false;
        }
        ItemStack held = stack(player, session.slot, session.lectern);
        return BookItems.supported(held) && session.book.equals(BookItems.id(held));
    }

    public static void upload(ServerPlayer player, BookNetwork.Upload packet) {
        BookSession session = SESSIONS.get(player.getUUID());
        if (session == null || !session.token.equals(packet.session())) return;
        BookNetwork.Result result = BookNetwork.Result.valueOf(packet.kind().name());
        try {
            if (!valid(player, session) || !canEdit(player, session) || session.processingImage) {
                throw new IllegalArgumentException("session");
            }
            long now = System.currentTimeMillis();
            if (!session.receive(packet.data().length, now)) throw new IllegalArgumentException("rate");
            if (session.upload == null || session.upload.expired(now)) {
                session.upload = new UploadBuffer(packet.transfer(), packet.kind(), packet.total(), now);
            }
            session.upload.append(packet.transfer(), packet.kind(), packet.total(), packet.offset(), packet.data(), now);
            if (!session.upload.complete()) return;
            byte[] data = session.upload.bytes();
            session.upload = null;
            if (packet.kind() == UploadKind.IMAGE) processImage(player, session, data);
            else save(player, session, data, packet.kind() == UploadKind.SIGN);
        } catch (Exception exception) {
            session.upload = null;
            reply(player, session, result, false, reason(exception));
        }
    }

    private static void processImage(ServerPlayer player, BookSession session, byte[] bytes) {
        if (session.uploadedImages.size() >= BookDocument.MAX_IMAGES * 2) throw new IllegalArgumentException("images");
        session.processingImage = true;
        BookStore destination = store;
        try {
            IMAGE_WORKER.execute(() -> {
                byte[] image;
                try {
                    image = PngImage.normalize(bytes);
                } catch (Exception exception) {
                    player.server.execute(() -> {
                        session.processingImage = false;
                        reply(player, session, BookNetwork.Result.IMAGE, false, reason(exception));
                    });
                    return;
                }
                player.server.execute(() -> {
                    session.processingImage = false;
                    if (SESSIONS.get(player.getUUID()) != session || !valid(player, session)) return;
                    try {
                        String hash = destination.putImage(image);
                        session.uploadedImages.add(hash);
                        PacketDistributor.sendToPlayer(player, new BookNetwork.Image(session.token, hash, image));
                        reply(player, session, BookNetwork.Result.IMAGE, true, hash);
                    } catch (Exception exception) {
                        reply(player, session, BookNetwork.Result.IMAGE, false, reason(exception));
                    }
                });
            });
        } catch (RuntimeException exception) {
            session.processingImage = false;
            throw exception;
        }
    }

    private static void save(ServerPlayer player, BookSession session, byte[] bytes, boolean sign) throws IOException {
        BookDocument next = BookDocument.parse(bytes);
        BookDocument existing = store.read(session.book);
        if ((existing.signed && !session.administrative) || existing.revision != next.revision || session.document.revision != next.revision) {
            throw new IllegalArgumentException("conflict");
        }
        for (String hash : next.images()) {
            if ((!session.document.images().contains(hash) && !session.uploadedImages.contains(hash)) || !store.hasImage(hash)) {
                throw new IllegalArgumentException("image");
            }
        }
        next.title = next.title.strip();
        if ((sign || existing.signed) && next.title.isEmpty()) throw new IllegalArgumentException("title");
        next.signed = existing.signed || sign;
        next.author = BookSigning.author(existing.author, next.author, player.getGameProfile().getName(),
                session.administrative && player.hasPermissions(2), next.signed);
        next.revision = existing.revision + 1;
        store.write(session.book, next);
        session.document = next;
        if (next.signed) {
            ItemStack signed = BookItems.sign(stack(player, session.slot, session.lectern), session.book, next);
            if (session.lectern == null) player.getInventory().setItem(session.slot, signed);
            else ((LecternBlockEntity) player.level().getBlockEntity(session.lectern)).setBook(signed);
        }
        markChanged(player, session.lectern);
        reply(player, session, sign ? BookNetwork.Result.SIGN : BookNetwork.Result.SAVE, true, "saved");
    }

    public static void request(ServerPlayer player, BookNetwork.Request packet) {
        BookSession session = SESSIONS.get(player.getUUID());
        if (session == null || !session.token.equals(packet.session())) return;
        if (packet.command() == BookNetwork.Command.CLOSE) {
            if (!session.processingImage) SESSIONS.remove(player.getUUID());
            return;
        }
        try {
            if (!valid(player, session)) throw new IllegalArgumentException("session");
            if (packet.command() == BookNetwork.Command.IMAGE) {
                String hash = packet.value();
                if (!session.document.images().contains(hash) && !session.uploadedImages.contains(hash)) return;
                if (!session.requestedImages.add(hash)) return;
                PacketDistributor.sendToPlayer(player, new BookNetwork.Image(session.token, hash, store.image(hash)));
            } else {
                takeFromLectern(player, session);
            }
        } catch (Exception exception) {
            reply(player, session, BookNetwork.Result.TAKE_FROM_LECTERN, false, reason(exception));
        }
    }

    private static void takeFromLectern(ServerPlayer player, BookSession session) {
        if (session.lectern == null || session.processingImage || !player.mayBuild()
                || player.server.isUnderSpawnProtection(player.serverLevel(), session.lectern, player)) {
            throw new IllegalArgumentException("session");
        }
        LecternBlockEntity lectern = (LecternBlockEntity) player.level().getBlockEntity(session.lectern);
        ItemStack remaining = lectern.getBook().copy();
        player.getInventory().add(remaining);
        // Inventory.add may insert only part of a modified stack; retain the exact remainder.
        if (!remaining.isEmpty()) {
            lectern.setBook(remaining);
            markChanged(player, session.lectern);
            throw new IllegalArgumentException("inventory_full");
        }
        lectern.setBook(ItemStack.EMPTY);
        LecternBlock.resetBookState(player, player.level(), session.lectern, player.level().getBlockState(session.lectern), false);
        markChanged(player, session.lectern);
        SESSIONS.remove(player.getUUID());
        reply(player, session, BookNetwork.Result.TAKE_FROM_LECTERN, true, "taken");
    }

    private static void markChanged(ServerPlayer player, BlockPos position) {
        player.getInventory().setChanged();
        if (position != null && player.level().getBlockEntity(position) instanceof LecternBlockEntity lectern) lectern.setChanged();
    }

    private static void reply(ServerPlayer player, BookSession session, BookNetwork.Result result, boolean success, String message) {
        PacketDistributor.sendToPlayer(player, new BookNetwork.Reply(session.token, result, session.document.revision, success, message, session.document.author));
    }

    private static String reason(Exception exception) {
        String message = exception.getMessage();
        if (message != null && message.matches("[a-z_]{1,64}")) return message;
        CivitatumBooks.LOG.warn("Book operation failed", exception);
        return "failed";
    }

    private BookServer() {}
}
