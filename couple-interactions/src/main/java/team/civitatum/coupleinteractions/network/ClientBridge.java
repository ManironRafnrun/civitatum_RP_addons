package team.civitatum.coupleinteractions.network;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.entity.player.Player;
import team.civitatum.coupleinteractions.core.PoseSettings;

/** No client classes in common handlers: dedicated servers can load the entire protocol. */
public final class ClientBridge {
    public static Consumer<CoupleNetwork.Invitation> invitation = p -> {};
    public static Consumer<CoupleNetwork.ClearInvitation> clearInvitation = p -> {};
    public static Consumer<CoupleNetwork.CarryState> carryState = p -> {};
    public static Consumer<CoupleNetwork.KissEffect> kissEffect = p -> {};
    public static Function<UUID, UUID> carrierOf = id -> null;
    public static Function<UUID, UUID> passengerOf = id -> null;
    public static Consumer<CoupleNetwork.SocialState> socialState=p->{};
    public static Function<UUID,CoupleNetwork.SocialState> socialOf=id->null;
    public static Function<UUID,PoseSettings> poseOf=id->PoseSettings.DEFAULT;
    public static Function<Player,Player> previewCarrier=p->null,previewPassenger=p->null;
    public static java.util.function.Predicate<Player> ignorePose=p->false;
    private ClientBridge() {}
}
