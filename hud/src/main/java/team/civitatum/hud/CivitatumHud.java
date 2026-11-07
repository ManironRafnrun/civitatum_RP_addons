package team.civitatum.hud;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import team.civitatum.hud.client.HudSettingsScreen;
import team.civitatum.hud.client.InventoryClient;

/** No server entry point, custom inventories, packets, or gameplay changes. */
@Mod(value = CivitatumHud.ID, dist = Dist.CLIENT)
public final class CivitatumHud {
    public static final String ID = "civitatum_hud";

    public CivitatumHud(IEventBus bus, ModContainer container) {
        InventoryClient.register(bus, NeoForge.EVENT_BUS);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new HudSettingsScreen(parent));
    }
}
