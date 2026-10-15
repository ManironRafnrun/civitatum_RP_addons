package team.civitatum.couple.client;

import java.util.UUID;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import team.civitatum.couple.CarryHooks;
import team.civitatum.couple.SocialHooks;
import team.civitatum.couple.network.CoupleNetwork;
import net.neoforged.neoforge.network.PacketDistributor;
import team.civitatum.couple.core.Interaction;

public final class ClientEvents {
    private static LocalPlayer lastPlayer;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ClientState.ticks++;
        if (lastPlayer != mc.player) { lastPlayer = mc.player; ClientState.sendPreferences(); }
        if (ClientState.invitation != null && ClientState.ticks >= ClientState.invitationEnd) ClientState.invitation = null;
        boolean inGame = mc.screen == null || mc.screen instanceof RadialScreen;
        while (CoupleClient.ACCEPT.consumeClick()) if (inGame) ClientState.decide(true);
        while (CoupleClient.DECLINE.consumeClick()) if (inGame) ClientState.decide(false);
        while (CoupleClient.MENU.consumeClick()) {
            if (mc.screen != null || !mc.player.isAlive() || mc.player.isSpectator()) continue;
            UUID partner = CarryHooks.carrierId(mc.player);
            if (partner == null) partner = CarryHooks.passengerId(mc.player);
            if(partner==null){var social=ClientState.social(mc.player.getUUID());if(social!=null)partner=social.state().first().equals(mc.player.getUUID())?social.state().second():social.state().first();}
            Player target = partner == null ? null : mc.level.getPlayerByUUID(partner);
            if (target == null && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player p) target = p;
            if (target != null && target != mc.player && mc.player.distanceToSqr(target) <= 16) {
                mc.setScreen(new RadialScreen(target));
            } else if (partner != null) {
                // A tracked entity can briefly disappear: still allow emergency release.
                mc.setScreen(new RadialScreen(partner, Component.translatable("ui.civitatum_couple.partner"), true));
            } else {
                mc.setScreen(new SettingsScreen(null));
            }
        }
        if (CarryHooks.carrying(mc.player)) mc.player.setSprinting(false);
        if(SocialHooks.active(mc.player) && mc.player.isShiftKeyDown())PacketDistributor.sendToServer(new CoupleNetwork.Action(mc.player.getUUID(),Interaction.RELEASE.ordinal()));
    }
    @SubscribeEvent public static void movement(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event){if(SocialHooks.active(event.getEntity())){var input=event.getInput();input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;}}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientState.clear(); lastPlayer = null; }
    @SubscribeEvent public static void hand(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player != null && CarryHooks.occupied(player)) event.setCanceled(true);
    }
    @SubscribeEvent public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        var player = Minecraft.getInstance().player;
        if (player != null && CarryHooks.occupied(player)) { event.setCanceled(true); event.setSwingHand(false); }
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || ClientState.invitation == null) return;
        var invite = ClientState.invitation;
        Interaction action = Interaction.fromWire(invite.action());
        if (action == null) return;
        var g = event.getGuiGraphics();
        int center = g.guiWidth() / 2, top = 30, half = Math.min(158, center - 6);
        g.fill(center - half, top, center + half, top + 50, 0xEA16171D);
        g.fill(center - half, top, center - half + 2, top + 50, 0xFFCFA0A2);
        Component title = Component.translatable("ui.civitatum_couple.invite_" + action.name().toLowerCase(java.util.Locale.ROOT), invite.name());
        g.drawCenteredString(mc.font, title, center, top + 8, 0xFFF3EDE4);
        g.drawCenteredString(mc.font, Component.translatable("ui.civitatum_couple.respond", CoupleClient.ACCEPT.getTranslatedKeyMessage(), CoupleClient.DECLINE.getTranslatedKeyMessage()), center, top + 23, 0xFFC7C0B6);
        int seconds = (int) Math.max(0, (ClientState.invitationEnd - ClientState.ticks + 19) / 20);
        g.drawCenteredString(mc.font, Component.translatable("ui.civitatum_couple.seconds", seconds), center, top + 37, 0xFF8B939D);
    }
    public static boolean held(KeyMapping binding) {
        var key = binding.getKey(); long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() >= 0) return InputConstants.isKeyDown(window, key.getValue());
        return binding.isDown();
    }
    private ClientEvents() {}
}
