package team.civitatum.starclock.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import team.civitatum.starclock.CivitatumStarclock;

public record CancelRitualPayload(UUID session, String reason) implements CustomPacketPayload {
    public static final Type<CancelRitualPayload> TYPE = new Type<>(CivitatumStarclock.resource("cancel_ritual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CancelRitualPayload> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUUID(value.session());
                buffer.writeUtf(value.reason(), 64);
            }, buffer -> new CancelRitualPayload(buffer.readUUID(), buffer.readUtf(64)));

    @Override
    public Type<CancelRitualPayload> type() {
        return TYPE;
    }
}
