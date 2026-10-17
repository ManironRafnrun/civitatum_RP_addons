package team.civitatum.inventory.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

public final class InventoryClient {
    public static final KeyMapping TOGGLE = new KeyMapping("key.civitatum_inventory.toggle", InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_F8, "key.categories.civitatum_inventory");
    private static boolean bypass;
    public static final KeyMapping ACTIVATE=new KeyMapping("key.civitatum_inventory.activate_key",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_BACKSLASH,"key.categories.civitatum_inventory");
    public static final KeyMapping NEXT=new KeyMapping("key.civitatum_inventory.next_ability",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_PAGE_DOWN,"key.categories.civitatum_inventory");
    public static final KeyMapping PREVIOUS=new KeyMapping("key.civitatum_inventory.previous_ability",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_PAGE_UP,"key.categories.civitatum_inventory");
    private static boolean skipBackpack;
    public static void register(IEventBus mod, IEventBus game) {
        mod.addListener((RegisterKeyMappingsEvent event) -> {event.register(TOGGLE);event.register(ACTIVATE);event.register(NEXT);event.register(PREVIOUS);});
        mod.addListener(CivitatumHud::register);
        game.addListener(CivitatumHud::hideReplaced);
        game.addListener(InventoryClient::opening);
        game.addListener(InventoryClient::tick);
        game.addListener(InventoryClient::key);
    }
    private static void opening(ScreenEvent.Opening event) {
        if (!InventoryPreferences.get().enabled || bypass) return;
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.gameMode != null && !mc.gameMode.hasInfiniteItems()
                && event.getNewScreen()!=null && !(event.getNewScreen() instanceof CivitatumInventoryScreen)) {
            if(event.getNewScreen().getClass()==InventoryScreen.class && mc.player.inventoryMenu.slots.size()==46) {
                var screen=new CivitatumInventoryScreen(mc.player);
                screen.allowAutoBackpack(!skipBackpack);
                event.setNewScreen(screen);
            }
            else if(CompatBridge.supported(event.getNewScreen())) {
                var nativeScreen=(AbstractContainerScreen<?>)event.getNewScreen();
                event.setNewScreen(new CivitatumInventoryScreen(mc.player,nativeScreen.getMenu(),nativeScreen));
            }
        }
    }
    private static void tick(ClientTickEvent.Post event) {
        while (TOGGLE.consumeClick()) {
            toggle();
        }
        var mc=Minecraft.getInstance();
        while(NEXT.consumeClick())if(mc.screen==null&&mc.player!=null)AbilitySelection.cycle(mc.player,1);
        while(PREVIOUS.consumeClick())if(mc.screen==null&&mc.player!=null)AbilitySelection.cycle(mc.player,-1);
        while(ACTIVATE.consumeClick())if(mc.screen==null&&mc.player!=null&&!mc.player.isSpectator())AbilitySelection.activate(mc.player);
    }
    private static void key(ScreenEvent.KeyPressed.Pre event) {
        if ((event.getScreen() instanceof CivitatumInventoryScreen || event.getScreen() instanceof InventoryScreen || CompatBridge.supported(event.getScreen()))
                && TOGGLE.matches(event.getKeyCode(),event.getScanCode())) {
            toggle();event.setCanceled(true);
        }
    }
    private static void toggle() {
        var mc=Minecraft.getInstance();var preferences=InventoryPreferences.get();
        preferences.enabled=!preferences.enabled;preferences.save();
        if(mc.gameMode!=null&&mc.gameMode.hasInfiniteItems())return;
        if(mc.screen instanceof CivitatumInventoryScreen screen) screen.openNative();
        else if(mc.player!=null && mc.screen instanceof InventoryScreen) mc.setScreen(new InventoryScreen(mc.player));
        else if(mc.player!=null && CompatBridge.supported(mc.screen)) {
            var screen=(AbstractContainerScreen<?>)mc.screen;
            if(CompatBridge.kind(screen.getMenu())==CompatBridge.Kind.ACCESSORIES)CompatBridge.openAccessories();
            else mc.setScreen(new CivitatumInventoryScreen(mc.player,screen.getMenu(),screen));
        }
    }
    public static void vanilla() {
        var mc = Minecraft.getInstance();
        bypass = true;
        try { mc.setScreen(new InventoryScreen(mc.player)); }
        finally { bypass = false; }
    }
    public static void personalInventory(boolean equipment) {
        var mc=Minecraft.getInstance();
        if(mc.player==null)return;
        mc.player.closeContainer();skipBackpack=equipment;
        try {mc.setScreen(new InventoryScreen(mc.player));}
        finally {skipBackpack=false;}
    }
    public static void nativeScreen(net.minecraft.client.gui.screens.Screen screen) {
        var mc=Minecraft.getInstance();bypass=true;
        try {mc.setScreen(screen);}finally{bypass=false;}
    }
}
