package ru.teacraft.inventory.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.teacraft.inventory.client.*;

@Mod(value="inventory_smoke",dist=Dist.CLIENT)
public final class InventorySmoke {
    private int phase,ticks,total;
    private int originalX,originalY;
    private CompatibilitySmoke compatibility;
    private RevisionSmoke revision;
    private UpdateSmoke update;
    public InventorySmoke() {NeoForge.EVENT_BUS.addListener(this::tick);}
    private void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();ticks++;total++;
        if(total%200==0) System.out.println("INVENTORY_SMOKE_PHASE "+phase+" "+(mc.screen==null?"world":mc.screen.getClass().getName()));
        if(total>10000) throw new IllegalStateException("Inventory smoke timed out at phase "+phase);
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());
        }
        if(phase==0 && mc.screen instanceof TitleScreen && ticks>50) {
            next(1);CreateWorldScreen.openFresh(mc,mc.screen);
        } else if(phase==1 && mc.screen instanceof CreateWorldScreen screen && ticks>40) {
            next(2);
            try {var method=CreateWorldScreen.class.getDeclaredMethod("onCreate");method.setAccessible(true);method.invoke(screen);}
            catch(ReflectiveOperationException ex) {throw new IllegalStateException(ex);}
        } else if(phase==2 && mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirmation && ticks>15) {
            ((net.minecraft.client.gui.components.Button)confirmation.children().getFirst()).onPress();
        } else if(phase==2 && mc.player!=null && mc.screen==null) {
            mc.options.guiScale().set(2);mc.resizeDisplay();
            mc.options.languageCode="ru_ru";mc.getLanguageManager().setSelected("ru_ru");mc.reloadResourcePacks();
            InventoryPreferences.get().enabled=true;InventoryPreferences.get().panels.clear();InventoryPreferences.get().slotScale=1;
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getAbilities().invulnerable=true;
                Item[] entries={Items.COBBLESTONE,Items.APPLE,Items.DIAMOND_SWORD,Items.IRON_CHESTPLATE,Items.OAK_LOG,Items.DIAMOND,Items.ARROW,Items.BOW};
                int[] counts={32,16,1,1,16,4,32,1};
                for(int i=0;i<entries.length;i++) p.getInventory().setItem(9+i,new ItemStack(entries[i],counts[i]));
                p.getInventory().setItem(0,new ItemStack(Items.TORCH,64));
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,12000,0,false,false));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,12000,0,false,false));
                if(net.neoforged.fml.ModList.get().isLoaded("civitatum")) setupRace(p);
                p.inventoryMenu.broadcastChanges();
            });next(3);
        } else if(phase==3 && ticks>60) {
            mc.setScreen(new InventoryScreen(mc.player));next(4);
        } else if(phase==4 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>40) {
            if(Boolean.getBoolean("inventory.smoke.compatOnly")){next(29);return;}
            check(screen.getMenu().slots.get(9).getItem().getCount()==32,"server inventory seeded");
            var first=screen.getMenu().slots.get(9);var second=screen.getMenu().slots.get(10);
            var third=screen.getMenu().slots.get(11);var fourth=screen.getMenu().slots.get(12);
            check(first.x==second.x&&second.x==third.x&&first.y<second.y&&second.y<third.y,"inventory fills the first column vertically");
            check(fourth.x>first.x&&fourth.y==first.y,"fourth inventory slot starts the next column");
            for(var slot:screen.getMenu().slots)if(slot.container==mc.player.getInventory()&&slot.getContainerSlot()>=9&&slot.getContainerSlot()<36)
                check(screen.pickSlot(slot.x+8,slot.y+8)==slot,"vertical grid hit testing uses real slot "+slot.getContainerSlot());
            capture(mc,"01-inventory.png");
            var p=screen.itemsPanel();
            screen.mouseClicked(p.x+20,p.y+56,0);screen.mouseReleased(p.x+20,p.y+56,0);
            for(char c:"diamond".toCharArray()) screen.charTyped(c,0);
            check(screen.matches(new ItemStack(Items.DIAMOND_SWORD)),"search matches diamond sword");
            check(!screen.matches(new ItemStack(Items.APPLE)),"search excludes apple");next(5);
        } else if(phase==5 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>30) {
            capture(mc,"02-search.png");
            screen.keyPressed(256,0,0);
            var slot=screen.getMenu().slots.get(9);click(screen,slot.x+8,slot.y+8,0);next(6);
        } else if(phase==6 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>15) {
            check(screen.getMenu().getCarried().getCount()==32,"left click picks up stack");
            var slot=screen.getMenu().slots.get(17);click(screen,slot.x+8,slot.y+8,1);next(7);
        } else if(phase==7 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>15) {
            check(screen.getMenu().slots.get(17).getItem().getCount()==1,"right click places one item");
            check(screen.getMenu().getCarried().getCount()==31,"remaining stack is correct");
            var slot=screen.getMenu().slots.get(18);click(screen,slot.x+8,slot.y+8,0);next(8);
        } else if(phase==8 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>15) {
            check(screen.getMenu().getCarried().isEmpty(),"carried stack deposited");
            check(screen.getMenu().slots.get(18).getItem().getCount()==31,"deposited stack synchronized");
            mc.gameMode.handleInventoryMouseClick(0,12,0,ClickType.QUICK_MOVE,mc.player);next(9);
        } else if(phase==9 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>20) {
            check(screen.getMenu().slots.get(6).getItem().is(Items.IRON_CHESTPLATE),"quick move equips armor");
            var panel=screen.equipmentPanel();originalX=panel.x;originalY=panel.y;
            screen.mouseClicked(panel.x+12,panel.y+8,0);
            screen.mouseDragged(panel.x+12,panel.y-12,0,0,-20);
            screen.mouseReleased(panel.x+12,panel.y+8,0);
            check(panel.y<originalY,"window drag works");
            check(screen.itemsPanel().y==panel.y&&panel.x+panel.width==screen.itemsPanel().x,"equipment stays docked while dragging");originalY=panel.y;originalX=panel.x;
            screen.onClose();next(10);
        } else if(phase==10 && ticks>20) {
            check(mc.player.inventoryMenu.slots.get(9).x==8 && mc.player.inventoryMenu.slots.get(9).y==84,"vanilla coordinates restored");
            mc.setScreen(new InventoryScreen(mc.player));next(11);
        } else if(phase==11 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>20) {
            check(screen.equipmentPanel().x==originalX && screen.equipmentPanel().y==originalY,"window layout persists");
            InventoryClient.vanilla();check(mc.screen.getClass()==InventoryScreen.class,"vanilla fallback opens");next(12);
        } else if(phase==12 && ticks>20) {
            mc.setScreen(new InventoryScreen(mc.player));mc.options.guiScale().set(3);mc.resizeDisplay();next(13);
        } else if(phase==13 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>40) {
            capture(mc,"03-compact.png");checkBounds(screen);
            click(screen,35,12,0);next(14);
        } else if(phase==14 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>30) {
            check(screen.informationPanel().visible && !screen.equipmentPanel().visible,"compact information toggle");
            capture(mc,"04-compact-information.png");
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,960);
            mc.options.guiScale().set(4);mc.resizeDisplay();next(15);
        } else if(phase==15 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>40) {
            mc.options.guiScale().set(4);mc.resizeDisplay();next(150);
        } else if(phase==150 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>30) {
            check(screen.width==320 && screen.height==240,"minimum GUI resolution 320x240");
            checkBounds(screen);capture(mc,"05-small.png");
            InventoryPreferences.get().slotScale=2;screen.containerTick();
            var first=screen.getMenu().slots.get(9);var second=screen.getMenu().slots.get(10);int fx=first.x,fy=first.y;
            check(first.x>=0&&second.x<0,"large vertical slots clip rows beyond the viewport");
            screen.mouseScrolled(fx+8,fy+8,0,-1);
            check(second.x==fx&&second.y==fy&&first.x<0,"scroll reveals the next slot in the same column");
            screen.mouseScrolled(fx+8,fy+8,0,1);InventoryPreferences.get().slotScale=1;screen.containerTick();
            screen.equipmentPanel().x=screen.itemsPanel().x+4;
            click(screen,35,12,0);checkBounds(screen);
            check(screen.equipmentPanel().visible,"equipment remains accessible at minimum GUI scale");
            check(screen.equipmentPanel().x+screen.equipmentPanel().width<=screen.itemsPanel().x,"switching compact panels repairs overlapping layout");
            screen.onClose();mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new InventoryScreen(mc.player));next(16);
        } else if(phase==16 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>30) {
            // Verify vanilla crafting uses the same server slots and result.
            mc.gameMode.handleInventoryMouseClick(0,13,0,ClickType.PICKUP,mc.player);
            next(17);
        } else if(phase==17 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>15) {
            mc.gameMode.handleInventoryMouseClick(0,1,0,ClickType.PICKUP,mc.player);next(18);
        } else if(phase==18 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>20) {
            check(screen.getMenu().slots.get(0).getItem().is(Items.OAK_PLANKS),"crafting result synchronized");
            var out=screen.getMenu().slots.get(0);click(screen,out.x+8,out.y+8,0);next(19);
        } else if(phase==19 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>15) {
            check(screen.getMenu().getCarried().is(Items.OAK_PLANKS)&&screen.getMenu().getCarried().getCount()==4,"crafting output pickup");
            var slot=screen.getMenu().slots.get(19);click(screen,slot.x+8,slot.y+8,0);next(20);
        } else if(phase==20 && ticks>20) {
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                check(p.getInventory().getItem(17).getCount()==1 && p.getInventory().getItem(18).getCount()==31,"server confirms item counts");
                check(p.inventoryMenu.slots.get(6).getItem().is(Items.IRON_CHESTPLATE),"server confirms armor");
                check(p.getInventory().getItem(19).is(Items.OAK_PLANKS),"server confirms crafted items");
            });next(21);
        } else if(phase==21 && ticks>30) {
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                p.getInventory().setItem(20,new ItemStack(Items.STONE,12));
                p.inventoryMenu.broadcastChanges();
            });next(22);
        } else if(phase==22 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>20) {
            var slot=screen.getMenu().slots.get(20);click(screen,slot.x+8,slot.y+8,0);next(23);
        } else if(phase==23 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>20) {
            check(screen.getMenu().getCarried().getCount()==12,"drag distribution source");
            var slot=screen.getMenu().slots.get(21);screen.mouseClicked(slot.x+8,slot.y+8,0);
            for(int i=21;i<=23;i++) {var target=screen.getMenu().slots.get(i);screen.mouseDragged(target.x+8,target.y+8,0,18,0);}
            var target=screen.getMenu().slots.get(23);screen.mouseReleased(target.x+8,target.y+8,0);next(24);
        } else if(phase==24 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>20) {
            for(int i=21;i<=23;i++)check(screen.getMenu().slots.get(i).getItem().getCount()==4,"drag distributes four items to slot "+i);
            check(screen.getMenu().getCarried().isEmpty(),"drag distribution conserves count");
            var key=new net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre(screen,297,0,0);
            NeoForge.EVENT_BUS.post(key);
            check(key.isCanceled() && mc.screen.getClass()==InventoryScreen.class && !InventoryPreferences.get().enabled,"F8 switches to vanilla");
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,297,0,0));
            check(mc.screen instanceof CivitatumInventoryScreen && InventoryPreferences.get().enabled,"F8 restores custom inventory");next(25);
        } else if(phase==25 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>20) {
            var panel=screen.itemsPanel();click(screen,panel.x+15,panel.y+9,0);int oldHeight=panel.height;
            int x=panel.x+panel.width-4,y=panel.y+panel.height-4;
            screen.mouseClicked(x,y,0);screen.mouseDragged(x,y+12,0,0,12);screen.mouseReleased(x,y+12,0);
            check(panel.height>oldHeight,"window resize works");
            var eq=screen.equipmentPanel();int beforeScale=screen.modelScale();
            int ex=eq.x+eq.width-4,ey=eq.y+eq.height-4;
            screen.mouseClicked(ex,ey,0);screen.mouseDragged(ex+12,ey+22,0,12,22);screen.mouseReleased(ex+12,ey+22,0);
            check(screen.modelScale()>beforeScale,"player model grows with equipment window");
            check(eq.height==screen.itemsPanel().height&&eq.x+eq.width==screen.itemsPanel().x,"resized equipment stays docked");
            if(net.neoforged.fml.ModList.get().isLoaded("civitatum")) {
                var details=RacesBridge.read(mc.player);
                check(details!=null && details.name().equals("Арина") && details.race().equals("Человек"),"race adapter reads synchronized character");
                var info=screen.powersPanel();click(screen,info.x+info.width-20,info.y+9,0);next(26);
            } else next(29);
        } else if(phase==26 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>30) {
            capture(mc,"06-racial-powers.png");var info=screen.powersPanel();
            click(screen,info.x+30,info.y+info.height-21,0);next(27);
        } else if(phase==27 && ticks>25) {
            check(mc.screen==null,"ability button closes inventory");
            check(RacesBridge.read(mc.player).remainingTicks()>0,"ability request reaches server and cooldown synchronizes");
            mc.setScreen(new InventoryScreen(mc.player));next(28);
        } else if(phase==28 && mc.screen instanceof CivitatumInventoryScreen screen && ticks>30) {
            var info=screen.powersPanel();click(screen,info.x+info.width-20,info.y+9,0);next(280);
        } else if(phase==280 && ticks>30) {
            capture(mc,"07-racial-cooldown.png");next(29);
        } else if(phase==29 && ticks>20) {
            mc.getToasts().clear();mc.gui.getChat().clearMessages(true);mc.setScreen(new InventoryScreen(mc.player));next(30);
        } else if(phase==30 && ticks>30) {
            capture(mc,"00-overview.png");
            if(Boolean.getBoolean("inventory.smoke.update")){update=new UpdateSmoke();next(33);}
            else if(Boolean.getBoolean("inventory.smoke.revision")){revision=new RevisionSmoke();next(32);}
            else if(CompatBridge.loaded("camping")||Boolean.getBoolean("inventory.smoke.accessoriesOnly")) {compatibility=new CompatibilitySmoke();next(31);}
            else {System.out.println("INVENTORY_SMOKE_ALL_PASS");mc.stop();}
        } else if(phase==33 && update!=null) {
            if(update.tick(mc)){System.out.println("INVENTORY_SMOKE_ALL_PASS");mc.stop();}
        } else if(phase==32 && revision!=null) {
            if(revision.tick(mc)){System.out.println("INVENTORY_SMOKE_ALL_PASS");mc.stop();}
        } else if(phase==31 && compatibility!=null) {
            if(compatibility.tick(mc)){System.out.println("INVENTORY_SMOKE_ALL_PASS");mc.stop();}
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void setupRace(net.minecraft.server.level.ServerPlayer player) {
        try {
            var character=Class.forName("ru.teacraft.civitatum.RaceCatalog").getMethod("defaults",String.class,String.class).invoke(null,"human","female");
            character.getClass().getField("name").set(character,"Арина");
            var state=Class.forName("ru.teacraft.civitatum.RaceState").getConstructor(character.getClass(),int.class,long.class).newInstance(character,0,0L);
            var type=(net.neoforged.neoforge.attachment.AttachmentType)((java.util.function.Supplier)Class.forName("ru.teacraft.civitatum.Civitatum").getField("STATE").get(null)).get();
            player.setData(type,state);
            Class.forName("ru.teacraft.civitatum.RaceService").getMethod("apply",net.minecraft.server.level.ServerPlayer.class).invoke(null,player);
            Class.forName("ru.teacraft.civitatum.RaceNetwork").getMethod("sync",net.minecraft.server.level.ServerPlayer.class).invoke(null,player);
        } catch(ReflectiveOperationException ex) {throw new IllegalStateException(ex);}
    }
    private void next(int phase) {this.phase=phase;ticks=0;}
    private static void check(boolean condition,String description) {if(!condition)throw new IllegalStateException("INVENTORY FAIL: "+description);System.out.println("INVENTORY PASS: "+description);}
    private static void click(CivitatumInventoryScreen screen,int x,int y,int button) {screen.mouseClicked(x,y,button);screen.mouseReleased(x,y,button);}
    private static void checkBounds(CivitatumInventoryScreen screen) {
        for(var slot:screen.getMenu().slots) if(slot.x>=0)check(slot.x>=0&&slot.y>=24&&slot.x+16<=screen.width&&slot.y+16<screen.height-20,"slot "+slot.index+" inside screen");
    }
    private static void capture(Minecraft mc,String name) {
        var directory=new java.io.File("../build/client-captures");directory.mkdirs();
        net.minecraft.client.Screenshot.grab(directory,name,mc.getMainRenderTarget(),message->System.out.println("INVENTORY_CAPTURE "+message.getString()));
    }
}
