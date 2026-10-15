package ru.teacraft.civitatum;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RaceNetwork {
    public record Clientbound(int kind, UUID player, long token, String data) implements CustomPacketPayload {
        public static final Type<Clientbound> TYPE = new Type<>(Civitatum.res("state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Clientbound> CODEC = StreamCodec.of(
            (b,p) -> { b.writeVarInt(p.kind); b.writeUUID(p.player); b.writeLong(p.token); b.writeUtf(p.data, 16384); },
            b -> new Clientbound(b.readVarInt(), b.readUUID(), b.readLong(), b.readUtf(16384)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Serverbound(int action, long token, String data) implements CustomPacketPayload {
        public static final Type<Serverbound> TYPE = new Type<>(Civitatum.res("action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Serverbound> CODEC = StreamCodec.of(
            (b,p) -> { b.writeVarInt(p.action); b.writeLong(p.token); b.writeUtf(p.data, 8192); },
            b -> new Serverbound(b.readVarInt(), b.readLong(), b.readUtf(8192)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** Installed exclusively on the client, without loading Minecraft client classes on servers. */
    public static Consumer<Clientbound> CLIENT_RECEIVER = ignored -> {};
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("3");
        r.playToClient(Clientbound.TYPE, Clientbound.CODEC, (p,c) -> c.enqueueWork(() -> CLIENT_RECEIVER.accept(p)));
        r.playToServer(Serverbound.TYPE, Serverbound.CODEC, (p,c) -> c.enqueueWork(() -> {
            if (!(c.player() instanceof ServerPlayer player)) return;
            switch (p.action) {
                case 0 -> RaceService.save(player, p.token, p.data);
                case 1 -> RaceAbilities.activate(player);
                case 2 -> RaceService.openWithShard(player);
                case 3 -> RaceService.cancel(player, p.token);
                default -> {}
            }
        }));
    }
    public static void sync(ServerPlayer player) {
        var packet=new Clientbound(0,player.getUUID(),0,player.getData(Civitatum.STATE).json());
        for(ServerPlayer observer:player.server.getPlayerList().getPlayers()) send(observer,packet);
    }
    public static void send(ServerPlayer player,Clientbound packet) {
        if(player.connection!=null && player.connection.hasChannel(Clientbound.TYPE)) PacketDistributor.sendToPlayer(player,packet);
    }
}
