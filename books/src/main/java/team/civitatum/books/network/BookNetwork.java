package team.civitatum.books.network;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import team.civitatum.books.CivitatumBooks;
import team.civitatum.books.document.BookDocument;
import team.civitatum.books.document.PngImage;
import team.civitatum.books.server.BookServer;

public final class BookNetwork {
    public enum Command { IMAGE, CLOSE, TAKE_FROM_LECTERN }
    public enum Result { SAVE, IMAGE, SIGN, TAKE_FROM_LECTERN }

    // Installed by the physical client. Dedicated servers never load screen or texture classes.
    public static Consumer<OpenBook> onOpen = packet -> {};
    public static Consumer<Reply> onReply = packet -> {};
    public static Consumer<Image> onImage = packet -> {};

    public record OpenBook(UUID session, UUID book, byte[] document, boolean editable, boolean lectern, boolean administrative)
            implements CustomPacketPayload {
        public static final Type<OpenBook> TYPE = new Type<>(CivitatumBooks.resource("open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenBook> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    buffer.writeUUID(packet.session);
                    buffer.writeUUID(packet.book);
                    buffer.writeByteArray(packet.document);
                    buffer.writeBoolean(packet.editable);
                    buffer.writeBoolean(packet.lectern);
                    buffer.writeBoolean(packet.administrative);
                }, buffer -> new OpenBook(buffer.readUUID(), buffer.readUUID(),
                        buffer.readByteArray(BookDocument.MAX_BYTES), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()));
        public Type<OpenBook> type() { return TYPE; }
    }

    public record Upload(UUID session, UUID transfer, UploadKind kind, int total, int offset, byte[] data)
            implements CustomPacketPayload {
        public static final Type<Upload> TYPE = new Type<>(CivitatumBooks.resource("upload"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Upload> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    buffer.writeUUID(packet.session);
                    buffer.writeUUID(packet.transfer);
                    buffer.writeEnum(packet.kind);
                    buffer.writeVarInt(packet.total);
                    buffer.writeVarInt(packet.offset);
                    buffer.writeByteArray(packet.data);
                }, buffer -> new Upload(buffer.readUUID(), buffer.readUUID(), buffer.readEnum(UploadKind.class),
                        buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(UploadBuffer.CHUNK_BYTES)));
        public Type<Upload> type() { return TYPE; }
    }

    public record Request(UUID session, Command command, String value) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(CivitatumBooks.resource("request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    buffer.writeUUID(packet.session);
                    buffer.writeEnum(packet.command);
                    buffer.writeUtf(packet.value, 64);
                }, buffer -> new Request(buffer.readUUID(), buffer.readEnum(Command.class), buffer.readUtf(64)));
        public Type<Request> type() { return TYPE; }
    }

    public record Reply(UUID session, Result result, int revision, boolean success, String message, String author)
            implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(CivitatumBooks.resource("reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    buffer.writeUUID(packet.session);
                    buffer.writeEnum(packet.result);
                    buffer.writeVarInt(packet.revision);
                    buffer.writeBoolean(packet.success);
                    buffer.writeUtf(packet.message, 128);
                    buffer.writeUtf(packet.author, 80);
                }, buffer -> new Reply(buffer.readUUID(), buffer.readEnum(Result.class), buffer.readVarInt(),
                        buffer.readBoolean(), buffer.readUtf(128), buffer.readUtf(80)));
        public Type<Reply> type() { return TYPE; }
    }

    public record Image(UUID session, String hash, byte[] data) implements CustomPacketPayload {
        public static final Type<Image> TYPE = new Type<>(CivitatumBooks.resource("image"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Image> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    buffer.writeUUID(packet.session);
                    buffer.writeUtf(packet.hash, 64);
                    buffer.writeByteArray(packet.data);
                }, buffer -> new Image(buffer.readUUID(), buffer.readUtf(64), buffer.readByteArray(PngImage.MAX_BYTES)));
        public Type<Image> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(OpenBook.TYPE, OpenBook.CODEC, (packet, context) -> onOpen.accept(packet));
        registrar.playToClient(Reply.TYPE, Reply.CODEC, (packet, context) -> onReply.accept(packet));
        registrar.playToClient(Image.TYPE, Image.CODEC, (packet, context) -> onImage.accept(packet));
        registrar.playToServer(Upload.TYPE, Upload.CODEC,
                (packet, context) -> BookServer.upload((ServerPlayer) context.player(), packet));
        registrar.playToServer(Request.TYPE, Request.CODEC,
                (packet, context) -> BookServer.request((ServerPlayer) context.player(), packet));
    }

    public static void upload(UUID session, UploadKind kind, byte[] data) {
        if (data.length == 0 || data.length > kind.maximumBytes()) throw new IllegalArgumentException("upload_size");
        UUID transfer = UUID.randomUUID();
        for (int offset = 0; offset < data.length; offset += UploadBuffer.CHUNK_BYTES) {
            byte[] chunk = Arrays.copyOfRange(data, offset, Math.min(offset + UploadBuffer.CHUNK_BYTES, data.length));
            PacketDistributor.sendToServer(new Upload(session, transfer, kind, data.length, offset, chunk));
        }
    }

    private BookNetwork() {}
}
