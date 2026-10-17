package team.civitatum.inventory.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PowersView {
    private List<Component> hover=List.of();
    private int scroll;
    private int effectScroll;
    private int effectsBottom(InventoryPanel p,Player player){
        int count=player.getActiveEffects().size()+(RacesBridge.read(player)==null?0:2);
        int columns=Math.max(1,(p.width-20)/24);
        return p.y+46+Math.max(1,(count+columns-1)/columns)*24;
    }
    public void render(GuiGraphics g,Font font,InventoryPanel p,Player player,int mx,int my){
        hover=List.of();int columns=Math.max(1,(p.width-20)/24),i=0,bottom=Math.min(effectsBottom(p,player),p.y+p.height-80);
        g.drawString(font,CivitatumInventoryScreen.tr("effects"),p.x+9,p.y+26,0xD3B478,false);
        var race=RacesBridge.read(player);
        int count=player.getActiveEffects().size()+(race==null?0:2),visibleRows=Math.max(1,(bottom-p.y-42)/24);
        effectScroll=Math.max(0,Math.min(effectScroll,Math.max(0,(count+columns-1)/columns-visibleRows)));
        g.enableScissor(p.x+6,p.y+40,p.x+p.width-6,bottom);
        if(race!=null){
            for(int n=0;n<2;n++){
                int x=p.x+10+i%columns*24,y=p.y+42+(i/columns-effectScroll)*24;i++;if(y<p.y+40||y+20>bottom)continue;
                MorrowindSkin.frame(g,x-1,y-1,20,20,false);g.renderItem(new ItemStack(n==0?Items.OAK_SAPLING:Items.FLINT),x+1,y+1);
                if(mx>=x&&mx<x+20&&my>=Math.max(y,p.y+40)&&my<y+20&&my<bottom)hover=List.of(Component.literal(race.race()).withColor(0xD3B478),CivitatumInventoryScreen.tr(n==0?"benefits":"limitations"),Component.literal(n==0?race.positive():race.negative()));
            }
        }
        for(var effect:player.getActiveEffects()){
            int x=p.x+10+i%columns*24,y=p.y+42+(i/columns-effectScroll)*24;i++;if(y<p.y+40||y+20>bottom)continue;
            MorrowindSkin.frame(g,x-1,y-1,20,20,false);
            g.blit(x+1,y+1,0,18,18,Minecraft.getInstance().getMobEffectTextures().get(effect.getEffect()));
            if(mx>=x&&mx<x+20&&my>=Math.max(y,p.y+40)&&my<y+20&&my<bottom)hover=List.of(effect.getEffect().value().getDisplayName().copy().append(" "+(effect.getAmplifier()+1)),MobEffectUtil.formatDuration(effect,1,player.level().tickRateManager().tickrate()));
        }
        g.disableScissor();
        if(i==0)g.drawString(font,font.plainSubstrByWidth(CivitatumInventoryScreen.tr("no_effects").getString(),p.width-20),p.x+10,p.y+47,0xABA799,false);
        g.fill(p.x+6,bottom+4,p.x+p.width-6,bottom+5,0xFF8C754F);
        g.drawString(font,CivitatumInventoryScreen.tr("abilities"),p.x+9,bottom+12,0xD3B478,false);
        int top=bottom+26,end=p.y+p.height-26;var list=AbilitySelection.available(player);var selected=AbilitySelection.selected(player);
        scroll=Math.max(0,Math.min(scroll,Math.max(0,list.size()*24-(end-top))));
        g.enableScissor(p.x+6,top,p.x+p.width-6,end);
        for(int n=0;n<list.size();n++){
            var a=list.get(n);int y=top+n*24-scroll;boolean active=selected!=null&&selected.id().equals(a.id());
            if(active)g.fill(p.x+7,y,p.x+p.width-7,y+22,0x663D5032);
            g.renderItem(a.icon(),p.x+10,y+3);
            g.drawString(font,font.plainSubstrByWidth(a.name().getString(),p.width-43),p.x+31,y+7,active?0xB5D78B:0xE9E3D4,false);
            if(mx>=p.x+7&&mx<p.x+p.width-7&&my>=Math.max(y,top)&&my<Math.min(y+22,end))hover=a.description();
        }
        g.disableScissor();
        if(list.isEmpty())g.drawWordWrap(font,CivitatumInventoryScreen.tr("no_race_description"),p.x+10,top,p.width-20,0xABA799);
    }
    public boolean click(InventoryPanel p,Player player,double mx,double my){
        int top=Math.min(effectsBottom(p,player),p.y+p.height-80)+26;
        if(mx<p.x+7||mx>=p.x+p.width-7||my<top||my>=p.y+p.height-26)return false;
        var list=AbilitySelection.available(player);int i=((int)my-top+scroll)/24;
        if(i>=0&&i<list.size()){AbilitySelection.select(list.get(i).id());return true;}return false;
    }
    public void scroll(InventoryPanel p,Player player,double my,double delta){
        if(my<Math.min(effectsBottom(p,player),p.y+p.height-80))effectScroll=Math.max(0,effectScroll-(int)delta);
        else scroll=Math.max(0,scroll-(int)(delta*24));
    }
    public void tooltip(GuiGraphics g,Font font,InventoryPanel p){SideTooltip.render(g,font,p,hover);}
}
