package team.civitatum.starclock.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import team.civitatum.starclock.CivitatumStarclock;

public record TowerKeyStatePayload(int charges) implements CustomPacketPayload {
    public static final Type<TowerKeyStatePayload> TYPE = new Type<>(CivitatumStarclock.resource("tower_key_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TowerKeyStatePayload> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value.charges()), buffer -> new TowerKeyStatePayload(buffer.readVarInt()));

    public TowerKeyStatePayload {
        if (charges < 0 || charges > 5) throw new IllegalArgumentException("Неверный запас Ключа Башни");
    }

    @Override
    public Type<TowerKeyStatePayload> type() {
        return TYPE;
    }
}
