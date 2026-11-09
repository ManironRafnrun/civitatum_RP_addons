package team.civitatum.hud.client;

import net.minecraft.client.resources.language.I18n;
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
    /**
     * @param signPower       the name of the birthsign's power, empty when the sign has none on the key
     * @param signText        what the sign gives, for the race page
     */
    public record Details(String name, String race, String sign, String positive, String negative, String ability,
            int cooldown, long remainingTicks, String signPower, String signText, int signCooldown,
            long signRemainingTicks) {}
    private static final boolean LOADED = ModList.get().isLoaded("civitatum");

    public static Details read(Player player) { return LOADED ? Linked.read(player) : null; }
    public static void activate() { if (LOADED) Linked.activate(); }
    public static void activateSign() { if (LOADED) Linked.activateSign(); }

    private static final class Linked {
        static Details read(Player player) {
            var state = ClientCharacters.state(player);
            var character = state.character();
            if (!character.selected) return null;
            var race = RaceCatalog.get(character.race);
            if (race == null) return null;
            var sign = BirthsignCatalog.get(character.birthsign);
            boolean noSign = sign == null || sign.id().equals(BirthsignCatalog.NONE);
            long now = player.level().getGameTime();
            // Texts come from the lang files of the civitatum mod, in the client's language.
            return new Details(character.name, I18n.get(race.key("name")),
                noSign ? "" : I18n.get(sign.key("name")),
                I18n.get(race.key("positive")), I18n.get(race.key("negative")),
                race.hasAbility() ? I18n.get(race.key("ability")) : "", race.cooldown,
                Math.max(0, state.cooldownUntil() - now),
                !noSign && sign.hasPower() ? I18n.get(sign.key("power")) : "",
                noSign ? "" : I18n.get(sign.key("effect")), noSign ? 0 : sign.cooldown(),
                Math.max(0, state.signCooldownUntil() - now));
        }
        static void activate() { PacketDistributor.sendToServer(new RaceNetwork.Serverbound(1, 0, "")); }
        static void activateSign() { PacketDistributor.sendToServer(new RaceNetwork.Serverbound(6, 0, "")); }
    }
    private RacesBridge() {}
}
