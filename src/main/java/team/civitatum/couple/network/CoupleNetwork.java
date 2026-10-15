package team.civitatum.couple.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import team.civitatum.couple.CoupleInteractions;
import team.civitatum.couple.server.CoupleServer;

public final class CoupleNetwork {
    public record Action(UUID target, int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(CoupleInteractions.id("action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (b,p) -> { b.writeUUID(p.target); b.writeVarInt(p.action); }, b -> new Action(b.readUUID(), b.readVarInt()));
        public Type<Action> type() { return TYPE; }
    }
    public record Decision(UUID request, boolean accept) implements CustomPacketPayload {
        public static final Type<Decision> TYPE = new Type<>(CoupleInteractions.id("decision"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Decision> CODEC = StreamCodec.of(
                (b,p) -> { b.writeUUID(p.request); b.writeBoolean(p.accept); }, b -> new Decision(b.readUUID(), b.readBoolean()));
        public Type<Decision> type() { return TYPE; }
    }
    public record Preferences(String data) implements CustomPacketPayload {
        public Preferences(boolean carry,boolean kiss){this(team.civitatum.couple.core.PlayerPreferences.legacy(carry,kiss).json());}
        public static final Type<Preferences> TYPE = new Type<>(CoupleInteractions.id("preferences"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Preferences> CODEC = StreamCodec.of(
                (b,p) -> b.writeUtf(p.data,16384), b -> new Preferences(b.readUtf(16384)));
        public Type<Preferences> type() { return TYPE; }
    }
    public record Invitation(UUID request, UUID sender, String name, int action, int ticks) implements CustomPacketPayload {
        public static final Type<Invitation> TYPE = new Type<>(CoupleInteractions.id("invitation"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Invitation> CODEC = StreamCodec.of(
                (b,p) -> { b.writeUUID(p.request); b.writeUUID(p.sender); b.writeUtf(p.name, 64); b.writeVarInt(p.action); b.writeVarInt(p.ticks); },
                b -> new Invitation(b.readUUID(), b.readUUID(), b.readUtf(64), b.readVarInt(), b.readVarInt()));
        public Type<Invitation> type() { return TYPE; }
    }
    public record ClearInvitation(UUID request) implements CustomPacketPayload {
        public static final Type<ClearInvitation> TYPE = new Type<>(CoupleInteractions.id("clear_invitation"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClearInvitation> CODEC = StreamCodec.of(
                (b,p) -> b.writeUUID(p.request), b -> new ClearInvitation(b.readUUID()));
        public Type<ClearInvitation> type() { return TYPE; }
    }
    public record CarryState(UUID carrier, UUID passenger, boolean active, String pose) implements CustomPacketPayload {
        public CarryState(UUID carrier,UUID passenger,boolean active){this(carrier,passenger,active,team.civitatum.couple.core.PoseSettings.DEFAULT.json());}
        public static final Type<CarryState> TYPE = new Type<>(CoupleInteractions.id("carry_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CarryState> CODEC = StreamCodec.of(
                (b,p) -> { b.writeUUID(p.carrier); b.writeUUID(p.passenger); b.writeBoolean(p.active); b.writeUtf(p.pose,1024); },
                b -> new CarryState(b.readUUID(), b.readUUID(), b.readBoolean(),b.readUtf(1024)));
        public Type<CarryState> type() { return TYPE; }
    }
    public record KissEffect(UUID first, UUID second) implements CustomPacketPayload {
        public static final Type<KissEffect> TYPE = new Type<>(CoupleInteractions.id("kiss_effect"));
        public static final StreamCodec<RegistryFriendlyByteBuf, KissEffect> CODEC = StreamCodec.of(
                (b,p) -> { b.writeUUID(p.first); b.writeUUID(p.second); }, b -> new KissEffect(b.readUUID(), b.readUUID()));
        public Type<KissEffect> type() { return TYPE; }
    }
    public record SocialState(UUID first,UUID second,int action,boolean active,int elapsed,int duration,float yaw,boolean mounted) implements CustomPacketPayload {
        public static final Type<SocialState> TYPE=new Type<>(CoupleInteractions.id("social_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,SocialState> CODEC=StreamCodec.of(
            (b,p)->{b.writeUUID(p.first);b.writeUUID(p.second);b.writeVarInt(p.action);b.writeBoolean(p.active);b.writeVarInt(p.elapsed);b.writeVarInt(p.duration);b.writeFloat(p.yaw);b.writeBoolean(p.mounted);},
            b->new SocialState(b.readUUID(),b.readUUID(),b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readFloat(),b.readBoolean()));
        public Type<SocialState> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("2");
        r.playToServer(Action.TYPE, Action.CODEC, (p,c) -> { if (c.player() instanceof ServerPlayer s) CoupleServer.request(s, p); });
        r.playToServer(Decision.TYPE, Decision.CODEC, (p,c) -> { if (c.player() instanceof ServerPlayer s) CoupleServer.decide(s, p); });
        r.playToServer(Preferences.TYPE, Preferences.CODEC, (p,c) -> { if (c.player() instanceof ServerPlayer s) CoupleServer.preferences(s, p); });
        r.playToClient(Invitation.TYPE, Invitation.CODEC, (p,c) -> ClientBridge.invitation.accept(p));
        r.playToClient(ClearInvitation.TYPE, ClearInvitation.CODEC, (p,c) -> ClientBridge.clearInvitation.accept(p));
        r.playToClient(CarryState.TYPE, CarryState.CODEC, (p,c) -> ClientBridge.carryState.accept(p));
        r.playToClient(KissEffect.TYPE, KissEffect.CODEC, (p,c) -> ClientBridge.kissEffect.accept(p));
        r.playToClient(SocialState.TYPE,SocialState.CODEC,(p,c)->ClientBridge.socialState.accept(p));
    }
    private CoupleNetwork() {}
}
