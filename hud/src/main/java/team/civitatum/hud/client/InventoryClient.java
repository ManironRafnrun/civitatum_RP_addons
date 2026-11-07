package team.civitatum.hud.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.lwjgl.glfw.GLFW;

public final class InventoryClient {
    private static final String CATEGORY = "key.categories.civitatum_hud";
    /** Slots of the vanilla player menu: 5 crafting, 4 armour, 27 storage, 9 hotbar and the off hand. */
    private static final int VANILLA_INVENTORY_SLOTS = 46;
    public static final KeyMapping ACTIVATE = mapping("activate_key", GLFW.GLFW_KEY_BACKSLASH);
    public static final KeyMapping NEXT = mapping("next_ability", GLFW.GLFW_KEY_PAGE_DOWN);
    public static final KeyMapping PREVIOUS = mapping("previous_ability", GLFW.GLFW_KEY_PAGE_UP);
    /** JEI on its own, out of the inventory; the apostrophe is free among the pack's mods. */
    public static final KeyMapping JEI = mapping("jei_browser", GLFW.GLFW_KEY_APOSTROPHE);
    private static boolean skipBackpack;

    private static KeyMapping mapping(String name, int key) {
        return new KeyMapping("key.civitatum_hud." + name, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    public static void register(IEventBus mod, IEventBus game) {
        mod.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(ACTIVATE);
            event.register(NEXT);
            event.register(PREVIOUS);
            event.register(JEI);
        });
        mod.addListener(CivitatumHud::register);
        mod.addListener(InventoryClient::addStylePack);
        // Resource packs may replace ParCool's icons; they are recoloured again on the next draw.
        mod.addListener((RegisterClientReloadListenersEvent event) ->
            event.registerReloadListener((ResourceManagerReloadListener) manager -> ParCoolEnergyIcons.clear()));
        game.addListener(CivitatumHud::hideReplaced);
        // Last, so the layer is not run here when another listener cancels it.
        game.addListener(EventPriority.LOWEST, CivitatumHud::moveParCoolEnergy);
        game.addListener((RenderGuiEvent.Pre event) -> MinimapBounds.clear());
        game.addListener(InventoryClient::opening);
        game.addListener(InventoryClient::tick);
    }

    /**
     * The restyled vanilla and mod screens as a built-in pack above all mods: as plain mod resources they would lose
     * to a mod that ships the same file (Supplementaries' own slot icons), depending on the order mods load in.
     */
    private static void addStylePack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addPackFinders(ResourceLocation.fromNamespaceAndPath("civitatum_hud", "resourcepacks/civitatum_style"),
            PackType.CLIENT_RESOURCES, Component.literal("Civitatum Style"), PackSource.BUILT_IN, true,
            Pack.Position.TOP);
    }

    private static void opening(ScreenEvent.Opening event) {
        var mc = Minecraft.getInstance();
        var opened = event.getNewScreen();
        if (mc.player == null || mc.gameMode == null || mc.gameMode.hasInfiniteItems()
                || opened == null || opened instanceof CivitatumInventoryScreen) return;
        boolean vanillaInventory = mc.player.inventoryMenu.slots.size() == VANILLA_INVENTORY_SLOTS;
        if (opened.getClass() == InventoryScreen.class && vanillaInventory) {
            var screen = new CivitatumInventoryScreen(mc.player);
            screen.allowAutoBackpack(!skipBackpack);
            event.setNewScreen(screen);
        } else if (CompatBridge.supported(opened)) {
            var container = (AbstractContainerScreen<?>) opened;
            event.setNewScreen(new CivitatumInventoryScreen(mc.player, container.getMenu()));
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        boolean inWorld = mc.screen == null && mc.player != null;
        while (NEXT.consumeClick()) if (inWorld) AbilitySelection.cycle(mc.player, 1);
        while (PREVIOUS.consumeClick()) if (inWorld) AbilitySelection.cycle(mc.player, -1);
        while (ACTIVATE.consumeClick()) if (inWorld && !mc.player.isSpectator()) AbilitySelection.activate(mc.player);
        // the browser's class names JEI's: it is loaded only with JEI there
        while (JEI.consumeClick()) if (inWorld && CompatBridge.loaded("jei")) team.civitatum.hud.jei.JeiBrowser.open();
    }

    public static void personalInventory(boolean equipment) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.closeContainer();
        skipBackpack = equipment;
        try {
            mc.setScreen(new InventoryScreen(mc.player));
        } finally {
            skipBackpack = false;
        }
    }
}
