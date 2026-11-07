package team.civitatum.starclock.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import team.civitatum.starclock.CivitatumStarclock;

public record CastTowerKeyPayload() implements CustomPacketPayload {
    public static final Type<CastTowerKeyPayload> TYPE = new Type<>(CivitatumStarclock.resource("cast_tower_key"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CastTowerKeyPayload> CODEC =
            StreamCodec.unit(new CastTowerKeyPayload());

    @Override
    public Type<CastTowerKeyPayload> type() {
        return TYPE;
    }
}
