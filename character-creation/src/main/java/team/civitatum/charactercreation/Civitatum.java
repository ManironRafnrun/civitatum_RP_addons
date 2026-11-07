package team.civitatum.charactercreation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.*;
import net.minecraft.world.item.Item;

@Mod(Civitatum.ID)
public final class Civitatum {
    public static final String ID = "civitatum";
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RaceState>> STATE = ATTACHMENTS.register("character",
        () -> AttachmentType.builder(() -> RaceState.EMPTY).serialize(RaceState.CODEC).copyOnDeath().build());
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredItem<Item> SHARD = ITEMS.registerSimpleItem("demitara_shard", new Item.Properties().stacksTo(64));
    public static final DeferredItem<Item> MIRROR = ITEMS.registerSimpleItem("appearance_mirror", new Item.Properties().stacksTo(16));
    public Civitatum(IEventBus bus) {
        ATTACHMENTS.register(bus); ITEMS.register(bus);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e)->{
            if(e.getTabKey()==net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES){e.accept(MIRROR.get());e.accept(SHARD.get());}
        });
        bus.addListener(RaceNetwork::register);
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(CompatPatches::init));
        RaceCatalog.all();
        ModConfig.load();
    }
    /** Prefix of the error codes sent to the editor; the client translates them. */
    public static final String ERROR = "message.civitatum.error.";

    /** A chat or action-bar message; translated by the client into its own language. */
    public static net.minecraft.network.chat.Component message(String key, Object... args) {
        return net.minecraft.network.chat.Component.translatable("message.civitatum." + key, args);
    }

    public static net.minecraft.resources.ResourceLocation res(String name) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ID, name);
    }
}
