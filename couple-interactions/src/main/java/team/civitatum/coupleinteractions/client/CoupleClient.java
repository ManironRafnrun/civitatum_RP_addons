package team.civitatum.coupleinteractions.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import team.civitatum.coupleinteractions.CoupleInteractions;
import team.civitatum.coupleinteractions.network.ClientBridge;

@Mod(value = CoupleInteractions.ID, dist = Dist.CLIENT)
public final class CoupleClient {
    private static final String CATEGORY = "key.categories.civitatum_couple";
    public static final KeyMapping MENU = new KeyMapping("key.civitatum_couple.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, CATEGORY);
    public static final KeyMapping ACCEPT = new KeyMapping("key.civitatum_couple.accept", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);
    public static final KeyMapping DECLINE = new KeyMapping("key.civitatum_couple.decline", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);

    public CoupleClient(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, CoupleClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> new SettingsScreen(parent));
        bus.addListener(CoupleClient::keys);
        ClientBridge.invitation = ClientState::invite;
        ClientBridge.clearInvitation = ClientState::clearInvite;
        ClientBridge.carryState = ClientState::carry;
        ClientBridge.kissEffect = ClientState::kiss;
        ClientBridge.carrierOf = ClientState::carrierOf;
        ClientBridge.passengerOf = ClientState::passengerOf;
        ClientBridge.poseOf=id->PoseEngine.previewSecond!=null && PoseEngine.previewSecond.getUUID().equals(id)?PoseEngine.previewPose:ClientState.pose(id);
        ClientBridge.previewCarrier=PoseEngine::previewCarrier;ClientBridge.previewPassenger=PoseEngine::previewPassenger;
        ClientBridge.ignorePose=CccCompat::preview;
        ClientBridge.socialState=ClientState::social;
        ClientBridge.socialOf=id->{var s=ClientState.social(id);return s==null?null:s.state();};
        if(net.neoforged.fml.ModList.get().isLoaded("playeranimator"))AnimatorCompat.init();
        NeoForge.EVENT_BUS.register(ClientEvents.class);
    }
    private static void keys(RegisterKeyMappingsEvent event) { event.register(MENU); event.register(ACCEPT); event.register(DECLINE); }
    private CoupleClient() {}
}
