package team.civitatum.inventory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class CivitatumHud {
    public static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("civitatum_inventory","status");
    public static boolean active(){var mc=Minecraft.getInstance();return InventoryPreferences.get().enabled&&mc.player!=null&&!mc.player.isSpectator()&&mc.gameMode!=null&&!mc.gameMode.hasInfiniteItems();}
    public static void register(RegisterGuiLayersEvent event){event.registerAbove(VanillaGuiLayers.HOTBAR,ID,CivitatumHud::render);}
    public static void hideReplaced(RenderGuiLayerEvent.Pre event){
        if(!active())return;var id=event.getName();
        if(id.equals(VanillaGuiLayers.HOTBAR)||id.equals(VanillaGuiLayers.PLAYER_HEALTH)||id.equals(VanillaGuiLayers.FOOD_LEVEL))event.setCanceled(true);
        if(id.getNamespace().equals("ars_nouveau")&&id.getPath().equals("mana_hud")&&ArsBridge.mana(Minecraft.getInstance().player)!=null)event.setCanceled(true);
    }
    public static void render(GuiGraphics g,DeltaTracker tick){
        var mc=Minecraft.getInstance();if(!active()||mc.options.hideGui||mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)return;var player=mc.player;
        int x=8,y=g.guiHeight()-34,w=Math.max(76,Math.min(116,g.guiWidth()/4));
        int healthColor=player.hasEffect(net.minecraft.world.effect.MobEffects.POISON)?0xFF80A33F:player.hasEffect(net.minecraft.world.effect.MobEffects.WITHER)?0xFF766B60:0xFFAE4236;
        bar(g,x,y,w,player.getHealth(),player.getMaxHealth(),healthColor);
        // Absorption remains visible, proportional to the health scale, with its numeric value.
        if(player.getAbsorptionAmount()>0){int aw=Math.min(w-4,Math.round((w-4)*player.getAbsorptionAmount()/player.getMaxHealth()));g.fill(x+w-2-aw,y+2,x+w-2,y+4,0xFFE3C066);}
        var mana=ArsBridge.mana(player);bar(g,x,y+10,w,mana==null?0:mana.current(),mana==null?0:mana.max(),0xFF496FAD);
        if(mana!=null&&mana.max()>0&&mana.reserved()>0){int rw=(int)Math.min(w-4,(w-4)*mana.reserved());g.fill(x+w-2-rw,y+12,x+w-2,y+17,0xBB161C2D);}
        bar(g,x,y+20,w,player.getFoodData().getFoodLevel(),20,0xFF718D48);
        slot(g,player.getMainHandItem(),x+w+8,y+2);
        var selected=AbilitySelection.selected(player);slot(g,selected==null?ItemStack.EMPTY:selected.icon(),x+w+36,y+2);
        if(selected!=null&&!selected.ready().getAsBoolean())g.fill(x+w+39,y+5,x+w+55,y+21,0x8818130E);
        // Shield / off-hand use must still be visible after hiding the vanilla hotbar.
        if(!player.getOffhandItem().isEmpty())slot(g,player.getOffhandItem(),x+w+64,y+2);
    }
    private static void bar(GuiGraphics g,int x,int y,int w,double current,double max,int color){
        g.fill(x,y,x+w,y+9,0xCC17130E);MorrowindSkin.frame(g,x,y,w,9,false);
        if(max>0)g.fill(x+2,y+2,x+2+(int)Math.round((w-4)*Math.max(0,Math.min(1,current/max))),y+7,color);
    }
    private static void slot(GuiGraphics g,ItemStack item,int x,int y){g.fill(x,y,x+24,y+24,0x9917130E);MorrowindSkin.frame(g,x,y,24,24,true);g.renderItem(item,x+4,y+4);g.renderItemDecorations(Minecraft.getInstance().font,item,x+4,y+4);}
}
