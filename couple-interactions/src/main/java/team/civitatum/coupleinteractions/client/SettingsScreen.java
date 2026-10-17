package team.civitatum.coupleinteractions.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.ModConfigSpec;
import team.civitatum.coupleinteractions.core.*;

public final class SettingsScreen extends Screen {
    private final Screen parent;private final UUID target;
    private int page,scroll;private boolean dirty;
    private RemotePlayer first,second;
    private float angle=-.4f;
    public SettingsScreen(Screen parent){this(parent,null);}
    public SettingsScreen(Screen parent,UUID target){super(Component.translatable("ui.civitatum_couple.settings_title"));this.parent=parent;this.target=target;}
    private Component tr(String key){return Component.translatable("ui.civitatum_couple."+key);}
    private void button(int x,int y,int w,String text,Runnable run){addRenderableWidget(Button.builder(Component.literal(text),b->run.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        int x=Math.max(10,width/2-240),w=Math.min(480,width-20),tab=w/4;
        for(int i=0;i<4;i++){int n=i;button(x+i*tab,42,tab-3,tr(new String[]{"requests","favorites","pose","effects"}[i]).getString(),()->{flush();page=n;scroll=0;rebuildWidgets();});}
        if(page==0){int y=76;for(var action:List.of(Interaction.CARRY,Interaction.KISS,Interaction.HUG,Interaction.REST)){
            var option=CoupleClientConfig.POLICIES.get(action);
            button(x,y,w,Component.translatable(action.key()).getString()+": "+Component.translatable(CoupleClientConfig.preferences().policy(action).key()).getString(),()->{
                var current=CoupleClientConfig.preferences().policy(action);option.set(Consent.values()[(current.ordinal()+1)%Consent.values().length]);
                if(action==Interaction.CARRY)CoupleClientConfig.CARRY_REQUESTS.set(true);if(action==Interaction.KISS)CoupleClientConfig.KISS_REQUESTS.set(true);
                dirty=true;flush();rebuildWidgets();
            });y+=26;
        }}else if(page==1)friends(x,w);
        else if(page==2){
            int sx=x+w/2+4,sw=w/2-4,row=Math.max(17,Math.min(28,(height-116)/6));
            var options=List.of(CoupleClientConfig.HEAD,CoupleClientConfig.ARM,CoupleClientConfig.BANK,CoupleClientConfig.SIDE,CoupleClientConfig.UP,CoupleClientConfig.FORWARD);
            double[] lo={-20,20,-80,-.25,-.3,-.25},hi={65,110,-45,.25,.3,.35};String[] names={"head","arm","bank","side","up","forward"};
            for(int i=0;i<6;i++)addRenderableWidget(new PoseSlider(sx,72+i*row,sw,Math.min(20,row-1),names[i],options.get(i),lo[i],hi[i]));
            button(x,height-73,w/2-4,tr("reset_pose").getString(),()->{CoupleClientConfig.pose(PoseSettings.DEFAULT);dirty=true;rebuildWidgets();});
            button(x,height-98,(w/2-8)/2,"↶",()->angle-=.35f);button(x+(w/2-8)/2+4,height-98,(w/2-8)/2,"↷",()->angle+=.35f);
        }else {toggle(x,78,w,"sound",CoupleClientConfig.SOUND);toggle(x,106,w,"particles",CoupleClientConfig.PARTICLES);}
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x,height-29,w,20).build());
    }
    private void friends(int x,int w){
        var names=new LinkedHashMap<UUID,String>();
        for(String entry:CoupleClientConfig.TRUSTED.get()){int cut=entry.indexOf('|');var id=CoupleClientConfig.uuid(entry);if(id!=null)names.put(id,cut<0?id.toString().substring(0,8):entry.substring(cut+1));}
        if(minecraft.getConnection()!=null)for(var info:minecraft.getConnection().getOnlinePlayers())if(minecraft.player==null || !info.getProfile().getId().equals(minecraft.player.getUUID()))names.put(info.getProfile().getId(),info.getProfile().getName());
        int rows=Math.max(1,(height-133)/24);var entries=new ArrayList<>(names.entrySet());scroll=Math.clamp(scroll,0,Math.max(0,entries.size()-rows));
        for(int i=0;i<rows && i+scroll<entries.size();i++){var entry=entries.get(i+scroll);button(x,74+i*24,w,(CoupleClientConfig.trusted(entry.getKey())?"★ ":"☆ ")+entry.getValue(),()->{CoupleClientConfig.toggleTrusted(entry.getKey(),entry.getValue());rebuildWidgets();});}
        button(x,height-56,w/2-3,"↑",()->{scroll--;rebuildWidgets();});button(x+w/2+3,height-56,w/2-3,"↓",()->{scroll++;rebuildWidgets();});
    }
    private void toggle(int x,int y,int w,String key,ModConfigSpec.BooleanValue option){button(x,y,w,Component.translatable("setting.civitatum_couple."+key).getString()+": "+Component.translatable(option.get()?"options.on":"options.off").getString(),()->{option.set(!option.get());dirty=true;flush();rebuildWidgets();});}
    private final class PoseSlider extends AbstractSliderButton {
        private final String key;private final ModConfigSpec.DoubleValue option;private final double lo,hi;
        PoseSlider(int x,int y,int w,int h,String key,ModConfigSpec.DoubleValue option,double lo,double hi){super(x,y,w,h,Component.empty(),(option.get()-lo)/(hi-lo));this.key=key;this.option=option;this.lo=lo;this.hi=hi;updateMessage();}
        protected void updateMessage(){setMessage(Component.translatable("pose.civitatum_couple."+key).append(": "+String.format(Locale.ROOT,Math.abs(hi)>2?"%.0f°":"%.2f",lo+value*(hi-lo))));}
        protected void applyValue(){option.set(Math.rint((lo+value*(hi-lo))*100)/100);dirty=true;}
    }
    private void flush(){if(dirty){CoupleClientConfig.SPEC.save();ClientState.sendPreferences();dirty=false;}}
    @Override public void removed(){flush();PoseEngine.clearPreview();}
    @Override public void onClose(){flush();minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    public void pageForTest(int value){page=value;rebuildWidgets();}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        g.pose().pushPose();g.pose().translate(0,0,400);g.fill(0,0,width,height,0xFF16181F);
        g.drawCenteredString(font,title,width/2,14,0xFFF1E5D7);
        if(page==2)preview(g,partial);
        super.render(g,x,y,partial);
        String hint=page==0?tr("consent_hint").getString():page==1?tr("favorites_hint").getString():page==2?tr("pose_hint").getString():tr("effects_hint").getString();
        g.drawCenteredString(font,font.plainSubstrByWidth(hint,width-16),width/2,height-42,0xFFA9A3A0);g.flush();g.pose().popPose();
    }
    private void preview(GuiGraphics g,float partial){
        if(minecraft.player==null || minecraft.level==null)return;
        var other=target==null?null:minecraft.level.getPlayerByUUID(target);
        if(first==null){first=new RemotePlayer(minecraft.level,other==null?minecraft.player.getGameProfile():other.getGameProfile());second=new RemotePlayer(minecraft.level,minecraft.player.getGameProfile());}
        first.getAttribute(Attributes.SCALE).setBaseValue(other==null?minecraft.player.getScale():other.getScale());second.getAttribute(Attributes.SCALE).setBaseValue(minecraft.player.getScale());
        for(var p:List.of(first,second)){p.yBodyRot=p.yBodyRotO=180;p.setYRot(180);p.yRotO=180;p.yHeadRot=p.yHeadRotO=180;p.setXRot(0);p.xRotO=0;p.tickCount=minecraft.player.tickCount;}
        int px=Math.max(10,width/2-240),pw=Math.min(480,width-20)/2-4,top=70,bottom=height-106;
        float size=Math.max(22,(bottom-top-8)/2.6f/Math.max(first.getScale(),second.getScale()));
        PoseEngine.preview(first,second,CoupleClientConfig.pose());g.enableScissor(px,top,px+pw,bottom);
        try{var q=new org.joml.Quaternionf().rotateZ((float)Math.PI).rotateY(angle);InventoryScreen.renderEntityInInventory(g,px+pw/2f,bottom-5,size,new org.joml.Vector3f(),q,null,first);InventoryScreen.renderEntityInInventory(g,px+pw/2f,bottom-5,size,new org.joml.Vector3f(),q,null,second);}
        finally{g.disableScissor();PoseEngine.clearPreview();}
    }
}
