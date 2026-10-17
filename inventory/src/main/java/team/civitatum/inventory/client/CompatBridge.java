package team.civitatum.inventory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/** Optional integrations never invent slots or alter the server's menu layout. */
public final class CompatBridge {
    public enum Kind { INVENTORY, CURIOS, BACKPACK, ACCESSORIES, AETHER, BLOCK }
    private static final java.util.Set<AbstractContainerMenu> enderpacks=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static long backpackRequestUntil;
    public static boolean loaded(String id) { return ModList.get().isLoaded(id); }
    public static Kind kind(AbstractContainerMenu menu) {
        String name=menu.getClass().getName();
        if(name.equals("top.theillusivec4.curios.common.inventory.container.CuriosContainer")) return Kind.CURIOS;
        if(name.startsWith("io.wispforest.accessories.menu.variants.Accessories")) return Kind.ACCESSORIES;
        if(name.startsWith("net.satisfy.camping.") && name.endsWith("BackpackScreenHandler")) return Kind.BACKPACK;
        if(enderpacks.contains(menu))return Kind.BACKPACK;
        if(name.equals("com.aetherteam.aether.inventory.menu.AetherAccessoriesMenu"))return Kind.AETHER;
        if(blockMenu(menu))return Kind.BLOCK;
        return Kind.INVENTORY;
    }
    public static boolean supported(Screen screen) {
        if(!(screen instanceof AbstractContainerScreen<?> container)) return false;
        if(kind(container.getMenu())==Kind.ACCESSORIES)try {
            Object target=container.getMenu().getClass().getMethod("targetEntity").invoke(container.getMenu());
            if(target!=null&&target!=Minecraft.getInstance().player)return false;
        }catch(ReflectiveOperationException ex){return false;}
        if(container.getMenu() instanceof net.minecraft.world.inventory.ChestMenu && backpackRequestUntil>System.currentTimeMillis()
                && screen.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().equals("container.camping.enderpack"))enderpacks.add(container.getMenu());
        return kind(container.getMenu())!=Kind.INVENTORY;
    }
    public static boolean blockMenu(AbstractContainerMenu menu) {
        String n=menu.getClass().getName();
        return menu instanceof net.minecraft.world.inventory.ChestMenu || menu instanceof net.minecraft.world.inventory.HopperMenu
            || menu instanceof net.minecraft.world.inventory.DispenserMenu || menu instanceof net.minecraft.world.inventory.ShulkerBoxMenu
            || menu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu || menu instanceof net.minecraft.world.inventory.BrewingStandMenu
            || menu instanceof net.minecraft.world.inventory.CraftingMenu || n.equals("com.aetherteam.aether.inventory.menu.AltarMenu")
            || n.equals("com.aetherteam.aether.inventory.menu.FreezerMenu") || n.equals("com.aetherteam.aether.inventory.menu.IncubatorMenu");
    }
    public static boolean storageGrid(AbstractContainerMenu menu) {
        return menu instanceof net.minecraft.world.inventory.ChestMenu || menu instanceof net.minecraft.world.inventory.HopperMenu
            || menu instanceof net.minecraft.world.inventory.DispenserMenu || menu instanceof net.minecraft.world.inventory.ShulkerBoxMenu;
    }
    public static boolean openAether() {
        if(!loaded("aether"))return false;
        try {
            Object payload=Class.forName("com.aetherteam.aether.network.packet.serverbound.OpenAccessoriesPacket")
                .getConstructor(ItemStack.class).newInstance(ItemStack.EMPTY);
            PacketDistributor.sendToServer((CustomPacketPayload)payload);return true;
        }catch(ReflectiveOperationException|LinkageError ex){fail("Aether",ex);return false;}
    }
    public static double progress(AbstractContainerMenu menu,String method,double divisor) {
        try {return Math.max(0,Math.min(1,((Number)menu.getClass().getMethod(method).invoke(menu)).doubleValue()/divisor));}
        catch(ReflectiveOperationException ex){return 0;}
    }
    public static ItemStack backpack(Player player) {
        if(!loaded("camping")) return ItemStack.EMPTY;
        try {
            Object platform=Class.forName("net.satisfy.camping.platform.Services").getField("PLATFORM").get(null);
            return (ItemStack)platform.getClass().getMethod("getEquippedBackpack",Player.class).invoke(platform,player);
        } catch(ReflectiveOperationException|LinkageError ex) { return ItemStack.EMPTY; }
    }
    public static boolean openBackpack() {
        if(!loaded("camping")) return false;
        try {
            Object payload=Class.forName("net.satisfy.camping.core.network.packet.NeoForgeOpenBackpackC2SPacket").getField("INSTANCE").get(null);
            backpackRequestUntil=System.currentTimeMillis()+5000;
            PacketDistributor.sendToServer((CustomPacketPayload)payload);return true;
        } catch(ReflectiveOperationException|LinkageError ex) { fail("Camping",ex);return false; }
    }
    public static boolean openCurios() {
        return packet("CPacketOpenCurios",new Class<?>[]{ItemStack.class},ItemStack.EMPTY);
    }
    public static boolean openAccessories() {
        return openAccessories("EXPERIMENTAL_V1");
    }
    public static boolean openAccessories(String layout) {
        if(!loaded("accessories"))return false;
        try {
            Class<?> variantClass=Class.forName("io.wispforest.accessories.menu.AccessoriesMenuVariant");
            Object variant=variantClass.getField(layout).get(null);
            Object payload=Class.forName("io.wispforest.accessories.networking.server.ScreenOpen")
                .getMethod("of",boolean.class,variantClass).invoke(null,false,variant);
            return accessoryPacket(payload);
        }catch(ReflectiveOperationException|LinkageError ex){fail("Accessories",ex);return false;}
    }
    private static boolean accessoryPacket(Object payload) {
        try {Class.forName("io.wispforest.accessories.networking.AccessoriesNetworking").getMethod("sendToServer",Record.class).invoke(null,payload);return true;}
        catch(ReflectiveOperationException|LinkageError ex){fail("Accessories network",ex);return false;}
    }
    public static boolean cosmeticSlot(net.minecraft.world.inventory.Slot slot) {
        try {return slot.getClass().getField("isCosmetic").getBoolean(slot);}catch(ReflectiveOperationException ex){return false;}
    }
    public static boolean accessorySlot(net.minecraft.world.inventory.Slot slot) {
        try {slot.getClass().getMethod("isCosmeticSlot");return true;}catch(ReflectiveOperationException ex){return false;}
    }
    public static net.minecraft.network.chat.Component slotLabel(net.minecraft.world.inventory.Slot slot) {
        try {
            if(slot.getClass().getName().equals("top.theillusivec4.curios.common.inventory.CurioSlot"))
                return net.minecraft.network.chat.Component.translatable("curios.identifier."+slot.getClass().getMethod("getIdentifier").invoke(slot));
            if(accessorySlot(slot)) {
                Object type=slot.getClass().getMethod("slotType").invoke(slot);
                return net.minecraft.network.chat.Component.translatable((String)type.getClass().getMethod("translation").invoke(type));
            }
        }catch(ReflectiveOperationException ex){return net.minecraft.network.chat.Component.literal(slot.getClass().getSimpleName());}
        return null;
    }
    public static int accessoryArmor(net.minecraft.world.inventory.Slot slot) {
        if(!slot.getClass().getName().equals("io.wispforest.accessories.menu.AccessoriesArmorSlot"))return -1;
        try {
            String name=(String)slot.getClass().getMethod("slotName").invoke(slot);
            Class<?> types=Class.forName("io.wispforest.accessories.menu.ArmorSlotTypes");
            String[] methods={"headSlot","chestSlot","legsSlot","feetSlot"};
            for(int i=0;i<methods.length;i++){Object ref=types.getMethod(methods[i]).invoke(null);if(name.equals(ref.getClass().getMethod("slotName").invoke(ref)))return i;}
        }catch(ReflectiveOperationException ex){fail("Accessories armor",ex);}
        return -1;
    }
    public static boolean originalAccessories(AbstractContainerMenu menu){return menu.getClass().getName().endsWith(".AccessoriesMenu");}
    public static boolean accessoriesCosmetics(AbstractContainerMenu menu){
        try{return (boolean)menu.getClass().getMethod("isCosmeticsOpen").invoke(menu);}catch(ReflectiveOperationException ex){return false;}
    }
    public static void accessoryPage(AbstractContainerMenu menu,boolean next){
        try {Object payload=Class.forName("io.wispforest.accessories.networking.server.MenuScroll").getConstructor(int.class,boolean.class)
            .newInstance(integer(menu,"scrolledIndex",0)+(next?8:-8),false);accessoryPacket(payload);}
        catch(ReflectiveOperationException ex){fail("Accessories scrolling",ex);}
    }
    public static void accessoryCosmetics(AbstractContainerMenu menu){
        try {Class<?> prop=Class.forName("io.wispforest.accessories.networking.holder.HolderProperty");
            Object payload=Class.forName("io.wispforest.accessories.networking.holder.SyncHolderChange")
                .getConstructor(prop,Object.class).newInstance(prop.getField("COSMETIC_PROP").get(null),!accessoriesCosmetics(menu));accessoryPacket(payload);}
        catch(ReflectiveOperationException ex){fail("Accessories cosmetics",ex);}
    }
    public static void accessoryRender(net.minecraft.world.inventory.Slot slot){
        try {String name=(String)slot.getClass().getMethod("slotName").invoke(slot);
            Object payload=Class.forName("io.wispforest.accessories.networking.server.SyncCosmeticToggle").getConstructor(Integer.class,String.class,int.class)
                .newInstance(null,name,slot.getContainerSlot());accessoryPacket(payload);}
        catch(ReflectiveOperationException ex){fail("Accessories render",ex);}
    }
    public static boolean changeCuriosPage(AbstractContainerMenu menu,boolean next) {
        return packet("CPacketPage",new Class<?>[]{int.class,boolean.class},menu.containerId,next);
    }
    public static boolean cosmetics(AbstractContainerMenu menu) {
        try {menu.getClass().getMethod("toggleCosmetics").invoke(menu);}
        catch(ReflectiveOperationException ex){fail("Curios cosmetics",ex);return false;}
        return packet("CPacketToggleCosmetics",new Class<?>[]{int.class},menu.containerId);
    }
    public static void toggleRender(net.minecraft.world.inventory.Slot slot) {
        try {
            if(!(boolean)slot.getClass().getMethod("canToggleRender").invoke(slot))return;
            String id=(String)slot.getClass().getMethod("getIdentifier").invoke(slot);
            packet("CPacketToggleRender",new Class<?>[]{String.class,int.class},id,slot.getContainerSlot());
        } catch(ReflectiveOperationException|LinkageError ex) {fail("Curios render toggle",ex);}
    }
    private static boolean packet(String type,Class<?>[] signature,Object... args) {
        if(!loaded("curios")) return false;
        try {
            Object payload=Class.forName("top.theillusivec4.curios.common.network.client."+type).getConstructor(signature).newInstance(args);
            PacketDistributor.sendToServer((CustomPacketPayload)payload);return true;
        } catch(ReflectiveOperationException|LinkageError ex) {fail("Curios",ex);return false;}
    }
    public static int integer(AbstractContainerMenu menu,String field,int fallback) {
        try {return menu.getClass().getField(field).getInt(menu);}catch(ReflectiveOperationException ex){return fallback;}
    }
    public static boolean flag(AbstractContainerMenu menu,String field) {
        try {return menu.getClass().getField(field).getBoolean(menu);}catch(ReflectiveOperationException ex){return false;}
    }
    public static void tick(AbstractContainerMenu menu) {
        if(kind(menu)==Kind.CURIOS) try {menu.getClass().getMethod("checkQuickMove").invoke(menu);}
        catch(ReflectiveOperationException ex){fail("Curios quick move",ex);}
    }
    private static void fail(String integration,Throwable ex) {
        org.slf4j.LoggerFactory.getLogger("Civitatum Inventory").warn("{} integration unavailable: {}",integration,ex.toString());
    }
}
