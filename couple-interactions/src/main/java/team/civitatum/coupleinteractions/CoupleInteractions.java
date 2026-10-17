package team.civitatum.coupleinteractions;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import team.civitatum.coupleinteractions.network.CoupleNetwork;
import team.civitatum.coupleinteractions.server.CoupleServer;

@Mod(CoupleInteractions.ID)
public final class CoupleInteractions {
    public static final String ID = "civitatum_couple";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> KISS = SOUNDS.register("kiss", () -> SoundEvent.createVariableRangeEvent(id("kiss")));

    public CoupleInteractions(IEventBus bus, ModContainer container) {
        SOUNDS.register(bus);
        bus.addListener(CoupleNetwork::register);
        container.registerConfig(ModConfig.Type.SERVER, CoupleConfig.SPEC);
        NeoForge.EVENT_BUS.register(CoupleServer.class);
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
}
