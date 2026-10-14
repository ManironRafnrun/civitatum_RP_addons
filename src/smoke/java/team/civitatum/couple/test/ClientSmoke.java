package team.civitatum.couple.test;

import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeX11;
import team.civitatum.couple.CarryHooks;
import team.civitatum.couple.CarryGeometry;
import team.civitatum.couple.client.ClientState;
import team.civitatum.couple.client.RadialScreen;
import team.civitatum.couple.client.SettingsScreen;
import team.civitatum.couple.core.Interaction;
import team.civitatum.couple.network.ClientBridge;
import team.civitatum.couple.network.CoupleNetwork;
import team.civitatum.couple.server.CoupleServer;

/** Optional, isolated integrated-server test. Never included in the shipped jar. */
@Mod(value="couple_test",dist=Dist.CLIENT)
public final class ClientSmoke {
    private int phase,ticks,total,kisses;
    private volatile UUID partner;
    private volatile String serverFailure;
    private volatile boolean staged;
    private boolean hooked;
    public ClientSmoke() {
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();ticks++;total++;
        if(!hooked){var previous=ClientBridge.kissEffect;ClientBridge.kissEffect=p->{kisses++;previous.accept(p);};hooked=true;}
        if(total%200==0)System.out.println("COUPLE_CLIENT_STAGE "+phase+" "+(mc.screen==null?"world":mc.screen.getClass().getSimpleName()));
        if(serverFailure!=null)throw new IllegalStateException(serverFailure);
        if(total>9000)throw new IllegalStateException("COUPLE_CLIENT_TIMEOUT phase="+phase);
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());
        }
        if(phase==0&&mc.screen instanceof TitleScreen&&ticks>30){next(1);CreateWorldScreen.openFresh(mc,mc.screen);}
        else if(phase==1&&mc.screen instanceof CreateWorldScreen s&&ticks>30){
            next(2);
            try{var m=CreateWorldScreen.class.getDeclaredMethod("onCreate");m.setAccessible(true);m.invoke(s);}catch(Exception e){throw new IllegalStateException(e);}
        } else if(phase==2&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen s&&ticks>10){
            ((net.minecraft.client.gui.components.Button)s.children().getFirst()).onPress();
        } else if(phase==2&&mc.player!=null&&mc.screen==null){
            mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.renderDistance().set(4);mc.options.simulationDistance().set(4);
            mc.options.languageCode="ru_ru";mc.getLanguageManager().setSelected("ru_ru");mc.reloadResourcePacks();
            var server=mc.getSingleplayerServer();UUID self=mc.player.getUUID();
            server.execute(()->{
                try {
                    ServerPlayer a=server.getPlayerList().getPlayer(self);var level=a.serverLevel();
                    server.setDifficulty(Difficulty.PEACEFUL,true);level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                    BlockPos base=a.blockPosition().above(4);
                    for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){
                        level.setBlockAndUpdate(base.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState());
                        for(int y=0;y<=5;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.AIR.defaultBlockState());
                    }
                    a.teleportTo(base.getX()+0.5,base.getY(),base.getZ()+0.5);a.setGameMode(GameType.SURVIVAL);a.getAbilities().flying=false;a.setOnGround(true);
                    var profile=new GameProfile(UUID.nameUUIDFromBytes("CouplePartner".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"CouplePartner");
                    ServerPlayer b=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
                    Connection connection=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(connection);
                    ChannelAttributes.setPayloadSetup(connection,ChannelAttributes.getPayloadSetup(a.connection.getConnection()));
                    var type=ChannelAttributes.getConnectionType(a.connection.getConnection());
                    ChannelAttributes.setConnectionType(connection,type);
                    server.getPlayerList().placeNewPlayer(connection,b,new CommonListenerCookie(profile,0,ClientInformation.createDefault(),false,type));
                    b.connection=new ServerChecks.Recorder(b);
                    b.teleportTo(a.getX(),a.getY(),a.getZ()+1.5);b.setOnGround(true);b.setGameMode(GameType.SURVIVAL);b.setYRot(180);b.yBodyRot=180;
                    partner=b.getUUID();
                } catch(Throwable e){serverFailure="CLIENT SERVER SETUP: "+e; e.printStackTrace();}
            });next(3);
        } else if(phase==3&&partner!=null&&mc.level.getPlayerByUUID(partner)!=null&&ticks>80){
            check(mc.player.getBbHeight()>1.7,"normal player dimensions before carry");
            aimAtPartner(mc);next(4);
        } else if(phase==4&&ticks>10){
            aimAtPartner(mc);
            if(mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult h&&h.getEntity().getUUID().equals(partner)){
                key(mc,true);next(5);
            }else if(ticks>100)throw new IllegalStateException("COUPLE_CLIENT_FAIL target not aimed: self="+mc.player.position()+" partner="+mc.level.getPlayerByUUID(partner).position()+" hit="+mc.hitResult);
        }
        else if(phase==5&&mc.screen instanceof RadialScreen&&ticks>12){
            cursor(mc,mc.getWindow().getScreenWidth()/2.0,mc.getWindow().getScreenHeight()/2.0-145);next(6);
        } else if(phase==6&&ticks>15){
            check(mc.screen instanceof RadialScreen,"hold key opens radial wheel on aimed player");capture(mc,"01-radial.png");
            cursor(mc,mc.getWindow().getScreenWidth()/2.0,mc.getWindow().getScreenHeight()/2.0);next(7);
        } else if(phase==7&&ticks>5){key(mc,false);next(8);}
        else if(phase==8&&ticks>15){
            check(mc.screen==null,"release key in center cancels wheel");
            mc.setScreen(new SettingsScreen(null));next(9);
        } else if(phase==9&&ticks>15){
            capture(mc,"02-settings.png");mc.setScreen(null);
            server(mc,()->{
                var a=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var b=mc.getSingleplayerServer().getPlayerList().getPlayer(partner);
                a.setOnGround(true);b.setOnGround(true);
                CoupleServer.request(b,new CoupleNetwork.Action(a.getUUID(),Interaction.CARRY.ordinal()));
            });next(10);
        } else if(phase==10&&ClientState.invitation!=null&&ticks>12){
            capture(mc,"03-invitation.png");key(mc,true,'y');next(11);
        } else if(phase==11&&CarryHooks.carried(mc.player)&&mc.player.isPassenger()&&ticks>25){
            key(mc,false,'y');
            check(ClientState.invitation==null,"accept key clears the invitation");
            check(mc.player.getEyeHeight()<1,"client carried eye height synced");
            var camera=mc.gameRenderer.getMainCamera();
            check(camera.getPosition().distanceTo(CarryGeometry.head(mc.player,CarryHooks.carrier(mc.player),1))<0.25,"passenger camera follows the head anchor");
            check(camera.getUpVector().y()>0.98,"passenger camera has a level horizon");
            capture(mc,"04-carried-first-person.png");mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.player.setYRot(180);mc.player.setXRot(9);next(12);
        } else if(phase==12&&ticks>25){
            capture(mc,"05-carried-third-person.png");
            verifyCarryPose(mc);
            mc.player.setYRot(90);mc.player.setXRot(7);next(22);
        } else if(phase==22&&ticks>20){
            capture(mc,"11-carried-side.png");mc.player.setYRot(0);next(23);
        } else if(phase==23&&ticks>20){
            capture(mc,"12-carried-back.png");mc.player.setYRot(270);next(24);
        } else if(phase==24&&ticks>20){
            capture(mc,"13-carried-other-side.png");
            key(mc,true,0xffe1);next(18);
        } else if(phase==18&&!CarryHooks.occupied(mc.player)&&!mc.player.isPassenger()&&ticks>10){
            key(mc,false,0xffe1);check(true,"Shift exits carrying through the real client");
            server(mc,()->{
                var a=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var b=mc.getSingleplayerServer().getPlayerList().getPlayer(partner);
                CoupleServer.end(a,null);b.teleportTo(a.getX(),a.getY(),a.getZ()+1.4);a.setOnGround(true);b.setOnGround(true);a.yBodyRot=0;a.setYRot(0);
                a.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));b.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));
                if(!CoupleServer.beginCarry(a,b))throw new IllegalStateException("cannot reverse carrying roles");
            });mc.player.setYRot(0);mc.player.setXRot(7);next(13);
        } else if(phase==13&&CarryHooks.carrying(mc.player)&&ticks>30){
            check(!mc.player.isPassenger(),"carrier retains own movement");
            check(mc.level.getPlayerByUUID(partner).getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS),"passenger armor reaches the real client");capture(mc,"06-carrying-armor.png");
            next(25);
        } else if(phase==25&&ticks>15){
            capture(mc,"14-carrying-armor.png");
            mc.setScreen(new RadialScreen(mc.level.getPlayerByUUID(partner)));
            mc.screen.mouseClicked(mc.screen.width/2.0+65,mc.screen.height/2.0+37,0);
            check(mc.screen==null,"click selects the current sector before another render");next(14);
        } else if(phase==14&&ticks>15){
            server(mc,()->{var b=mc.getSingleplayerServer().getPlayerList().getPlayer(partner);var r=((ServerChecks.Recorder)b.connection).lastInvite();CoupleServer.decide(b,new CoupleNetwork.Decision(r.request(),true));});next(15);
        } else if(phase==15&&kisses>0&&ticks>3){
            check(kisses==1,"kiss effect arrives exactly once through network");capture(mc,"07-kiss.png");
            PacketDistributor.sendToServer(new CoupleNetwork.Action(partner,Interaction.RELEASE.ordinal()));next(16);
        } else if(phase==16&&!CarryHooks.occupied(mc.player)&&ticks>20){
            check(mc.player.getBbHeight()>1.7,"client dimensions restored after release");
            mc.options.setCameraType(CameraType.FIRST_PERSON);capture(mc,"08-released.png");
            server(mc,()->{var s=mc.getSingleplayerServer();var b=s.getPlayerList().getPlayer(partner);CoupleServer.end(b,null);s.getPlayerList().remove(b);b.discard();staged=true;});next(17);
        } else if(phase==17&&staged&&ticks>10){
            mc.options.guiScale().set(3);GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,720);mc.resizeDisplay();
            mc.setScreen(new SettingsScreen(null));next(19);
        } else if(phase==19&&ticks>15){
            mc.getToasts().clear();
            check(mc.screen.width==320&&mc.screen.height==240,"minimum GUI 320x240");
            for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget w)
                check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=320&&w.getY()+w.getHeight()<=240,"compact setting remains inside screen");
            capture(mc,"09-compact-settings.png");key(mc,true);
            mc.setScreen(new RadialScreen(partner,net.minecraft.network.chat.Component.literal("Второй игрок"),false));
            cursor(mc,480,360-120);next(20);
        } else if(phase==20&&ticks==5){cursor(mc,480,360-120);}
        else if(phase==20&&ticks>15){
            check(mc.screen instanceof RadialScreen,"compact radial remains open while holding F9");capture(mc,"10-compact-radial.png");
            cursor(mc,480,360);key(mc,false);next(21);
        } else if(phase==21&&ticks>15){
            check(mc.screen==null,"compact radial center cancels on key release");
            phase=99;System.out.println("COUPLE_CLIENT_ALL_PASS");mc.stop();
        }
    }
    private void server(Minecraft mc,Runnable run){mc.getSingleplayerServer().execute(()->{try{run.run();}catch(Throwable e){serverFailure="COUPLE SERVER CHECK: "+e;e.printStackTrace();}});}
    private void aimAtPartner(Minecraft mc){
        var delta=mc.level.getPlayerByUUID(partner).getEyePosition().subtract(mc.player.getEyePosition());
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));
    }
    private void verifyCarryPose(Minecraft mc) {
        var carried=mc.player;var carrier=CarryHooks.carrier(carried);
        float oldYaw=carrier.yBodyRot,oldPrevious=carrier.yBodyRotO;
        try {
            var rotation=net.minecraft.client.renderer.entity.player.PlayerRenderer.class.getDeclaredMethod("setupRotations",
                net.minecraft.client.player.AbstractClientPlayer.class,com.mojang.blaze3d.vertex.PoseStack.class,float.class,float.class,float.class,float.class);
            var scaling=net.minecraft.client.renderer.entity.player.PlayerRenderer.class.getDeclaredMethod("scale",
                net.minecraft.client.player.AbstractClientPlayer.class,com.mojang.blaze3d.vertex.PoseStack.class,float.class);
            rotation.setAccessible(true);scaling.setAccessible(true);
            for(var entry:mc.getEntityRenderDispatcher().getSkinMap().entrySet()) {
                var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)entry.getValue();
                var model=renderer.getModel();
                for(float yaw:new float[]{0,90,180,270}) {
                    carrier.yBodyRot=carrier.yBodyRotO=yaw;
                    model.setupAnim(carried,0,0,0,135,-35);
                    var pose=new com.mojang.blaze3d.vertex.PoseStack();
                    float scale=carried.getScale();pose.scale(scale,scale,scale);
                    rotation.invoke(renderer,carried,pose,0F,yaw,1F,scale);
                    pose.scale(-1,-1,1);scaling.invoke(renderer,carried,pose,1F);pose.translate(0,-1.501,0);
                    pose.pushPose();model.body.translateAndRotate(pose);
                    var face=pose.last().pose().transformDirection(new org.joml.Vector3f(0,0,-1)).normalize();pose.popPose();
                    check(face.y()>0.8,"face up for "+entry.getKey()+" carrier yaw "+yaw);
                    pose.pushPose();model.head.translateAndRotate(pose);
                    var eye=pose.last().pose().transformPosition(new org.joml.Vector3f(0,-4F/16F,-4.1F/16F));pose.popPose();
                    var actual=CarryGeometry.anchor(carrier,1).add(eye.x,eye.y,eye.z);
                    check(actual.distanceTo(CarryGeometry.head(carried,carrier,1))<0.0001,"camera matches rendered head for "+entry.getKey()+" yaw "+yaw);
                    check(Math.abs(model.rightArm.y-model.rightArm.getInitialPose().y)<0.001,"native arm pivot preserved for "+entry.getKey());
                }
            }
        } catch(ReflectiveOperationException e) {throw new IllegalStateException("carry pose verification failed",e);}
        finally {carrier.yBodyRot=oldYaw;carrier.yBodyRotO=oldPrevious;}
    }
    private void next(int p){phase=p;ticks=0;System.out.println("COUPLE_CLIENT_NEXT "+p);}
    private void check(boolean c,String name){if(!c)throw new IllegalStateException("COUPLE_CLIENT_FAIL "+name);System.out.println("COUPLE_CLIENT_PASS "+name);}
    private void capture(Minecraft mc,String name){var dir=new java.io.File("../build/client-captures");dir.mkdirs();net.minecraft.client.Screenshot.grab(dir,name,mc.getMainRenderTarget(),c->System.out.println("COUPLE_CAPTURE "+name+" "+c.getString()));}
    private void cursor(Minecraft mc,double x,double y){
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),x,y);
        try{var method=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);method.setAccessible(true);method.invoke(mc.mouseHandler,mc.getWindow().getWindow(),x,y);}
        catch(Exception ex){throw new IllegalStateException("test cursor update failed",ex);}
    }

    public interface Xlib extends Library {
        Xlib INSTANCE=Native.load("X11",Xlib.class);
        int XKeysymToKeycode(Pointer display,NativeLong keysym);
        NativeLong XDefaultRootWindow(Pointer display);
        int XSendEvent(Pointer display,NativeLong window,int propagate,NativeLong mask,Pointer event);
        int XFlush(Pointer display);
    }
    @Structure.FieldOrder({"type","serial","send_event","display","window","root","subwindow","time","x","y","x_root","y_root","state","keycode","same_screen"})
    public static class KeyEvent extends Structure {
        public int type;public NativeLong serial;public int send_event;public Pointer display;public NativeLong window,root,subwindow,time;
        public int x,y,x_root,y_root,state,keycode,same_screen;
        public KeyEvent(Pointer pointer){super(pointer);}
    }
    private void key(Minecraft mc,boolean down){ key(mc,down,0xffc6); }
    private void key(Minecraft mc,boolean down,int keysym){
        Pointer display=new Pointer(GLFWNativeX11.glfwGetX11Display());NativeLong win=new NativeLong(GLFWNativeX11.glfwGetX11Window(mc.getWindow().getWindow()));
        Memory mem=new Memory(192);mem.clear();KeyEvent e=new KeyEvent(mem);
        e.type=down?2:3;e.serial=new NativeLong(0);e.send_event=1;e.display=display;e.window=win;e.root=Xlib.INSTANCE.XDefaultRootWindow(display);
        e.subwindow=new NativeLong(0);e.time=new NativeLong(0);e.x=100;e.y=100;e.x_root=100;e.y_root=100;e.same_screen=1;
        e.keycode=Xlib.INSTANCE.XKeysymToKeycode(display,new NativeLong(keysym));e.write();
        Xlib.INSTANCE.XSendEvent(display,win,1,new NativeLong(down?1:2),mem);Xlib.INSTANCE.XFlush(display);
    }
}
