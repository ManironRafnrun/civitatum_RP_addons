package team.civitatum.starclock.client;

import net.neoforged.neoforge.network.PacketDistributor;
import team.civitatum.starclock.network.CastTowerKeyPayload;

public final class TowerKeyClient {
    private static int charges;

    public static int charges() {
        return charges;
    }

    public static void accept(int remaining) {
        charges = remaining;
    }

    public static void cast() {
        if (charges > 0 && !RitualClientState.isActive()) {
            PacketDistributor.sendToServer(new CastTowerKeyPayload());
        }
    }

    private TowerKeyClient() {}
}
