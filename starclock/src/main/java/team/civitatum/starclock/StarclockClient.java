package team.civitatum.starclock;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import team.civitatum.starclock.client.CardScreen;
import team.civitatum.starclock.client.RitualClientState;
import team.civitatum.starclock.network.CancelRitualPayload;
import team.civitatum.starclock.network.RitualStatePayload;
import team.civitatum.starclock.network.ShowCardPayload;

@EventBusSubscriber(modid = CivitatumStarclock.ID, value = Dist.CLIENT)
public final class StarclockClient {
    public static void state(RitualStatePayload packet) {
        RitualClientState.accept(packet);
    }

    public static void card(ShowCardPayload packet) {
        var state = RitualClientState.current();
        if (state == null || !state.session().equals(packet.session())) return;
        RitualClientState.clear();
        Minecraft.getInstance().setScreen(new CardScreen(packet));
    }

    public static void cancel(CancelRitualPayload packet) {
        var state = RitualClientState.current();
        if (state == null || !state.session().equals(packet.session())) return;
        RitualClientState.clear();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.stopUsingItem();
            player.displayClientMessage(Component.translatable("message.civitatum_starclock.cancelled",
                    Component.translatable("message.civitatum_starclock." + packet.reason())), true);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        RitualClientState.clear();
        team.civitatum.starclock.client.TowerKeyClient.accept(0);
    }

    @SubscribeEvent
    public static void overlay(RenderGuiEvent.Post event) {
        var state = RitualClientState.current();
        var mc = Minecraft.getInstance();
        if (state == null || mc.player == null || mc.level == null) return;
        var graphics = event.getGuiGraphics();
        int center = graphics.guiWidth() / 2;
        int top = 20;
        if (state.drawing()) graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x990B1020);
        graphics.drawCenteredString(mc.font, Component.translatable(state.drawing()
                ? "ui.civitatum_starclock.drawing" : "ui.civitatum_starclock.preparation"),
                center, top, 0xFFE3C07B);
        graphics.fill(center - 60, top + 15, center + 60, top + 19, 0xFF2A2118);
        graphics.fill(center - 60, top + 15, center - 60 + (int) (120 * RitualClientState.progress()),
                top + 19, 0xFFE3C07B);
    }

    private StarclockClient() {}
}
