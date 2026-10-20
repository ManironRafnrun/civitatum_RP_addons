package team.civitatum.starclock;

import net.minecraft.resources.ResourceLocation;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import team.civitatum.starclock.config.StarClockConfig;
import team.civitatum.starclock.prediction.PredictionService;
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
    public static final Logger LOG = LogUtils.getLogger();

    public CivitatumStarclock(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, StarClockConfig.SPEC);
        ModItems.ITEMS.register(bus);
        ModTabs.TABS.register(bus);
        bus.addListener(StarclockNetwork::register);
        NeoForge.EVENT_BUS.register(RitualManager.class);
        NeoForge.EVENT_BUS.register(PredictionService.class);
    }

    public static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
