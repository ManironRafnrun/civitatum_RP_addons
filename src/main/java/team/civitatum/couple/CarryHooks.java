package team.civitatum.couple;

import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import team.civitatum.couple.network.ClientBridge;
import team.civitatum.couple.server.CoupleServer;
import team.civitatum.couple.core.PoseSettings;

public final class CarryHooks {
    public static UUID carrierId(Player player) {
        if(ClientBridge.ignorePose.test(player))return null;
        Player preview=ClientBridge.previewCarrier.apply(player);if(preview!=null)return preview.getUUID();
        return player.level().isClientSide ? ClientBridge.carrierOf.apply(player.getUUID()) : CoupleServer.carrierOf(player.getUUID());
    }
    public static UUID passengerId(Player player) {
        if(ClientBridge.ignorePose.test(player))return null;
        Player preview=ClientBridge.previewPassenger.apply(player);if(preview!=null)return preview.getUUID();
        return player.level().isClientSide ? ClientBridge.passengerOf.apply(player.getUUID()) : CoupleServer.passengerOf(player.getUUID());
    }
    public static boolean carried(Player player) { return carrierId(player) != null; }
    public static boolean carrying(Player player) { return passengerId(player) != null; }
    public static boolean occupied(Player player) { return carried(player) || carrying(player) || SocialHooks.active(player); }
    public static PoseSettings tuning(Player player){return player.level().isClientSide?ClientBridge.poseOf.apply(player.getUUID()):CoupleServer.preferencesOf(player).pose;}
    public static boolean pair(Player carrier, Player passenger) { return carrier.getUUID().equals(carrierId(passenger)); }
    public static Player carrier(Player player) {
        Player preview=ClientBridge.previewCarrier.apply(player);if(preview!=null)return preview;
        UUID id = carrierId(player);
        return id == null ? null : player.level().getPlayerByUUID(id);
    }
    private CarryHooks() {}
}
