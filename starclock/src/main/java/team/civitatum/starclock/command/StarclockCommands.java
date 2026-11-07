package team.civitatum.starclock.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringUtil;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import team.civitatum.starclock.data.PendingPrediction;
import team.civitatum.starclock.data.StarClockData;
import team.civitatum.starclock.discord.DiscordNotifier;
import team.civitatum.starclock.prediction.PredictionService;

public final class StarclockCommands {
    /** Тот же уровень, что у /time: команды меняют состояние всего мира. */
    private static final int ADMIN_LEVEL = 2;
    private static final float TICKS_PER_SECOND = 20;
    /**
     * Обычный селектор @a видит только игроков в сети, а кулдауны и предсказания есть и у тех, кто вышел.
     * Отдельный литерал важнее аргумента при разборе, поэтому здесь @a значит действительно всех.
     */
    private static final String EVERYONE = "@a";
    /** Аргумент профиля отклоняет @s: для него это селектор сущностей, хотя цель здесь всегда сам игрок. */
    private static final String SELF = "@s";
    private static final String TARGETS = "targets";

    private record Targets(Predicate<UUID> players, boolean isEveryone) {}

    @FunctionalInterface
    private interface TargetedCommand {
        int run(CommandContext<CommandSourceStack> context, Targets targets) throws CommandSyntaxException;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var cooldown = Commands.literal("cooldown")
                .then(targeted("reset", StarclockCommands::resetCooldown));
        var prediction = Commands.literal("prediction")
                .then(Commands.literal("list").executes(StarclockCommands::list))
                .then(targeted("cancel", StarclockCommands::cancel))
                .then(targeted("fulfill", StarclockCommands::fulfill));
        event.getDispatcher().register(Commands.literal("starclock")
                .requires(source -> source.hasPermission(ADMIN_LEVEL)).then(cooldown).then(prediction));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> targeted(String name, TargetedCommand command) {
        return Commands.literal(name)
                .then(Commands.literal(EVERYONE).executes(context ->
                        command.run(context, new Targets(player -> true, true))))
                .then(Commands.literal(SELF).executes(context -> {
                    UUID self = context.getSource().getPlayerOrException().getUUID();
                    return command.run(context, new Targets(self::equals, false));
                }))
                // Аргумент профиля понимает селекторы и находит по имени игроков вне сети через кэш профилей.
                .then(Commands.argument(TARGETS, GameProfileArgument.gameProfile()).executes(context ->
                        command.run(context, selected(context))));
    }

    private static Targets selected(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Set<UUID> players = new HashSet<>();
        for (var profile : GameProfileArgument.getGameProfiles(context, TARGETS)) players.add(profile.getId());
        return new Targets(players::contains, false);
    }

    private static int resetCooldown(CommandContext<CommandSourceStack> context, Targets targets) {
        var source = context.getSource();
        int reset = data(context).resetCooldowns(source.getServer().overworld().getDayTime(), targets.players());
        if (reset == 0) {
            source.sendFailure(Component.translatable("command.civitatum_starclock.cooldown.none"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.civitatum_starclock.cooldown.reset", reset), true);
        return reset;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var source = context.getSource();
        var pending = data(context).pending();
        if (pending.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.civitatum_starclock.prediction.empty"), false);
            return 0;
        }
        long now = source.getServer().overworld().getGameTime();
        for (var prediction : pending) {
            source.sendSuccess(() -> line(source.getServer(), prediction, now), false);
        }
        return pending.size();
    }

    private static Component line(MinecraftServer server, PendingPrediction prediction, long now) {
        long left = prediction.dueGameTime() - now;
        Component due = left > 0
                ? Component.translatable("command.civitatum_starclock.prediction.left",
                        StringUtil.formatTickDuration((int) Math.min(left, Integer.MAX_VALUE), TICKS_PER_SECOND))
                : Component.translatable("command.civitatum_starclock.prediction.due");
        return Component.translatable("command.civitatum_starclock.prediction.line",
                playerName(server, prediction.player()), prediction.plan().eventId(),
                prediction.nextAction(), prediction.plan().actions().size(), due);
    }

    // Игрок может быть вне сети: его предсказание ждёт входа, а имя берётся из кэша профилей.
    private static String playerName(MinecraftServer server, UUID player) {
        var cache = server.getProfileCache();
        if (cache == null) return player.toString();
        return cache.get(player).map(profile -> profile.getName()).orElseGet(player::toString);
    }

    private static int cancel(CommandContext<CommandSourceStack> context, Targets targets) {
        var source = context.getSource();
        int cancelled = data(context).cancelPredictions(targets.players());
        if (cancelled == 0) {
            source.sendFailure(Component.translatable("command.civitatum_starclock.prediction.none"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.civitatum_starclock.prediction.cancelled",
                cancelled), true);
        // В канал идёт только отмена для всех: личная касается одного игрока.
        if (targets.isEveryone()) DiscordNotifier.predictionsCancelled(cancelled);
        return cancelled;
    }

    private static int fulfill(CommandContext<CommandSourceStack> context, Targets targets) {
        var source = context.getSource();
        var result = PredictionService.fulfill(source.getServer(), targets.players());
        if (result.total() == 0) {
            source.sendFailure(Component.translatable("command.civitatum_starclock.prediction.none"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.civitatum_starclock.prediction.fulfilled",
                result.fulfilled(), result.waiting(), result.retrying()), true);
        return result.fulfilled();
    }

    private static StarClockData data(CommandContext<CommandSourceStack> context) {
        return StarClockData.get(context.getSource().getServer());
    }

    private StarclockCommands() {}
}
