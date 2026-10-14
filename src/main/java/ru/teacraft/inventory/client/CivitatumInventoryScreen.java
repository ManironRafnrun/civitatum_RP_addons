package ru.teacraft.inventory.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import org.lwjgl.glfw.GLFW;
import ru.teacraft.inventory.mixin.SlotPositionAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.IdentityHashMap;

/** Vanilla InventoryMenu and AbstractContainerScreen retain all server-authoritative item operations. */
public final class CivitatumInventoryScreen extends AbstractContainerScreen<AbstractContainerMenu> {
    private static final int TEXT=0xE9E3D4, MUTED=0xABA799, GOLD=0xD3B478;
    private final Player player;
    private final IdentityHashMap<Slot,int[]> original=new IdentityHashMap<>();
    private final Screen nativeScreen;
    private final CompatBridge.Kind kind;
    private InventoryPanel equipment, items, information, container, powers, dragging;
    private final List<InventoryPanel> panels=new ArrayList<>();
    private final IdentityHashMap<Slot,InventoryPanel> owners=new IdentityHashMap<>();
    private final List<AbstractWidget> widgets=new ArrayList<>();
    private final List<Button> categories=new ArrayList<>();
    private final List<Button> storageTabs=new ArrayList<>();
    private List<Slot> extraSlots=List.of();
    private Button previousPage,nextPage,cosmetics,powersButton;
    private int extraRow;
    private boolean extras,preserveMenu,accessoryCosmetic;
    private EditBox search;
    private Button summaryTab, attributeTab, attributeFilter, ability, compactToggle, gearButton;
    private final PowersView powersView=new PowersView();
    private boolean autoBackpack=true, backpackRequested;
    private int playerRow, blockRow, resizeMask, anchorX, anchorY, anchorW, anchorH;
    private boolean modifiedAttributes;
    private int category, page, scroll, dragOffsetX, dragOffsetY;
    private boolean resizing, compact, compactInfo;
    private String query="";

    public CivitatumInventoryScreen(Player player) {
        this(player,player.inventoryMenu,new InventoryScreen(player));
    }
    public CivitatumInventoryScreen(Player player,AbstractContainerMenu menu,Screen nativeScreen) {
        super(menu,player.getInventory(),tr("title"));
        this.player=player;this.nativeScreen=nativeScreen;this.kind=CompatBridge.kind(menu);
        extras=kind==CompatBridge.Kind.CURIOS || kind==CompatBridge.Kind.ACCESSORIES;
        var previous=net.minecraft.client.Minecraft.getInstance().screen;
        for(var slot:menu.slots) original.put(slot,previous instanceof CivitatumInventoryScreen other && other.menu==menu && other.original.containsKey(slot)
            ? other.original.get(slot).clone() : new int[]{slot.x,slot.y});
    }
    public static Component tr(String key,Object... args) { return Component.translatable("gui.civitatum_inventory."+key,args); }
    @Override protected void init() {
        super.init();leftPos=0;topPos=0;imageWidth=width;imageHeight=height;
        compact=width<590;preserveMenu=false;
        panels.clear();categories.clear();storageTabs.clear();widgets.clear();
        int start=compact?4:Math.max(6,(width-584)/2), y=compact?28:32;
        int h=Math.min(kind==CompatBridge.Kind.BACKPACK||kind==CompatBridge.Kind.AETHER?302:236,height-54);
        int ew=compact?Math.max(112,Math.min(142,width-212)):146;
        equipment=new InventoryPanel("equipment",start,y,ew,h,112,kind==CompatBridge.Kind.BACKPACK?171:164);
        items=new InventoryPanel("items",start+ew,y,compact?Math.min(196,width-ew-8):214,h,186,kind==CompatBridge.Kind.BACKPACK?171:164);
        information=new InventoryPanel("information",compact?start:items.x+items.width+8,y,compact?ew:208,Math.min(190,height-54),112,128);
        int minWorkWidth=104,minWorkHeight=104;
        if(kind==CompatBridge.Kind.BLOCK&&!CompatBridge.storageGrid(menu)){
            int minX=10000,maxX=0,minY=10000,maxY=0;
            for(var slot:menu.slots)if(slot.container!=player.getInventory()){int[] xy=original.get(slot);minX=Math.min(minX,xy[0]);maxX=Math.max(maxX,xy[0]+16);minY=Math.min(minY,xy[1]);maxY=Math.max(maxY,xy[1]+16);}
            minWorkWidth=Math.max(minWorkWidth,maxX-minX+40);minWorkHeight=Math.max(minWorkHeight,maxY-minY+55);
        }
        container=new InventoryPanel("container",compact?4:items.x+items.width+8,compact?48:y,compact?width-8:208,Math.min(172,height-54),minWorkWidth,minWorkHeight);
        information.height=Math.min(164,height-70);
        powers=new InventoryPanel("powers",compact?4:information.x,compact?48:y+information.height+8,compact?width-8:208,Math.min(180,height-70),160,144);
        if(kind==CompatBridge.Kind.BLOCK){container.height=CompatBridge.storageGrid(menu)?Math.min(240,52+((menu.slots.size()-36+8)/9)*18):148;}
        panels.add(equipment);panels.add(items);panels.add(information);panels.add(powers);panels.add(container);
        for(var panel:panels){panel.restore(InventoryPreferences.get().panels.get(layoutKey(panel)),width,height);panel.constrain(width,height);}
        if(kind==CompatBridge.Kind.BLOCK&&!CompatBridge.storageGrid(menu)){
            int minX=10000,maxX=0,minY=10000,maxY=0;
            for(var slot:menu.slots)if(slot.container!=player.getInventory()){int[] xy=original.get(slot);minX=Math.min(minX,xy[0]);maxX=Math.max(maxX,xy[0]+16);minY=Math.min(minY,xy[1]);maxY=Math.max(maxY,xy[1]+16);}
            container.width=Math.max(container.width,maxX-minX+40);container.height=Math.max(container.height,maxY-minY+55);container.constrain(width,height);
        }
        if(kind==CompatBridge.Kind.BLOCK&&!compact)information.y=Math.min(height-information.height-44,container.y+container.height+8);
        updateVisibility();
        search=addWidget(new EditBox(font,0,0,100,18,tr("search")));
        search.setBordered(false);search.setTextColor(TEXT);search.setTextColorUneditable(MUTED);
        search.setMaxLength(80);search.setHint(tr("search"));search.setValue(query);search.setResponder(v->query=v);
        for(int i=0;i<6;i++){final int c=i;categories.add(button(tr("category.short."+i),b->category=c,27));}
        storageTabs.add(button(tr("storage.inventory"),b->{extras=false;extraRow=0;positionSlots();},54));
        if(CompatBridge.loaded("curios"))storageTabs.add(button(Component.literal("Curios"),b->{if(kind==CompatBridge.Kind.CURIOS){extras=true;positionSlots();}else CompatBridge.openCurios();},54));
        if(CompatBridge.loaded("accessories"))storageTabs.add(button(Component.literal("Accessories"),b->{if(kind==CompatBridge.Kind.ACCESSORIES){extras=true;positionSlots();}else CompatBridge.openAccessories();},54));
        if(CompatBridge.loaded("aether"))storageTabs.add(button(Component.literal("Aether"),b->CompatBridge.openAether(),54));
        gearButton=button(tr(kind==CompatBridge.Kind.BACKPACK?"equipment":"storage.backpack"),b->{if(kind==CompatBridge.Kind.BACKPACK)InventoryClient.personalInventory(true);else CompatBridge.openBackpack();},90);
        summaryTab=button(tr("statistics"),b->{page=0;scroll=0;},70);

        attributeTab=button(tr("attributes.short"),b->{page=2;scroll=0;},50);
        attributeFilter=button(tr("attributes.all"),b->{modifiedAttributes=!modifiedAttributes;scroll=0;},100);
        ability=button(tr("activate"),b->{AbilitySelection.activate(player);onClose();},100);
        previousPage=button(Component.literal("<"),b->{if(kind==CompatBridge.Kind.ACCESSORIES)CompatBridge.accessoryPage(menu,false);else CompatBridge.changeCuriosPage(menu,false);},16);
        nextPage=button(Component.literal(">"),b->{if(kind==CompatBridge.Kind.ACCESSORIES)CompatBridge.accessoryPage(menu,true);else CompatBridge.changeCuriosPage(menu,true);},16);
        cosmetics=button(tr("cosmetics"),b->{if(kind==CompatBridge.Kind.ACCESSORIES){if(CompatBridge.originalAccessories(menu))CompatBridge.accessoryCosmetics(menu);else{accessoryCosmetic=!accessoryCosmetic;extraRow=0;}}else CompatBridge.cosmetics(menu);},64);
        var reset=button(tr("reset"),b->resetLayout(),66);reset.setPosition(width-72,4);
        var normal=button(tr("vanilla"),b->openNative(),72);normal.setPosition(width-148,4);
        powersButton=button(tr("powers"),b->{powers.visible=!powers.visible;InventoryPreferences.get().powersVisible=powers.visible;InventoryPreferences.get().save();if(powers.visible)raise(powers);},54);powersButton.setPosition(width-208,4);
        var opacity=addWidget(new InventorySlider("transparency",InventoryPreferences.get().transparency,0,0.9,v->InventoryPreferences.get().transparency=v));opacity.setPosition(width-162,height-20);widgets.add(opacity);
        var scale=addWidget(new InventorySlider("slot_scale",InventoryPreferences.get().slotScale,1,2,v->{InventoryPreferences.get().slotScale=v;positionSlots();}));scale.setPosition(width-162,height-40);widgets.add(scale);
        compactToggle=compact?button(tr(compactInfo?"equipment":"information"),b->{compactInfo=!compactInfo;updateVisibility();positionControls();positionSlots();b.setMessage(tr(compactInfo?"equipment":"information"));},82):null;
        if(compactToggle!=null)compactToggle.setPosition(6,4);
        positionControls();positionSlots();
    }
    private Button button(Component label,Button.OnPress action,int w) {var b=addWidget(new MorrowindButton(label,action,w));widgets.add(b);return b;}
    private String layoutKey(InventoryPanel panel) {return "v4."+(compact?"compact.":"wide.")+(kind==CompatBridge.Kind.BACKPACK?"joint.":kind==CompatBridge.Kind.BLOCK?"block."+menu.getClass().getSimpleName()+".":kind==CompatBridge.Kind.AETHER?"aether.":"")+panel.id;}
    private void updateVisibility() {
        equipment.visible=!compact||!compactInfo;information.visible=!compact||compactInfo;
        container.visible=kind==CompatBridge.Kind.BLOCK;
        powers.visible=InventoryPreferences.get().powersVisible&&!compact&&kind!=CompatBridge.Kind.BLOCK;
        dockEquipment();
    }
    private void dockEquipment() {
        equipment.width=Math.max(equipment.minWidth,Math.min(equipment.width,width-items.width-8));
        items.x=Math.max(4+equipment.width,Math.min(items.x,width-items.width-4));
        equipment.x=items.x-equipment.width;equipment.y=items.y;equipment.height=items.height;
    }
    private void raise(InventoryPanel panel) {
        if(panel==equipment||panel==items){panels.remove(equipment);panels.remove(items);panels.add(equipment);panels.add(items);}
        else {panels.remove(panel);panels.add(panel);}
    }
    private InventoryPanel topPane(double mx,double my) {for(int i=panels.size()-1;i>=0;i--)if(panels.get(i).contains(mx,my))return panels.get(i);return null;}
    private boolean covered(InventoryPanel owner,int x,int y,int w,int h) {
        int index=panels.indexOf(owner);
        for(int i=index+1;i<panels.size();i++){var p=panels.get(i);if(p.visible&&x<p.x+p.width&&x+w>p.x&&y<p.y+p.height&&y+h>p.y)return true;}
        return false;
    }
    private float containerSlotScale=1F;
    private double itemScale(){double value=InventoryPreferences.get().slotScale;if(kind==CompatBridge.Kind.BACKPACK)value=Math.min(value,Math.max(1,(hotbarTop()-gridTop()-20)/36.0));return value;}
    private float hotbarScale(){return (float)Math.min(InventoryPreferences.get().slotScale,Math.max(1,(items.width-20)/162.0));}
    private float slotScale(Slot slot){
        if(owners.get(slot)==items&&slot.container==player.getInventory()&&slot.getContainerSlot()<9)return hotbarScale();
        return owners.get(slot)==container&&!CompatBridge.storageGrid(menu)?containerSlotScale:owners.get(slot)==items?(float)itemScale():owners.get(slot)==container?(float)InventoryPreferences.get().slotScale:1F;
    }
    public int slotPixels(Slot slot){return Math.round(16*slotScale(slot));}
    private int pitch(){return (int)Math.round(18*itemScale());}
    private int columns(){return Math.max(1,Math.min(9,(items.width-20)/pitch()));}
    private int hotbarRows(){return 1;}
    private int hotbarTop(){return items.y+items.height-24-Math.round(18*hotbarScale());}
    public boolean isSlotCovered(Slot slot){return slot.x<0||covered(owners.get(slot),slot.x,slot.y,slotPixels(slot),slotPixels(slot));}
    public Slot pickSlot(double mx,double my){for(var slot:menu.slots)if(slot.isActive()&&!isSlotCovered(slot)&&owners.get(slot)==topPane(mx,my)&&mx>=slot.x&&mx<slot.x+slotPixels(slot)&&my>=slot.y&&my<slot.y+slotPixels(slot))return slot;return null;}
    @Override protected boolean isHovering(int x,int y,int w,int h,double mx,double my) {
        for(var slot:menu.slots)if(slot.x==x&&slot.y==y)return !isSlotCovered(slot)&&owners.get(slot)==topPane(mx,my)&&mx>=x&&mx<x+slotPixels(slot)&&my>=y&&my<y+slotPixels(slot);
        return super.isHovering(x,y,w,h,mx,my);
    }

    public void allowAutoBackpack(boolean value){autoBackpack=value;}

    private void resetLayout() {
        var prefs=InventoryPreferences.get();
        for(var panel:panels) prefs.panels.remove(layoutKey(panel));
        prefs.save(); clearWidgets(); init();
    }
    private void positionControls() {
        int n=storageTabs.size(),tabWidth=(items.width-16)/n;
        for(int i=0;i<n;i++){var b=storageTabs.get(i);b.setPosition(items.x+8+i*tabWidth,items.y+25);b.setWidth(tabWidth-2);b.active=menu.getCarried().isEmpty();
            if(b.getMessage().equals(tr("storage.backpack")))b.active&=kind==CompatBridge.Kind.BACKPACK || !CompatBridge.backpack(player).isEmpty();}
        search.setPosition(items.x+12,items.y+50); search.setWidth(items.width-24);
        int buttonWidth=(items.width-20)/6;
        for(int i=0;i<6;i++) { categories.get(i).setPosition(items.x+10+i*buttonWidth,items.y+70); categories.get(i).setWidth(buttonWidth-1); }
        previousPage.setPosition(items.x+items.width-42,items.y+2);nextPage.setPosition(items.x+items.width-23,items.y+2);
        previousPage.visible=nextPage.visible=kind==CompatBridge.Kind.CURIOS&&extras&&CompatBridge.integer(menu,"totalPages",1)>1;
        previousPage.active=CompatBridge.integer(menu,"currentPage",0)>0&&menu.getCarried().isEmpty();
        nextPage.active=CompatBridge.integer(menu,"currentPage",0)+1<CompatBridge.integer(menu,"totalPages",1)&&menu.getCarried().isEmpty();
        if(kind==CompatBridge.Kind.ACCESSORIES&&CompatBridge.originalAccessories(menu)){
            previousPage.visible=nextPage.visible=extras&&CompatBridge.integer(menu,"totalSlots",0)>8;
            previousPage.active=CompatBridge.integer(menu,"scrolledIndex",0)>0&&menu.getCarried().isEmpty();
            nextPage.active=CompatBridge.integer(menu,"scrolledIndex",0)<CompatBridge.integer(menu,"totalSlots",0)-8&&menu.getCarried().isEmpty();
        }
        cosmetics.setPosition(items.x+items.width-82,items.y+items.height-20);cosmetics.visible=kind==CompatBridge.Kind.CURIOS&&extras&&CompatBridge.flag(menu,"hasCosmetics");
        cosmetics.visible|=kind==CompatBridge.Kind.ACCESSORIES&&extras;
        cosmetics.active=menu.getCarried().isEmpty();
        if(powersButton!=null)powersButton.active=menu.getCarried().isEmpty();
        int half=(information.width-20)/2;
        summaryTab.setPosition(information.x+8,information.y+25); summaryTab.setWidth(half);

        attributeTab.setPosition(information.x+12+half,information.y+25);attributeTab.setWidth(half);
        attributeTab.visible=information.visible;
        attributeFilter.setPosition(information.x+8,information.y+information.height-27);attributeFilter.setWidth(information.width-16);
        attributeFilter.visible=information.visible&&page==2;attributeFilter.setMessage(tr(modifiedAttributes?"attributes.modified":"attributes.all"));
        gearButton.setPosition(equipment.x+8,equipment.y+equipment.height-(hasCraftGrid()?92:27));gearButton.setWidth(equipment.width-16);
        gearButton.visible=equipment.visible&&CompatBridge.loaded("camping")&&kind!=CompatBridge.Kind.BLOCK&&kind!=CompatBridge.Kind.AETHER&&(!CompatBridge.backpack(player).isEmpty()||kind==CompatBridge.Kind.BACKPACK);
        gearButton.active=menu.getCarried().isEmpty();
        for(var b:categories)b.visible=items.height>=220;
        ability.setPosition(powers.x+8,powers.y+powers.height-23); ability.setWidth(powers.width-16);
        summaryTab.visible=information.visible;
        var details=RacesBridge.read(player);
        ability.visible=powers.visible;
        var selected=AbilitySelection.selected(player);ability.active=selected!=null&&selected.ready().getAsBoolean();
        ability.setMessage(details!=null&&details.remainingTicks()>0?tr("remaining_short",(details.remainingTicks()+19)/20):tr("activate"));
    }
    private void moveSlot(int index,int x,int y) {
        var accessor=(SlotPositionAccessor)menu.slots.get(index);
        accessor.civitatumInventory$setX(x); accessor.civitatumInventory$setY(y);
    }
    private void place(Slot slot,int x,int y) {
        ((SlotPositionAccessor)slot).civitatumInventory$setX(x);
        ((SlotPositionAccessor)slot).civitatumInventory$setY(y);
    }
    private void assign(Slot slot,InventoryPanel owner,int x,int y){place(slot,x,y);owners.put(slot,owner);}
    private int gridTop(){return items.y+(items.height<220?76:96);}
    private int mainRows(){int space=hotbarTop()-gridTop()-10;if(kind==CompatBridge.Kind.BACKPACK)space=(space-20)/2;return Math.max(1,Math.min((27+columns()-1)/columns(),space/pitch()));}
    private int bagTop(){return gridTop()+mainRows()*pitch()+18;}
    private void positionSlots() {
        owners.clear();
        var active=new IdentityHashMap<Slot,Boolean>();
        for(var slot:menu.slots){int[] xy=original.computeIfAbsent(slot,s->new int[]{s.x,s.y});place(slot,xy[0],xy[1]);active.put(slot,slot.isActive());}
        extraSlots=menu.slots.stream().filter(s->{
            if(kind==CompatBridge.Kind.ACCESSORIES)return CompatBridge.accessoryArmor(s)<0&&CompatBridge.accessorySlot(s)&&CompatBridge.cosmeticSlot(s)==(CompatBridge.originalAccessories(menu)?CompatBridge.accessoriesCosmetics(menu):accessoryCosmetic)&&active.getOrDefault(s,false);
            if(kind==CompatBridge.Kind.BACKPACK||kind==CompatBridge.Kind.BLOCK)return s.container!=player.getInventory();
            if(kind==CompatBridge.Kind.AETHER)return s.index>=5&&s.index<13;
            return s.index>=46;
        }).toList();
        for(var slot:menu.slots)place(slot,-10000,-10000);
        int cols=columns(),cell=pitch(),gx=items.x+(items.width-cols*cell)/2,rows=mainRows(),totalRows=(27+cols-1)/cols;
        playerRow=Math.max(0,Math.min(playerRow,totalRows-rows));
        for(var slot:menu.slots)if(slot.container==player.getInventory()){
            int i=slot.getContainerSlot(),r=(i-9)%totalRows-playerRow;
            // Fill each column from top to bottom; menu and storage IDs stay intact.
            if(i>=9&&i<36&&(!extras||kind==CompatBridge.Kind.BACKPACK||kind==CompatBridge.Kind.BLOCK)&&r>=0&&r<rows)assign(slot,items,gx+(i-9)/totalRows*cell,gridTop()+r*cell);
            else if(i>=0&&i<9)assign(slot,items,items.x+(items.width-9*Math.round(18*hotbarScale()))/2+i*Math.round(18*hotbarScale()),hotbarTop());
            else if(i>=36&&i<=39&&equipment.visible)assign(slot,equipment,equipment.x+10,equipment.y+34+(39-i)*22);
            else if(i==40&&equipment.visible)assign(slot,equipment,equipment.x+equipment.width-28,equipment.y+100);
        }
        if(kind==CompatBridge.Kind.ACCESSORIES&&equipment.visible)for(var slot:menu.slots){int armor=CompatBridge.accessoryArmor(slot);if(armor>=0)assign(slot,equipment,equipment.x+10,equipment.y+34+armor*22);}
        if(kind==CompatBridge.Kind.BLOCK){
            if(CompatBridge.storageGrid(menu)){
                int blockCols=menu instanceof net.minecraft.world.inventory.HopperMenu?5:menu instanceof net.minecraft.world.inventory.DispenserMenu?3:9;
                blockCols=Math.max(1,Math.min(blockCols,(container.width-22)/cell));int visible=Math.max(1,(container.height-48)/cell),total=Math.max(1,(extraSlots.size()+blockCols-1)/blockCols);
                blockRow=Math.max(0,Math.min(blockRow,total-visible));
                for(int i=0;i<extraSlots.size();i++){int r=i%total-blockRow;if(r>=0&&r<visible)assign(extraSlots.get(i),container,container.x+(container.width-blockCols*cell)/2+i/total*cell,container.y+30+r*cell);}
            }else{
                int minX=10000,maxX=0,minY=10000,maxY=0;
                for(var slot:extraSlots){int[] xy=original.get(slot);minX=Math.min(minX,xy[0]);maxX=Math.max(maxX,xy[0]+16);minY=Math.min(minY,xy[1]);maxY=Math.max(maxY,xy[1]+16);}
                float scale=(float)Math.min(InventoryPreferences.get().slotScale,Math.min((container.width-30F)/(maxX-minX),(container.height-62F)/(maxY-minY)));
                scale=Math.max(1,scale);containerSlotScale=scale;
                int cx=container.x+(container.width-Math.round((maxX-minX)*scale))/2,cy=container.y+30+Math.max(0,(container.height-62-Math.round((maxY-minY)*scale))/2);
                for(var slot:extraSlots){int[] xy=original.get(slot);assign(slot,container,cx+Math.round((xy[0]-minX)*scale),cy+Math.round((xy[1]-minY)*scale));}
            }
        }else if(kind==CompatBridge.Kind.AETHER&&equipment.visible){
            for(int i=0;i<extraSlots.size();i++)assign(extraSlots.get(i),equipment,equipment.x+14+i%4*18,equipment.y+equipment.height-109+i/4*18);
        }else if(extras||kind==CompatBridge.Kind.BACKPACK){
            int columns=Math.min(cols,kind==CompatBridge.Kind.BACKPACK&&extraSlots.size()==24?8:9);
            int top=kind==CompatBridge.Kind.BACKPACK?bagTop():gridTop();
            int visible=Math.max(1,(hotbarTop()-10-top)/cell),total=Math.max(1,(extraSlots.size()+columns-1)/columns);
            extraRow=Math.max(0,Math.min(extraRow,total-visible));
            for(int i=0;i<extraSlots.size();i++){int r=i%total-extraRow;if(r>=0&&r<visible)assign(extraSlots.get(i),items,items.x+(items.width-columns*cell)/2+i/total*cell,top+r*cell);}
        }
        if(equipment.visible&&hasCraftGrid()){
            int cy=equipment.y+equipment.height-53;
            for(int i=1;i<5;i++)assign(menu.slots.get(i),equipment,equipment.x+14+(i-1)%2*18,cy+(i-1)/2*18);
            assign(menu.slots.getFirst(),equipment,equipment.x+equipment.width-31,cy+9);
        }
    }
    @Override public void containerTick(){
        CompatBridge.tick(menu);positionSlots();
        if(autoBackpack&&!backpackRequested&&kind==CompatBridge.Kind.INVENTORY&&menu.getCarried().isEmpty()&&!CompatBridge.backpack(player).isEmpty())backpackRequested=CompatBridge.openBackpack();
    }

    @Override public void render(GuiGraphics g,int mx,int my,float tick) {
        positionControls();positionSlots();
        super.render(g,mx,my,tick);
        renderTooltip(g,mx,my);
        if(page==2&&topPane(mx,my)==information)AttributeView.tooltip(g,font,information);
        if(topPane(mx,my)==powers)powersView.tooltip(g,font,powers);
        if(hoveredSlot!=null&&hoveredSlot.getItem().isEmpty()) {
            var label=CompatBridge.slotLabel(hoveredSlot);
            if(label!=null)g.renderTooltip(font,label.copy().append(" · ").append(tr("render_toggle")),mx,my);
        }
        for(int i=0;i<categories.size();i++) if(categories.get(i).isHovered()) g.renderTooltip(font,tr("category."+i),mx,my);
        for(var panel:panels) if(panel==topPane(mx,my)&&panel.resizeEdges(mx,my)!=0) g.renderTooltip(font,tr("resize_hint"),mx,my);
        if(hoveredSlot==null && dragging==null) for(var panel:panels) if(panel.titleContains(mx,my)) g.renderTooltip(font,tr("move_hint"),mx,my);
    }
    @Override protected void renderSlot(GuiGraphics g,Slot slot){} // Drawn inside its owner window, with that window's depth.
    @Override protected void renderSlotHighlight(GuiGraphics g,Slot slot,int mx,int my,float tick){
        if(isSlotCovered(slot))return;int size=slotPixels(slot);g.pose().pushPose();g.pose().translate(0,0,panels.indexOf(owners.get(slot))*40+210);g.fill(slot.x,slot.y,slot.x+size,slot.y+size,0x55FFFFFF);g.flush();g.pose().popPose();
    }
    public boolean highlighted(Slot slot){return owners.get(slot)==items&&!slot.getItem().isEmpty()&&(!query.isBlank()||category!=0)&&matches(slot.getItem());}
    private void renderOwnedSlot(GuiGraphics g,Slot slot){
        int size=slotPixels(slot);renderSlotFrame(g,slot.x-1,slot.y-1,size+2);
        g.pose().pushPose();float scale=slotScale(slot);g.pose().translate(slot.x,slot.y,-95);g.pose().scale(scale,scale,1);g.pose().translate(-slot.x,-slot.y,0);super.renderSlot(g,slot);g.flush();g.pose().popPose();
        g.pose().pushPose();g.pose().translate(0,0,210);
        if(owners.get(slot)==items&&!slot.getItem().isEmpty()){
            if(!matches(slot.getItem()))g.fill(slot.x,slot.y,slot.x+size,slot.y+size,0xA518130E);
            else if(highlighted(slot)){MorrowindSkin.frame(g,slot.x-2,slot.y-2,size+4,size+4,true);g.fill(slot.x,slot.y+size-2,slot.x+size,slot.y+size,0xFFB5D78B);}
        }g.flush();g.pose().popPose();
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float tick) {renderBg(g,tick,mx,my);}
    @Override protected void renderBg(GuiGraphics g,float tick,int mx,int my) {
        if(!compact)g.drawString(font,tr("title"),10,9,GOLD,false);
        for(var panel:List.copyOf(panels))if(panel.visible){
            g.pose().pushPose();g.pose().translate(0,0,panels.indexOf(panel)*40);
            renderPanel(g,panel,panel==container?nativeScreen.getTitle():tr(panel.id));
            if(panel==equipment){
                int px=equipment.x+32,py=equipment.y+25,pw=equipment.width-41,ph=equipment.height-(kind==CompatBridge.Kind.AETHER?144:gearButton.visible&&hasCraftGrid()?128:94);
                g.fill(px,py,px+pw,py+ph,0x3018130E);
                InventoryScreen.renderEntityInInventoryFollowsMouse(g,px+1,py+1,px+pw-1,py+ph-1,modelScale(),0.0625F,px+pw/2F+8,py+ph/2F+6,player);
                if(hasCraftGrid()){
                    g.drawString(font,tr("crafting"),equipment.x+9,equipment.y+equipment.height-67,MUTED,false);
                    var out=menu.slots.getFirst();int ax=out.x-26,ay=out.y+6;g.fill(ax,ay,ax+17,ay+5,0xFFB29A6B);
                    for(int n=0;n<4;n++)g.fill(ax+17+n,ay-2+n,ax+18+n,ay+7-n,0xFFB29A6B);
                    MorrowindSkin.frame(g,out.x-3,out.y-3,22,22,true);
                }
                if(kind==CompatBridge.Kind.AETHER)g.drawString(font,"Aether",equipment.x+9,equipment.y+equipment.height-123,GOLD,false);
                if(kind==CompatBridge.Kind.BACKPACK||kind==CompatBridge.Kind.BLOCK){
                    var armor=new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET};
                    for(int i=0;i<4;i++){int x=equipment.x+10,y=equipment.y+34+i*22;renderSlotFrame(g,x-1,y-1);g.renderItem(player.getItemBySlot(armor[i]),x,y);}
                    if(kind==CompatBridge.Kind.BLOCK)g.drawWordWrap(font,tr("container_equipment_hint"),equipment.x+9,equipment.y+equipment.height-61,equipment.width-18,MUTED);
                }
            }
            if(panel==items){
                g.fill(items.x+8,items.y+46,items.x+items.width-8,items.y+65,0x35130E09);MorrowindSkin.frame(g,items.x+8,items.y+46,items.width-16,19,false);
                if(kind==CompatBridge.Kind.BACKPACK)g.drawString(font,tr("storage.backpack"),items.x+10,bagTop()-12,GOLD,false);
                int occupied=0,matched=0;for(var slot:menu.slots)if(owners.get(slot)==items&&!slot.getItem().isEmpty()){occupied++;if(matches(slot.getItem()))matched++;}
                g.drawString(font,font.plainSubstrByWidth(tr("items_count",matched,occupied).getString(),items.width-(cosmetics.visible?98:20)),items.x+10,items.y+items.height-16,MUTED,false);
            }
            if(panel==information){if(page==2)scroll=AttributeView.render(g,font,player,information,scroll,modifiedAttributes,mx,my);else renderInformation(g);}
            if(panel==container)renderProgress(g);
            if(panel==powers)powersView.render(g,font,powers,player,mx,my);
            for(var slot:menu.slots)if(owners.get(slot)==panel&&!isSlotCovered(slot))renderOwnedSlot(g,slot);
            if(panel==items&&!covered(items,search.getX(),search.getY(),search.getWidth(),search.getHeight()))search.render(g,mx,my,tick);
            for(var b:widgets)if(widgetOwner(b)==panel&&b.visible&&!covered(panel,b.getX(),b.getY(),b.getWidth(),b.getHeight()))b.render(g,mx,my,tick);
            g.flush();g.pose().popPose();
        }
        g.pose().pushPose();g.pose().translate(0,0,400);
        for(var b:widgets)if(widgetOwner(b)==null&&b.visible)b.render(g,mx,my,tick);
        g.drawString(font,font.plainSubstrByWidth(tr(compact?"footer_short":"footer").getString(),Math.max(80,width-176)),8,height-14,MUTED,false);g.flush();g.pose().popPose();
    }
    private InventoryPanel widgetOwner(AbstractWidget b){
        if(categories.contains(b)||storageTabs.contains(b)||b==previousPage||b==nextPage||b==cosmetics)return items;
        if(b==summaryTab||b==attributeTab||b==attributeFilter)return information;
        if(b==ability)return powers;
        if(b==gearButton)return equipment;return null;
    }
    private void renderProgress(GuiGraphics g){
        if(CompatBridge.storageGrid(menu)||extraSlots.isEmpty())return;
        double burn=0,fuel=0;boolean furnace=menu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu;
        if(furnace){burn=CompatBridge.progress(menu,"getBurnProgress",1);fuel=CompatBridge.progress(menu,"getLitProgress",1);}
        else if(menu.getClass().getName().endsWith("AltarMenu")||menu.getClass().getName().endsWith("FreezerMenu")){burn=CompatBridge.progress(menu,"getBurnProgress",24);fuel=CompatBridge.progress(menu,"getLitProgress",13);}
        else if(menu.getClass().getName().endsWith("IncubatorMenu"))burn=CompatBridge.progress(menu,"getIncubationProgressScaled",54);
        else if(menu instanceof net.minecraft.world.inventory.BrewingStandMenu){double brewing=CompatBridge.progress(menu,"getBrewingTicks",400);burn=brewing>0?1-brewing:0;fuel=CompatBridge.progress(menu,"getFuel",20);}
        Slot output=menu instanceof net.minecraft.world.inventory.CraftingMenu?extraSlots.getFirst():furnace&&extraSlots.size()>2?extraSlots.get(2):null;
        if(output!=null){
            int size=slotPixels(output),x=output.x-28,y=output.y+size/2-3;
            g.fill(x,y,x+19,y+6,0xFF514631);
            for(int n=0;n<6;n++)g.fill(x+19+n,y-3+n,x+20+n,y+9-n,0xFF8B7851);
            g.fill(x,y,x+(int)(19*burn),y+6,0xFFD3B478);
            // Subtle result recess distinguishes the output from ingredient cells.
            MorrowindSkin.frame(g,output.x-3,output.y-3,size+6,size+6,true);
            if(furnace){var input=extraSlots.getFirst();var coal=extraSlots.get(1);int fx=input.x+size/2-3,fy=(input.y+size+coal.y)/2-5;
                g.fill(fx,fy,fx+6,fy+9,0xFF4D3929);g.fill(fx,fy+9-(int)(9*fuel),fx+6,fy+9,0xFFCC8B43);}
        }
        int x=container.x+14,y=container.y+container.height-20,w=container.width-28;
        if(!(menu instanceof net.minecraft.world.inventory.CraftingMenu)){
            g.fill(x,y,x+w,y+5,0x66201810);g.fill(x,y,x+(int)(w*burn),y+5,0xFFBDA06B);
            g.fill(x,y+7,x+w,y+9,0x44201810);g.fill(x,y+7,x+(int)(w*fuel),y+9,0xFFBB7744);
        }
    }

    private void renderPanel(GuiGraphics g,InventoryPanel p,Component label) {
        MorrowindSkin.body(g,p.x+2,p.y+2,p.width-4,p.height-4);
        MorrowindSkin.frame(g,p.x,p.y,p.width,p.height,false);
        g.fill(p.x+2,p.y+17,p.x+p.width-2,p.y+18,0xFFAF9462);
        g.drawString(font,font.plainSubstrByWidth(label.getString(),p.width-22),p.x+8,p.y+5,TEXT,true);
        for(int i=0;i<3;i++) g.fill(p.x+p.width-4-i*3,p.y+p.height-4,p.x+p.width-3,p.y+p.height-3-i*3,0xFF958E78);
    }
    private void renderSlotFrame(GuiGraphics g,int x,int y){renderSlotFrame(g,x,y,18);}
    private void renderSlotFrame(GuiGraphics g,int x,int y,int size) {
        g.fill(x+1,y+1,x+size-1,y+size-1,0x3018100A);
        g.fill(x,y,x+size,y+1,0xFF473B2A);g.fill(x,y,x+1,y+size,0xFF473B2A);
        g.fill(x+1,y+size-1,x+size,y+size,0xFFA48D61);g.fill(x+size-1,y+1,x+size,y+size,0xFFA48D61);
    }
    public int modelScale(){int pw=equipment.width-43,ph=equipment.height-(kind==CompatBridge.Kind.AETHER?146:gearButton!=null&&gearButton.visible&&hasCraftGrid()?130:96);return Math.max(8,(int)Math.floor(Math.min((ph-16)/Math.max(2.15,player.getBbHeight()+0.35),(pw-16)/1.55)));}
    private boolean hasCraftGrid(){return kind!=CompatBridge.Kind.BLOCK&&menu.slots.size()>=5&&menu.slots.getFirst() instanceof net.minecraft.world.inventory.ResultSlot;}

    private void renderInformation(GuiGraphics g) {
        int x=information.x+9,y=information.y+51,w=information.width-18;
        int bottom=information.y+information.height-(page==1&&RacesBridge.read(player)!=null?34:14);
        List<Component> lines=new ArrayList<>();
        var details=RacesBridge.read(player);
        if(page==0) {
            lines.add(Component.literal(details!=null&&!details.name().isBlank()?details.name():player.getName().getString()).withColor(GOLD));
            if(details!=null) lines.add(tr("race",details.race()));
            lines.add(tr("health",number(player.getHealth()),number(player.getMaxHealth())));
            lines.add(tr("food",player.getFoodData().getFoodLevel()));
            lines.add(tr("level",player.experienceLevel));
            lines.add(tr("armor",player.getArmorValue()));
            lines.add(tr("toughness",number(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS))));
            lines.add(tr("damage",number(player.getAttributeValue(Attributes.ATTACK_DAMAGE))));
            lines.add(tr("attack_speed",number(player.getAttributeValue(Attributes.ATTACK_SPEED))));
            lines.add(tr("movement",number(player.getAttributeValue(Attributes.MOVEMENT_SPEED))));
            lines.add(tr("effects").copy().withColor(GOLD));
            if(player.getActiveEffects().isEmpty()) lines.add(tr("no_effects").copy().withColor(MUTED));
            for(var effect:player.getActiveEffects()) {
                lines.add(effect.getEffect().value().getDisplayName().copy().append(" "+(effect.getAmplifier()+1)+" · ")
                    .append(MobEffectUtil.formatDuration(effect,1,player.level().tickRateManager().tickrate())));
            }
        } else if(details==null) {
            lines.add(tr("no_race").copy().withColor(MUTED));
            lines.add(tr("no_race_description"));
        } else {
            lines.add(Component.literal(details.race()).withColor(GOLD));
            lines.add(tr("benefits").copy().withColor(GOLD)); lines.add(Component.literal(details.positive()));
            lines.add(tr("limitations").copy().withColor(GOLD)); lines.add(Component.literal(details.negative()));
            lines.add(tr("ability").copy().withColor(GOLD)); lines.add(Component.literal(details.ability()));
            lines.add(tr("cost",details.food()));
            lines.add(details.remainingTicks()>0?tr("remaining",(details.remainingTicks()+19)/20):tr("ready"));
            lines.add(tr("cooldown",details.cooldown()));
            if(details.curseDeaths()>0) lines.add(tr("curse",details.curseDeaths()));
            if(!details.sign().isBlank()) lines.add(tr("sign",details.sign()));
        }
        List<net.minecraft.util.FormattedCharSequence> wrapped=new ArrayList<>();
        for(var line:lines) {wrapped.addAll(font.split(line,w-4));if(page==1)wrapped.add(net.minecraft.util.FormattedCharSequence.EMPTY);}
        int lineHeight=12, maxScroll=Math.max(0,wrapped.size()*lineHeight-(bottom-y));
        scroll=Math.max(0,Math.min(scroll,maxScroll));
        g.enableScissor(x,y,x+w,bottom);
        for(int i=0;i<wrapped.size();i++) g.drawString(font,wrapped.get(i),x,y+i*lineHeight-scroll,TEXT,true);
        g.disableScissor();
        if(maxScroll>0) {
            int track=bottom-y,thumb=Math.max(12,track*track/(track+maxScroll));
            int ty=y+(track-thumb)*scroll/maxScroll;
            g.fill(information.x+information.width-5,y,information.x+information.width-3,bottom,0xFF232829);
            g.fill(information.x+information.width-5,ty,information.x+information.width-3,ty+thumb,0xFFAF9462);
        }
    }
    private static String number(double n) { return String.format(Locale.ROOT,"%.2f",n).replaceAll("0+$","").replaceAll("\\.$",""); }
    public boolean matches(ItemStack stack) {
        if(stack.isEmpty()) return true;
        if(!query.isBlank()) {
            String haystack=(stack.getHoverName().getString()+" "+BuiltInRegistries.ITEM.getKey(stack.getItem())).toLowerCase(Locale.ROOT);
            if(!haystack.contains(query.toLowerCase(Locale.ROOT).strip())) return false;
        }
        Item item=stack.getItem();
        boolean weapon=item instanceof SwordItem || item instanceof DiggerItem || item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem || item instanceof MaceItem;
        boolean armor=item instanceof ArmorItem || item instanceof ShieldItem || item instanceof ElytraItem;
        boolean food=stack.has(DataComponents.FOOD) || item instanceof PotionItem;
        boolean block=item instanceof BlockItem;
        return switch(category) { case 1->weapon;case 2->armor;case 3->food;case 4->block;case 5->!weapon&&!armor&&!food&&!block;default->true; };
    }
    @Override protected boolean hasClickedOutside(double mx,double my,int left,int top,int button) {
        if(my<24 || my>=height-42) return false;
        return panels.stream().noneMatch(p->p.contains(mx,my));
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        InventoryPanel top=topPane(mx,my);Slot slot=pickSlot(mx,my);
        if(top==powers&&button==0&&powersView.click(powers,player,mx,my)){raise(powers);return true;}
        if(button==1&&hasControlDown()&&slot!=null){if(kind==CompatBridge.Kind.CURIOS&&slot.index>=46){CompatBridge.toggleRender(slot);return true;}if(CompatBridge.accessorySlot(slot)){CompatBridge.accessoryRender(slot);return true;}}
        boolean widget=false;for(var b:widgets)if(b.visible&&b.isMouseOver(mx,my)&&(widgetOwner(b)==null||widgetOwner(b)==top)&&!covered(widgetOwner(b),b.getX(),b.getY(),b.getWidth(),b.getHeight()))widget=true;
        if(button==0&&menu.getCarried().isEmpty()&&top!=null&&!widget&&(top.resizeEdges(mx,my)!=0||top.titleContains(mx,my))){
            raise(top);dragging=top;resizeMask=top.resizeEdges(mx,my);anchorX=top.x;anchorY=top.y;anchorW=top.width;anchorH=top.height;dragOffsetX=(int)mx;dragOffsetY=(int)my;setFocused(null);return true;
        }
        if(!search.isMouseOver(mx,my)||top!=items)search.setFocused(false);
        var disabled=new ArrayList<AbstractWidget>();for(var b:widgets)if(b.active&&widgetOwner(b)!=null&&(widgetOwner(b)!=top||covered(widgetOwner(b),b.getX(),b.getY(),b.getWidth(),b.getHeight()))){b.active=false;disabled.add(b);}
        boolean searchVisible=search.visible;search.visible=top==items&&!covered(items,search.getX(),search.getY(),search.getWidth(),search.getHeight());
        try{if(top!=null&&slot==null&&!widget&&!search.isMouseOver(mx,my)){raise(top);return true;}boolean result=super.mouseClicked(mx,my,button);if(top!=null)raise(top);return result;}
        finally{disabled.forEach(b->b.active=true);search.visible=searchVisible;}
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(dragging!=null){
            int ddx=(int)mx-dragOffsetX,ddy=(int)my-dragOffsetY;
            int nx=anchorX,ny=anchorY,right=anchorX+anchorW,bottom=anchorY+anchorH;
            if(resizeMask==0){nx+=ddx;ny+=ddy;right+=ddx;bottom+=ddy;}
            else{
                if((resizeMask&InventoryPanel.LEFT)!=0)nx=Math.max(4,Math.min(right-dragging.minWidth,anchorX+ddx));
                if((resizeMask&InventoryPanel.RIGHT)!=0)right=Math.min(width-4,Math.max(nx+dragging.minWidth,anchorX+anchorW+ddx));
                if((resizeMask&InventoryPanel.TOP)!=0)ny=Math.max(25,Math.min(bottom-dragging.minHeight,anchorY+ddy));
                if((resizeMask&InventoryPanel.BOTTOM)!=0)bottom=Math.min(height-44,Math.max(ny+dragging.minHeight,anchorY+anchorH+ddy));
            }
            dragging.x=nx;dragging.y=ny;dragging.width=right-nx;dragging.height=bottom-ny;dragging.constrain(width,height);
            if(dragging==equipment){equipment.width=Math.min(equipment.width,width-items.width-8);items.x=equipment.x+equipment.width;items.y=equipment.y;items.height=equipment.height;}
            if(dragging==items||dragging==equipment){items.constrain(width,height);dockEquipment();}
            positionControls();positionSlots();return true;
        }
        return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){if(dragging!=null){dragging=null;saveLayout();return true;}return super.mouseReleased(mx,my,button);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        var top=topPane(mx,my);
        if(top==powers){powersView.scroll(powers,player,my,vertical);return true;}
        if(top==information){scroll=Math.max(0,scroll-(int)(vertical*20));return true;}
        if(top==container){blockRow=Math.max(0,blockRow-(int)vertical);positionSlots();return true;}
        if(top==items){if(kind==CompatBridge.Kind.BACKPACK&&my>=bagTop()||extras)extraRow=Math.max(0,extraRow-(int)vertical);else playerRow=Math.max(0,playerRow-(int)vertical);positionSlots();return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }

    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(search.isFocused()) {
            if(key==GLFW.GLFW_KEY_ESCAPE) {search.setFocused(false);setFocused(null);return true;}
            if(search.keyPressed(key,scan,modifiers)) return true;
            // Never let inventory shortcuts fire while entering a search query.
            return true;
        }
        if(key==GLFW.GLFW_KEY_F && hasControlDown()) {setFocused(search);search.setFocused(true);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean charTyped(char character,int modifiers) {
        return search.isFocused()?search.charTyped(character,modifiers):super.charTyped(character,modifiers);
    }
    private void saveLayout() {var prefs=InventoryPreferences.get();for(var panel:panels) prefs.panels.put(layoutKey(panel),panel.save(width,height));prefs.save();}
    @Override public void removed() {
        saveLayout();
        original.forEach((slot,coordinates)->place(slot,coordinates[0],coordinates[1]));
        if(!preserveMenu&&minecraft.player!=null&&minecraft.player.containerMenu==menu)super.removed();
    }
    public InventoryPanel equipmentPanel() { return equipment; }
    public InventoryPanel itemsPanel() { return items; }
    public InventoryPanel powersPanel(){return powers;}
    public InventoryPanel containerPanel(){return container;}
    public InventoryPanel informationPanel() { return information; }
    public void openNative(){preserveMenu=true;InventoryClient.nativeScreen(nativeScreen);}
    public CompatBridge.Kind integrationKind(){return kind;}
    public boolean viewingExtras(){return extras;}
    public List<Slot> compatibilitySlots(){return extraSlots;}
}
