package team.civitatum.books.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class BookCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("civitatumbooks")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("edit").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    BookServer.open(player, player.getInventory().selected, null, true);
                    return 1;
                }))
                .then(Commands.literal("author")
                        .then(Commands.argument("name", StringArgumentType.greedyString()).executes(context -> {
                            BookServer.changeAuthor(context.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(context, "name"));
                            return 1;
                        }))));
    }

    private BookCommands() {}
}
