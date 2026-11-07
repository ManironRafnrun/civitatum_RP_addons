package team.civitatum.books;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import team.civitatum.books.network.BookNetwork;
import team.civitatum.books.server.BookServer;
import team.civitatum.books.server.BookCommands;

@Mod(CivitatumBooks.ID)
public final class CivitatumBooks {
    public static final String ID = "civitatum_books";
    public static final Logger LOG = LogUtils.getLogger();

    public CivitatumBooks(IEventBus bus) {
        bus.addListener(BookNetwork::register);
        NeoForge.EVENT_BUS.register(BookServer.class);
        NeoForge.EVENT_BUS.register(BookCommands.class);
    }

    public static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
