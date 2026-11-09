package team.civitatum.charactercreation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /civitatum sign set <player> <sign>} for an administrator, the only way to the Serpent besides the lot, and
 * {@code /civitatum sign rewrite}, "Переписать небо": once per character, at night, for a level of experience and the
 * night's sleep; the sign is cleared and chosen again in the editor.
 */
public final class SignCommands {
    /** Night by the overworld's clock, as beds count it: from dusk (12 542) to dawn (23 460), rounded. */
    private static final long NIGHT_START = 13000, NIGHT_END = 23000;
    private static final long DAY = 24000;

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("sign")
            .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("sign", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(BirthsignCatalog.all().stream()
                            .filter(s -> s.kind() != BirthsignCatalog.Kind.LOT).map(BirthsignCatalog.Sign::id), b))
                        .executes(c -> set(c.getSource(), EntityArgument.getPlayer(c, "player"),
                            StringArgumentType.getString(c, "sign"))))))
            .then(Commands.literal("rewrite").executes(c -> rewrite(c.getSource().getPlayerOrException())));
    }

    private static int set(CommandSourceStack source, ServerPlayer p, String id) {
        var sign = BirthsignCatalog.get(id);
        var state = p.getData(Civitatum.STATE);
        if (sign == null || sign.kind() == BirthsignCatalog.Kind.LOT || !state.character().selected) {
            source.sendFailure(Civitatum.message("sign_unknown", id));
            return 0;
        }
        var c = state.character().copy();
        c.birthsign = sign.id();
        p.setData(Civitatum.STATE, state.withCharacter(c));
        RaceService.apply(p);
        RaceNetwork.sync(p);
        p.sendSystemMessage(Civitatum.message("sign_given", Component.translatable(sign.key("name"))));
        source.sendSuccess(() -> Civitatum.message("sign_given", Component.translatable(sign.key("name"))), true);
        return 1;
    }

    private static int rewrite(ServerPlayer p) {
        var state = p.getData(Civitatum.STATE);
        if (!state.character().selected || state.character().birthsign.equals(BirthsignCatalog.NONE)) {
            p.sendSystemMessage(Civitatum.message("rewrite.no_sign"));
            return 0;
        }
        if (state.skyRewritten()) {
            p.sendSystemMessage(Civitatum.message("rewrite.used"));
            return 0;
        }
        long time = p.serverLevel().getDayTime() % DAY;
        if (!p.serverLevel().dimensionType().hasSkyLight() || time < NIGHT_START || time > NIGHT_END) {
            p.sendSystemMessage(Civitatum.message("rewrite.not_night"));
            return 0;
        }
        if (p.experienceLevel < 1 && !p.isCreative()) {
            p.sendSystemMessage(Civitatum.message("rewrite.no_level"));
            return 0;
        }
        if (!p.isCreative()) p.giveExperienceLevels(-1);
        var c = state.character().copy();
        c.birthsign = BirthsignCatalog.NONE;
        long dawn = RaceService.time(p) + (DAY - time);
        p.setData(Civitatum.STATE, state.withSkyRewritten(c, dawn));
        RaceService.apply(p);
        RaceNetwork.sync(p);
        p.sendSystemMessage(Civitatum.message("rewrite.done"));
        RaceService.open(p, false, false);
        return 1;
    }

    private SignCommands() {}
}
