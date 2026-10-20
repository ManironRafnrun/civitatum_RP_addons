package team.civitatum.starclock.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import team.civitatum.starclock.CivitatumStarclock;
import team.civitatum.starclock.item.ToolKind;
import team.civitatum.starclock.item.ToolTier;

public record RitualStatePayload(UUID session, InteractionHand hand, ToolKind kind,
                                 ToolTier tier, boolean drawing, long startedAt) implements CustomPacketPayload {
    public static final Type<RitualStatePayload> TYPE = new Type<>(CivitatumStarclock.resource("ritual_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RitualStatePayload> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUUID(value.session());
                buffer.writeEnum(value.hand());
                buffer.writeEnum(value.kind());
                buffer.writeEnum(value.tier());
                buffer.writeBoolean(value.drawing());
                buffer.writeLong(value.startedAt());
            },
            buffer -> new RitualStatePayload(buffer.readUUID(), buffer.readEnum(InteractionHand.class),
                    buffer.readEnum(ToolKind.class), buffer.readEnum(ToolTier.class),
                    buffer.readBoolean(), buffer.readLong()));

    @Override
    public Type<RitualStatePayload> type() {
        return TYPE;
    }
}
