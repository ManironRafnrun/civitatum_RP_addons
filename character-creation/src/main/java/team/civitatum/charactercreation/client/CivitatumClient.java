package team.civitatum.charactercreation.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.*;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.RaceNetwork;

@Mod(value = Civitatum.ID, dist = Dist.CLIENT)
public final class CivitatumClient {
    public static final KeyMapping ABILITY = new KeyMapping("key.civitatum.ability", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.civitatum");
    public static final KeyMapping EDITOR = new KeyMapping("key.civitatum.appearance", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "key.categories.civitatum");

    public CivitatumClient(IEventBus bus) {
        bus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> SkinTemplates.clear()));
        RaceNetwork.CLIENT_RECEIVER = ClientCharacters::accept;
        bus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(ABILITY);
            e.register(EDITOR);
        });
        bus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> e.registerLayerDefinition(CharacterLayer.MODEL, CharacterLayer::createModel));
        bus.addListener((EntityRenderersEvent.AddLayers e) -> {
            for (var skin : e.getSkins())
                if (e.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer)
                    renderer.addLayer(new CharacterLayer(renderer, e.getEntityModels().bakeLayer(CharacterLayer.MODEL)));
        });
    }

    @EventBusSubscriber(modid = Civitatum.ID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            var game = Minecraft.getInstance();
            while (ABILITY.consumeClick()) if (game.player != null && game.screen == null)
                PacketDistributor.sendToServer(new RaceNetwork.Serverbound(1, 0, ""));
            while (EDITOR.consumeClick()) if (game.player != null && game.screen == null)
                PacketDistributor.sendToServer(new RaceNetwork.Serverbound(2, 0, ""));
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
            ClientCharacters.clear();
        }
    }
}
