package team.civitatum.starclock.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import team.civitatum.starclock.CivitatumStarclock;
import team.civitatum.starclock.item.ToolTier;
import team.civitatum.starclock.prediction.Constellation;
import team.civitatum.starclock.prediction.Orientation;

public record ShowCardPayload(UUID session, Constellation sign, Orientation orientation, ToolTier tier, String category, String target)
        implements CustomPacketPayload {
    public static final Type<ShowCardPayload> TYPE = new Type<>(CivitatumStarclock.resource("show_card"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShowCardPayload> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUUID(value.session());
                buffer.writeEnum(value.sign());
                buffer.writeEnum(value.orientation());
                buffer.writeEnum(value.tier());
                buffer.writeUtf(value.category(), 32);
                buffer.writeUtf(value.target(), 32);
            }, buffer -> new ShowCardPayload(buffer.readUUID(), buffer.readEnum(Constellation.class),
                    buffer.readEnum(Orientation.class), buffer.readEnum(ToolTier.class), buffer.readUtf(32), buffer.readUtf(32)));

    public ShowCardPayload {
        if ((sign == Constellation.SERPENT) != (orientation == Orientation.NONE)) {
            throw new IllegalArgumentException("Несовместимые знак и положение");
        }
    }

    @Override
    public Type<ShowCardPayload> type() {
        return TYPE;
    }
}
