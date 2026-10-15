package team.civitatum.couple;

import net.minecraft.world.entity.player.Player;
import team.civitatum.couple.core.Interaction;
import team.civitatum.couple.network.*;
import team.civitatum.couple.server.SocialSessions;

public final class SocialHooks {
    public static CoupleNetwork.SocialState state(Player p){if(ClientBridge.ignorePose.test(p))return null;return p.level().isClientSide?ClientBridge.socialOf.apply(p.getUUID()):SocialSessions.state(p.getUUID());}
    public static boolean active(Player p){return state(p)!=null;}
    public static boolean resting(Player p){var s=state(p);return s!=null && s.action()==Interaction.REST.ordinal();}
    private SocialHooks(){}
}
