package team.civitatum.inventory.client;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import team.civitatum.charactercreation.BirthsignCatalog;
import team.civitatum.charactercreation.RaceCatalog;
import team.civitatum.charactercreation.RaceNetwork;
import team.civitatum.charactercreation.client.ClientCharacters;

/**
 * Optional adapter to Civitatum Character Creation. The mod is a compile-only dependency and is not bundled;
 * its classes are touched only in {@link Linked}, which loads after the {@code civitatum} mod is confirmed present.
 */
public final class RacesBridge {
    public record Details(String name, String race, String sign, String positive, String negative, String ability,
            int food, int cooldown, long remainingTicks, int curseDeaths) {}
    private static final boolean LOADED = ModList.get().isLoaded("civitatum");

    public static Details read(Player player) { return LOADED ? Linked.read(player) : null; }
    public static void activate() { if (LOADED) Linked.activate(); }

    private static final class Linked {
        static Details read(Player player) {
            var state = ClientCharacters.state(player);
            var character = state.character();
            if (!character.selected) return null;
            var race = RaceCatalog.get(character.race);
            if (race == null) return null;
            var sign = BirthsignCatalog.get(character.birthsign);
            return new Details(character.name, race.name, sign == null || sign.id().equals("none") ? "" : sign.name(),
                race.positive, race.negative, race.active, race.food, race.cooldown,
                Math.max(0, state.cooldownUntil() - player.level().getGameTime()), state.curseDeaths());
        }
        static void activate() { PacketDistributor.sendToServer(new RaceNetwork.Serverbound(1, 0, "")); }
    }
    private RacesBridge() {}
}
