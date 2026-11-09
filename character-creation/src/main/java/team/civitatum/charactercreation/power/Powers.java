package team.civitatum.charactercreation.power;

import net.minecraft.server.level.ServerPlayer;

/** What a power keeps for a player and must drop: a new character, death, leaving the game. */
public final class Powers {
    /** Death and a new character end what runs; the clocks stay ("смерть сбрасывает только силу"). */
    public static void clear(ServerPlayer p) {
        Channels.stop(p);
        PassiveTicks.dismount(p);
        PlayerPowers.forget(p);
    }

    public static void logout(ServerPlayer p) {
        clear(p);
        SignPowers.logout(p);
        Zones.logout(p);
    }

    private Powers() {}
}
