package team.civitatum.starclock.network;

import java.util.UUID;
import team.civitatum.starclock.prediction.Constellation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import team.civitatum.starclock.CivitatumStarclock;
import team.civitatum.starclock.item.ToolKind;
import team.civitatum.starclock.item.ToolTier;

public record RitualStatePayload(UUID session, InteractionHand hand, ToolKind kind,
                                 ToolTier tier, boolean drawing, long startedAt, Constellation figure) implements CustomPacketPayload {
    public RitualStatePayload {
        if (drawing != (figure != null)) throw new IllegalArgumentException("Фигура допустима только при вытягивании");
    }

    public static final Type<RitualStatePayload> TYPE = new Type<>(CivitatumStarclock.resource("ritual_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RitualStatePayload> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUUID(value.session());
                buffer.writeEnum(value.hand());
                buffer.writeEnum(value.kind());
                buffer.writeEnum(value.tier());
                buffer.writeBoolean(value.drawing());
                buffer.writeLong(value.startedAt());
                if (value.drawing()) buffer.writeEnum(value.figure());
            },
            buffer -> {
                var session = buffer.readUUID();
                var hand = buffer.readEnum(InteractionHand.class);
                var kind = buffer.readEnum(ToolKind.class);
                var tier = buffer.readEnum(ToolTier.class);
                boolean drawing = buffer.readBoolean();
                long started = buffer.readLong();
                return new RitualStatePayload(session, hand, kind, tier, drawing, started,
                        drawing ? buffer.readEnum(Constellation.class) : null);
            });

    @Override
    public Type<RitualStatePayload> type() {
        return TYPE;
    }
}
