package team.civitatum.hud.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Optional integrations never invent slots or alter the server's menu layout.
 *
 * <p>The screen asks these questions every frame for every slot, so classes, methods and fields of the optional mods
 * are looked up once and kept, together with a failed lookup: an absent member is not searched again. Everything runs
 * on the render thread.
 */
public final class CompatBridge {
    public enum Kind { INVENTORY, CURIOS, BACKPACK, ACCESSORIES, AETHER }

    private static final String CURIOS_MENU = "top.theillusivec4.curios.common.inventory.container.CuriosContainer";
    private static final String CURIOS_SLOT = "top.theillusivec4.curios.common.inventory.CurioSlot";
    private static final String CURIOS_PACKETS = "top.theillusivec4.curios.common.network.client.";
    private static final String ACCESSORIES_MENUS = "io.wispforest.accessories.menu.variants.Accessories";
    private static final String ACCESSORIES_ARMOR_SLOT = "io.wispforest.accessories.menu.AccessoriesArmorSlot";
    private static final String ACCESSORIES_INTERNAL_SLOT = "io.wispforest.accessories.menu.AccessoriesInternalSlot";
    private static final String AETHER_MENUS = "com.aetherteam.aether.inventory.menu.";
    private static final String[] ARMOR_SLOT_TYPES = {"headSlot", "chestSlot", "legsSlot", "feetSlot"};
    /** How long a chest screen counts as the answer to an ender-pack request. */
    private static final long BACKPACK_REQUEST_MILLIS = 5000;

    private static final Set<AbstractContainerMenu> enderpacks = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Class<?>, Kind> KINDS = new HashMap<>();
    private static final Map<String, Object> CLASSES = new HashMap<>();
    private static final Map<Class<?>, Map<String, Object>> METHODS = new HashMap<>();
    private static final Map<Class<?>, Map<String, Object>> FIELDS = new HashMap<>();
    private static long backpackRequestUntil;
    private static Method capabilityOf, shouldRender, equippedBackpack;
    private static List<String> armorNames;
    private static boolean wornFailed;

    public static boolean loaded(String id) { return ModList.get().isLoaded(id); }

    public static Kind kind(AbstractContainerMenu menu) {
        Kind kind = KINDS.computeIfAbsent(menu.getClass(), CompatBridge::kindOf);
        // An ender pack is a plain chest menu; only its title, seen when it opens, tells it apart.
        if (kind != Kind.CURIOS && kind != Kind.ACCESSORIES && kind != Kind.BACKPACK && enderpacks.contains(menu)) {
            return Kind.BACKPACK;
        }
        return kind;
    }

    private static Kind kindOf(Class<?> type) {
        String name = type.getName();
        if (name.equals(CURIOS_MENU)) return Kind.CURIOS;
        if (name.startsWith(ACCESSORIES_MENUS)) return Kind.ACCESSORIES;
        if (name.startsWith("net.satisfy.camping.") && name.endsWith("BackpackScreenHandler")) return Kind.BACKPACK;
        if (name.equals(AETHER_MENUS + "AetherAccessoriesMenu")) return Kind.AETHER;
        return Kind.INVENTORY;
    }

    public static boolean supported(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return false;
        var menu = container.getMenu();
        if (kind(menu) == Kind.ACCESSORIES) try {
            Object target = method(menu.getClass(), "targetEntity").invoke(menu);
            if (target != null && target != Minecraft.getInstance().player) return false;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
        if (menu instanceof ChestMenu && backpackRequestUntil > System.currentTimeMillis()
                && screen.getTitle().getContents() instanceof TranslatableContents text
                && text.getKey().equals("container.camping.enderpack")) {
            enderpacks.add(menu);
        }
        return kind(menu) != Kind.INVENTORY;
    }

    public static ItemStack backpack(Player player) {
        if (!loaded("camping")) return ItemStack.EMPTY;
        try {
            Object platform = field(type("net.satisfy.camping.platform.Services"), "PLATFORM").get(null);
            // asked twice a frame while the inventory is open; the platform object never changes
            if (equippedBackpack == null) {
                equippedBackpack = platform.getClass().getMethod("getEquippedBackpack", Player.class);
            }
            return (ItemStack) equippedBackpack.invoke(platform, player);
        } catch (ReflectiveOperationException | LinkageError ex) {
            return ItemStack.EMPTY;
        }
    }

    public static boolean openBackpack() {
        if (!loaded("camping")) return false;
        try {
            Object payload = Class.forName("net.satisfy.camping.core.network.packet.NeoForgeOpenBackpackC2SPacket")
                .getField("INSTANCE").get(null);
            backpackRequestUntil = System.currentTimeMillis() + BACKPACK_REQUEST_MILLIS;
            PacketDistributor.sendToServer((CustomPacketPayload) payload);
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            fail("Camping", ex);
            return false;
        }
    }

    /**
     * Accessories' experimental menu. Unlike the original one it has the 2x2 crafting grid; this screen draws its
     * accessory slots as the classic column itself.
     */
    public static boolean openAccessories() {
        return openAccessories("EXPERIMENTAL_V1");
    }

    public static boolean openAccessories(String layout) {
        if (!loaded("accessories")) return false;
        try {
            Class<?> variantClass = Class.forName("io.wispforest.accessories.menu.AccessoriesMenuVariant");
            Object variant = variantClass.getField(layout).get(null);
            Object payload = Class.forName("io.wispforest.accessories.networking.server.ScreenOpen")
                .getMethod("of", boolean.class, variantClass).invoke(null, false, variant);
            return accessoryPacket(payload);
        } catch (ReflectiveOperationException | LinkageError ex) {
            fail("Accessories", ex);
            return false;
        }
    }

    private static boolean accessoryPacket(Object payload) {
        try {
            Class.forName("io.wispforest.accessories.networking.AccessoriesNetworking")
                .getMethod("sendToServer", Record.class).invoke(null, payload);
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            fail("Accessories network", ex);
            return false;
        }
    }

    public static boolean cosmeticSlot(Slot slot) {
        try {
            return field(slot.getClass(), "isCosmetic").getBoolean(slot);
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    /**
     * A slot of an accessory itself (regular or cosmetic). Armour and cosmetic armour slots of the experimental menu
     * are subclasses and do not count.
     */
    public static boolean accessoryItemSlot(Slot slot) {
        return slot.getClass().getName().equals(ACCESSORIES_INTERNAL_SLOT);
    }

    public static boolean accessorySlot(Slot slot) {
        try {
            method(slot.getClass(), "isCosmeticSlot");
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public static Component slotLabel(Slot slot) {
        try {
            if (slot.getClass().getName().equals(CURIOS_SLOT)) {
                Object identifier = method(slot.getClass(), "getIdentifier").invoke(slot);
                return Component.translatable("curios.identifier." + identifier);
            }
            if (accessorySlot(slot)) {
                Object type = method(slot.getClass(), "slotType").invoke(slot);
                return Component.translatable((String) method(type.getClass(), "translation").invoke(type));
            }
        } catch (ReflectiveOperationException ex) {
            return Component.literal(slot.getClass().getSimpleName());
        }
        return null;
    }

    public static int accessoryArmor(Slot slot) {
        if (!slot.getClass().getName().equals(ACCESSORIES_ARMOR_SLOT)) return -1;
        try {
            String name = (String) method(slot.getClass(), "slotName").invoke(slot);
            Class<?> types = type("io.wispforest.accessories.menu.ArmorSlotTypes");
            for (int i = 0; i < ARMOR_SLOT_TYPES.length; i++) {
                Object ref = method(types, ARMOR_SLOT_TYPES[i]).invoke(null);
                if (name.equals(method(ref.getClass(), "slotName").invoke(ref))) return i;
            }
        } catch (ReflectiveOperationException ex) {
            fail("Accessories armor", ex);
        }
        return -1;
    }

    /**
     * What the entity wears in Accessories, the slots with an item or a cosmetic look. The
     * containers are synced to the client; this is read where no Accessories menu is open (a backpack, a chest).
     */
    /** A worn accessory: its cosmetic look, the item, and whether Accessories draws that slot on the entity. */
    /**
     * A worn accessory: its cosmetic look, the item, whether Accessories draws that slot on the entity, and for the
     * armour slot types the armour row (0 head .. 3 feet), else -1.
     */
    public record Worn(ItemStack look, ItemStack item, boolean shown, int armor) {}

    public static List<Worn> wornAccessories(LivingEntity entity) {
        List<Worn> worn = new ArrayList<>();
        if (wornFailed || !loaded("accessories")) return worn;
        try {
            Class<?> capabilityType = type("io.wispforest.accessories.api.AccessoriesCapability");
            Class<?> containerType = type("io.wispforest.accessories.api.AccessoriesContainer");
            if (capabilityOf == null) capabilityOf = capabilityType.getMethod("get", LivingEntity.class);
            Object capability = capabilityOf.invoke(null, entity);
            if (capability == null) return worn;
            Method regular = method(containerType, "getAccessories");
            Method cosmetic = method(containerType, "getCosmeticAccessories");
            var containers = (Map<?, ?>) method(capabilityType, "getContainers").invoke(capability);
            for (var entry : containers.entrySet()) {
                Object container = entry.getValue();
                int armor = armorNames().indexOf(String.valueOf(entry.getKey()));
                var items = (Container) regular.invoke(container);
                var looks = (Container) cosmetic.invoke(container);
                for (int i = 0; i < items.getContainerSize(); i++) {
                    ItemStack item = items.getItem(i);
                    ItemStack look = i < looks.getContainerSize() ? looks.getItem(i) : ItemStack.EMPTY;
                    // An armour slot is listed even when empty: hiding it hides the armour itself.
                    if (armor >= 0 || !item.isEmpty() || !look.isEmpty()) {
                        worn.add(new Worn(look, item, shouldRender(container, i), armor));
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ex) {
            // Drawn every frame: one warning, then the window stays empty.
            wornFailed = true;
            fail("Accessories worn items", ex);
        }
        return worn;
    }

    /**
     * Whether Accessories draws the item of this accessory slot on the entity; Ctrl+RMB switches it. Regular and
     * cosmetic slots of one index share the switch. True for any other slot and when the lookup fails.
     */
    public static boolean accessoryShown(Slot slot) {
        if (!accessorySlot(slot)) return true;
        try {
            return shouldRender(field(slot.getClass(), "accessoriesContainer").get(slot), slot.getContainerSlot());
        } catch (ReflectiveOperationException | LinkageError ex) {
            return true;
        }
    }

    private static boolean shouldRender(Object container, int index) throws ReflectiveOperationException {
        if (shouldRender == null) {
            shouldRender = type("io.wispforest.accessories.api.AccessoriesContainer")
                .getMethod("shouldRender", int.class);
        }
        return (boolean) shouldRender.invoke(container, index);
    }

    /**
     * Row (0 head .. 3 feet) of a cosmetic armour slot of Accessories' experimental menu, or -1. Accessories draws the
     * item there instead of the worn armour, and a hidden one hides that armour piece.
     */
    public static int cosmeticArmor(Slot slot) {
        if (accessoryItemSlot(slot) || !accessorySlot(slot) || !cosmeticSlot(slot)) return -1;
        try {
            return armorNames().indexOf((String) method(slot.getClass(), "slotName").invoke(slot));
        } catch (ReflectiveOperationException ex) {
            return -1;
        }
    }

    /**
     * Names of Accessories' armour slot types, head to feet as the equipment window shows them. They are unique slot
     * types registered at startup, so the names are read from {@code ArmorSlotTypes} rather than spelled out here.
     * Empty while that fails; the lookup is retried.
     */
    private static List<String> armorNames() {
        if (armorNames != null) return armorNames;
        try {
            Class<?> types = type("io.wispforest.accessories.menu.ArmorSlotTypes");
            var names = new ArrayList<String>();
            for (String reference : ARMOR_SLOT_TYPES) {
                Object ref = method(types, reference).invoke(null);
                names.add((String) method(ref.getClass(), "slotName").invoke(ref));
            }
            armorNames = List.copyOf(names);
            return armorNames;
        } catch (ReflectiveOperationException | LinkageError | NullPointerException ex) {
            return List.of();
        }
    }

    public static boolean originalAccessories(AbstractContainerMenu menu) {
        return menu.getClass().getName().endsWith(".AccessoriesMenu");
    }

    public static boolean accessoriesCosmetics(AbstractContainerMenu menu) {
        try {
            return (boolean) method(menu.getClass(), "isCosmeticsOpen").invoke(menu);
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    /** Scrolls the classic menu to {@code index}; the server answers with the same scroll for the client's menu. */
    public static void accessoryScroll(int index) {
        try {
            Object payload = Class.forName("io.wispforest.accessories.networking.server.MenuScroll")
                .getConstructor(int.class, boolean.class).newInstance(index, false);
            accessoryPacket(payload);
        } catch (ReflectiveOperationException ex) {
            fail("Accessories scrolling", ex);
        }
    }

    public static void accessoryCosmetics(AbstractContainerMenu menu) {
        try {
            Class<?> prop = Class.forName("io.wispforest.accessories.networking.holder.HolderProperty");
            Object payload = Class.forName("io.wispforest.accessories.networking.holder.SyncHolderChange")
                .getConstructor(prop, Object.class)
                .newInstance(prop.getField("COSMETIC_PROP").get(null), !accessoriesCosmetics(menu));
            accessoryPacket(payload);
        } catch (ReflectiveOperationException ex) {
            fail("Accessories cosmetics", ex);
        }
    }

    public static void accessoryRender(Slot slot) {
        try {
            String name = (String) method(slot.getClass(), "slotName").invoke(slot);
            Object payload = Class.forName("io.wispforest.accessories.networking.server.SyncCosmeticToggle")
                .getConstructor(Integer.class, String.class, int.class)
                .newInstance(null, name, slot.getContainerSlot());
            accessoryPacket(payload);
        } catch (ReflectiveOperationException ex) {
            fail("Accessories render", ex);
        }
    }

    public static boolean changeCuriosPage(AbstractContainerMenu menu, boolean next) {
        return packet("CPacketPage", new Class<?>[]{int.class, boolean.class}, menu.containerId, next);
    }

    public static boolean cosmetics(AbstractContainerMenu menu) {
        try {
            menu.getClass().getMethod("toggleCosmetics").invoke(menu);
        } catch (ReflectiveOperationException ex) {
            fail("Curios cosmetics", ex);
            return false;
        }
        return packet("CPacketToggleCosmetics", new Class<?>[]{int.class}, menu.containerId);
    }

    public static void toggleRender(Slot slot) {
        try {
            if (!(boolean) slot.getClass().getMethod("canToggleRender").invoke(slot)) return;
            String id = (String) slot.getClass().getMethod("getIdentifier").invoke(slot);
            packet("CPacketToggleRender", new Class<?>[]{String.class, int.class}, id, slot.getContainerSlot());
        } catch (ReflectiveOperationException | LinkageError ex) {
            fail("Curios render toggle", ex);
        }
    }

    private static boolean packet(String type, Class<?>[] signature, Object... args) {
        if (!loaded("curios")) return false;
        try {
            Object payload = Class.forName(CURIOS_PACKETS + type).getConstructor(signature).newInstance(args);
            PacketDistributor.sendToServer((CustomPacketPayload) payload);
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            fail("Curios", ex);
            return false;
        }
    }

    public static int integer(AbstractContainerMenu menu, String field, int fallback) {
        try {
            return field(menu.getClass(), field).getInt(menu);
        } catch (ReflectiveOperationException ex) {
            return fallback;
        }
    }

    public static boolean flag(AbstractContainerMenu menu, String field) {
        try {
            return field(menu.getClass(), field).getBoolean(menu);
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public static void tick(AbstractContainerMenu menu) {
        if (kind(menu) == Kind.CURIOS) try {
            method(menu.getClass(), "checkQuickMove").invoke(menu);
        } catch (ReflectiveOperationException ex) {
            fail("Curios quick move", ex);
        }
    }

    /** {@link Class#forName}, remembered together with a failure. */
    private static Class<?> type(String name) throws ClassNotFoundException {
        Object found = CLASSES.computeIfAbsent(name, key -> {
            try {
                return Class.forName(key);
            } catch (ClassNotFoundException ex) {
                return ex;
            }
        });
        if (found instanceof ClassNotFoundException missing) throw missing;
        return (Class<?>) found;
    }

    /** {@link Class#getMethod} without parameters, remembered together with a failure. */
    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        Object found = METHODS.computeIfAbsent(type, key -> new HashMap<>()).computeIfAbsent(name, key -> {
            try {
                return type.getMethod(key);
            } catch (NoSuchMethodException ex) {
                return ex;
            }
        });
        if (found instanceof NoSuchMethodException missing) throw missing;
        return (Method) found;
    }

    /** {@link Class#getField}, remembered together with a failure. */
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Object found = FIELDS.computeIfAbsent(type, key -> new HashMap<>()).computeIfAbsent(name, key -> {
            try {
                return type.getField(key);
            } catch (NoSuchFieldException ex) {
                return ex;
            }
        });
        if (found instanceof NoSuchFieldException missing) throw missing;
        return (Field) found;
    }

    private static void fail(String integration, Throwable ex) {
        org.slf4j.LoggerFactory.getLogger("Civitatum HUD")
            .warn("{} integration unavailable: {}", integration, ex.toString());
    }
}
