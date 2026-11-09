package team.civitatum.charactercreation.power;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** What a power remembers about a player while the player is online. Gone on logout and death. */
public final class PlayerPowers {
    /** A power held or focused for a while ("10b", rule 5): «Транс», «Выдох», «Тропа». */
    public enum ChannelKind { TRANCE, EXHALE, PATH }

    /** A running channel; {@code until} is overworld game time, {@code at} where it began. */
    public record Channel(ChannelKind kind, long started, long until, Vec3 at) {}

    public long lastRequest = -1000;
    public Channel channel;
    /** When the Ancestor last struck the undead: until a minute passes, they may strike back first. */
    public long lastUndeadHit = -100000;
    /** The animal the Beast rides with the bonus on it, to take the bonus off after the ride. */
    public UUID mount;

    private static final Map<UUID, PlayerPowers> ALL = new HashMap<>();

    public static PlayerPowers of(Player p) { return ALL.computeIfAbsent(p.getUUID(), ignored -> new PlayerPowers()); }

    public static PlayerPowers peek(Player p) { return ALL.get(p.getUUID()); }

    public static void forget(Player p) { ALL.remove(p.getUUID()); }
}
