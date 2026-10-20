package team.civitatum.starclock;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import team.civitatum.starclock.network.StarclockNetwork;
import team.civitatum.starclock.registry.ModItems;
import team.civitatum.starclock.registry.ModTabs;
import team.civitatum.starclock.ritual.RitualManager;

@Mod(CivitatumStarclock.ID)
public final class CivitatumStarclock {
    public static final String ID = "civitatum_starclock";

    public CivitatumStarclock(IEventBus bus) {
        ModItems.ITEMS.register(bus);
        ModTabs.TABS.register(bus);
        bus.addListener(StarclockNetwork::register);
        NeoForge.EVENT_BUS.register(RitualManager.class);
    }

    public static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
