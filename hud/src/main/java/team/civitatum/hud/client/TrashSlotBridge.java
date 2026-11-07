package team.civitatum.hud.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * TrashSlot's trash cell in our inventory. TrashSlot keeps doing all the work it does in any container screen: the
 * clicks and keys that trash and bring back items, the server's copy of the trashed stack, its hints. Only the cell is
 * ours: a slot of the items window in our style, drawn with the window's depth, where TrashSlot would draw its own
 * frame over the middle of our full-screen menu (see {@code TrashSlotMixin}). Reflection keeps TrashSlot optional.
 *
 * <p>TrashSlot shows its cell from the start only on vanilla's inventory, crafting table and chests; on any other
 * menu it waits for its key. Here every menu with the player's inventory in it shows it, see {@link #layout}.
 */
public final class TrashSlotBridge {
    private static final String MOD = "trashslot";
    private static final String LAYOUT = "net.blay09.mods.trashslot.api.IGuiContainerLayout";
    private static final String SIMPLE_LAYOUT = "net.blay09.mods.trashslot.client.gui.layout.SimpleGuiContainerLayout";
    /**
     * Before the class name in the key TrashSlot keeps a screen's cell under. TrashSlot has already kept the menus
     * it opened before as ones with the cell off; under a key of our own they start with it on. The player's choice,
     * the cell switched off with the key or moved, is kept as before, under this key.
     */
    private static final String KEY = "civitatum_hud/";
    private static final String HANDLER = "net.blay09.mods.trashslot.client.TrashSlotGuiHandler";
    private static final String CONFIG = "net.blay09.mods.trashslot.config.TrashSlotConfig";
    private static boolean registered, broken;
    private static Method slot, component, visible, config;
    /** TrashSlot's layout for screens it has none for, and ours over it that shows the cell from the start. */
    private static Object plain, shownAtStart;

    private TrashSlotBridge() {}

    /** Whether our cell stands in for TrashSlot's in our screen; if TrashSlot changed, its own cell is left be. */
    public static boolean active() {
        return registered && !broken;
    }

    public static boolean available() {
        return !broken && CompatBridge.loaded(MOD);
    }

    /** Looks TrashSlot's parts up, once: when it first asks for a layout or our screen first opens. */
    static void register() {
        if (registered || !available()) return;
        registered = true;
        try {
            plain = Class.forName(SIMPLE_LAYOUT).getField("DEFAULT").get(null);
            var layout = Class.forName(LAYOUT);
            var loader = layout.getClassLoader();
            shownAtStart = Proxy.newProxyInstance(loader, new Class<?>[]{layout}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "isEnabledByDefault" -> true;
                    case "getContainerId" -> KEY + args[0].getClass().getName().replace('.', '/');
                    default -> method.invoke(plain, args);
                });
            var handler = Class.forName(HANDLER);
            slot = handler.getMethod("getTrashSlot");
            component = handler.getMethod("getTrashSlotComponent");
            visible = component.getReturnType().getMethod("isVisible");
            config = Class.forName(CONFIG).getMethod("getActive");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            fail(ex);
        }
    }

    /**
     * The layout TrashSlot is to use for a screen, given the one it found: for a menu with the player's inventory in
     * it that TrashSlot knows nothing of (a furnace, a mod's machine, our inventory), one that shows the cell from the
     * start. Menus TrashSlot or a mod gave a layout of their own keep it.
     */
    public static Object layout(AbstractContainerScreen<?> screen, Object found) {
        register();
        if (!active() || found != plain) return found;
        boolean inventory = screen.getMenu().slots.stream().anyMatch(s -> s.container instanceof Inventory);
        return inventory ? shownAtStart : found;
    }

    /** TrashSlot's slot in the screen open now, when TrashSlot is on the server too; null otherwise. */
    static Slot slot() {
        if (!registered || broken) return null;
        try {
            return component.invoke(null) == null ? null : (Slot) slot.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            fail(ex);
            return null;
        }
    }

    /** Whether the cell is shown: the player may hide it with TrashSlot's key. */
    static boolean shown() {
        if (!registered || broken) return false;
        try {
            Object cell = component.invoke(null);
            return cell != null && (boolean) visible.invoke(cell);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            fail(ex);
            return false;
        }
    }

    /** What the cell says when an item is carried over it, as TrashSlot's own cell does. */
    static Component carriedTooltip() {
        boolean instant = false;
        try {
            Object active = config.invoke(null);
            instant = active.getClass().getField("instantDeletion").getBoolean(active);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            // the wording only: an item trashed at once is the rarer setting
        }
        return Component.translatable(instant ? "tooltip.trashslot.destroy_item" : "tooltip.trashslot.trash_item");
    }

    private static void fail(Exception ex) {
        broken = true;
        TrashCell.hide();
        LogUtils.getLogger().warn("Civitatum HUD: TrashSlot is not supported in this version, its cell is off", ex);
    }
}
