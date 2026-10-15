package team.civitatum.couple.test;

import java.util.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import team.civitatum.couple.*;
import team.civitatum.couple.client.*;
import team.civitatum.couple.core.*;
import team.civitatum.couple.network.*;
import team.civitatum.couple.server.*;

/** Integrated client/server exercise, including Emotecraft's actual nested bending libraries. */
@Mod(value="couple_test",dist=Dist.CLIENT)
public final class ClientSmoke {
    private int phase,ticks,checks,effects;private volatile UUID partner;private volatile String failure;private boolean hooked;private Vec3 origin;private RemotePlayer observer;
    public ClientSmoke(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private void check(boolean ok,String what){if(!ok)throw new IllegalStateException("CCI check: "+what);checks++;}
    private void next(int p){phase=p;ticks=0;}
    private void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();ticks++;
        if(!hooked){var previous=ClientBridge.kissEffect;ClientBridge.kissEffect=p->{effects++;previous.accept(p);};hooked=true;}
        if(failure!=null)throw new IllegalStateException(failure);
        if(ticks>1200)throw new IllegalStateException("CCI timeout phase "+phase);
        if(ticks%100==0)System.out.println("CCI_020_STAGE "+phase+" "+(mc.screen==null?"world":mc.screen.getClass().getSimpleName()));
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());}
        if(phase==0 && mc.screen instanceof TitleScreen && ticks>35){next(1);CreateWorldScreen.openFresh(mc,mc.screen);}
        else if(phase==1 && mc.screen instanceof CreateWorldScreen s && ticks>30){next(2);try{var method=CreateWorldScreen.class.getDeclaredMethod("onCreate");method.setAccessible(true);method.invoke(s);}catch(Exception ex){throw new IllegalStateException(ex);}}
        else if(phase==2 && mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen s && ticks>10){((net.minecraft.client.gui.components.Button)s.children().getFirst()).onPress();}
        else if(phase==2 && mc.player!=null && mc.screen==null){
            mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.renderDistance().set(4);mc.options.simulationDistance().set(4);mc.options.languageCode="ru_ru";mc.getLanguageManager().setSelected("ru_ru");mc.reloadResourcePacks();
            server(mc,()->setup(mc));next(3);
        }
        else if(phase==3 && partner!=null && mc.level.getPlayerByUUID(partner)!=null && ticks>50){
            check(!net.neoforged.fml.ModList.get().isLoaded("emotecraft") || PoseEngine.animator,"PlayerAnimator adapter from Emotecraft is active");
            var settings=new SettingsScreen(null,partner);mc.setScreen(settings);next(4);
        }
        else if(phase==4 && ticks>20){capture(mc,"01-consent.png");var s=(SettingsScreen)mc.screen;s.pageForTest(1);CoupleClientConfig.toggleTrusted(partner,"CCI_Companion");check(CoupleClientConfig.trusted(partner),"favorite added by UUID");next(5);}
        else if(phase==5 && ticks>20){capture(mc,"02-favorites.png");((SettingsScreen)mc.screen).pageForTest(2);next(6);}
        else if(phase==6 && ticks>25){capture(mc,"03-pose-preview.png");mc.screen.onClose();mc.setScreen(new RadialScreen(mc.level.getPlayerByUUID(partner)));next(7);}
        else if(phase==7 && ticks>15){capture(mc,"04-radial.png");mc.screen.onClose();camera(mc);server(mc,()->{var a=a(mc);var b=b(mc);var p=new PlayerPreferences();p.policies.put(Interaction.CARRY,Consent.ALLOW);CoupleServer.preferences(b,new CoupleNetwork.Preferences(p.json()));});next(8);}
        else if(phase==8 && ticks>15){PacketDistributor.sendToServer(new CoupleNetwork.Action(partner,Interaction.CARRY.ordinal()));next(9);}
        else if(phase==9 && CarryHooks.carrying(mc.player) && ticks>25){
            check(true,"auto-accepted carry synchronizes");var b=mc.level.getPlayerByUUID(partner);var f=PoseEngine.frame(b,0);check(f.bones.get("head").pitch()>0,"head tilts forward");check(f.bones.get("leftArm").roll()<-.8f,"left arm is further out");
            check(Math.abs(mc.player.getScale()-b.getScale())>.1,"different native scales synchronized");bends(mc,b);capture(mc,"05-carry-height-and-bends.png");
            PacketDistributor.sendToServer(new CoupleNetwork.Action(partner,Interaction.RELEASE.ordinal()));next(10);
        }
        else if(phase==10 && !CarryHooks.occupied(mc.player) && ticks>15){
            server(mc,()->{reset(mc);var p=new PlayerPreferences();for(var action:List.of(Interaction.KISS,Interaction.HUG,Interaction.REST))p.policies.put(action,Consent.ALLOW);CoupleServer.preferences(b(mc),new CoupleNetwork.Preferences(p.json()));});next(11);
        }
        else if(phase==11 && ticks>15){PacketDistributor.sendToServer(new CoupleNetwork.Action(partner,Interaction.KISS.ordinal()));next(12);}
        else if(phase==12 && SocialHooks.active(mc.player) && ticks>29){
            check(ClientState.social(mc.player.getUUID()).state().action()==Interaction.KISS.ordinal(),"kiss state synchronizes");check(PoseEngine.frame(mc.player,0).bones.get("rightArm").bend()<-.5f,"initiator reaches cheek with bent arm");bends(mc,mc.player);capture(mc,"06-kiss-cheek.png");next(13);
        }
        else if(phase==13 && !SocialHooks.active(mc.player) && ticks>35){check(effects==1,"kiss emits hearts/sound once at contact");server(mc,()->reset(mc));next(14);}
        else if(phase==14 && ticks>20){PacketDistributor.sendToServer(new CoupleNetwork.Action(partner,Interaction.HUG.ordinal()));next(15);}
        else if(phase==15 && SocialHooks.active(mc.player) && ticks>30){check(ClientState.social(mc.player.getUUID()).state().action()==Interaction.HUG.ordinal(),"hug state synchronizes");capture(mc,"07-hug.png");PacketDistributor.sendToServer(new CoupleNetwork.Action(partner,Interaction.RELEASE.ordinal()));next(16);}
        else if(phase==16 && !SocialHooks.active(mc.player) && ticks>20){check(mc.player.getAttributeValue(Attributes.MOVEMENT_SPEED)>0,"movement restored after hug");server(mc,()->reset(mc));next(17);}
        else if(phase==17 && ticks>65){PacketDistributor.sendToServer(new CoupleNetwork.Action(partner,Interaction.REST.ordinal()));next(18);}
        else if(phase==18 && SocialHooks.resting(mc.player) && ticks>30){check(mc.player.getBbHeight()<1.5,"resting dimensions synchronized");capture(mc,"08-rest-on-shoulder.png");server(mc,()->{b(mc).setShiftKeyDown(true);SocialSessions.tick();b(mc).setShiftKeyDown(false);});next(19);}
        else if(phase==19 && !SocialHooks.active(mc.player) && ticks>20){
            check(mc.player.getBbHeight()>1.7,"either player leaving restores dimensions");mc.setCameraEntity(mc.player);mc.options.hideGui=false;mc.options.guiScale().set(3);mc.resizeDisplay();var s=new SettingsScreen(null,partner);mc.setScreen(s);s.pageForTest(2);next(20);
        }
        else if(phase==20 && ticks>20){for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget w)check(w.getX()>=0 && w.getY()>=0 && w.getX()+w.getWidth()<=mc.screen.width && w.getY()+w.getHeight()<=mc.screen.height,"setting fits compact UI");capture(mc,"09-compact-preview.png");mc.screen.onClose();next(21);}
        else if(phase==21 && ticks>15){System.out.println("CCI_020_CLIENT_PASS checks="+checks+" emotecraft="+net.neoforged.fml.ModList.get().isLoaded("emotecraft")+" ccc="+net.neoforged.fml.ModList.get().isLoaded("civitatum"));phase=99;mc.stop();}
    }
    private ServerPlayer a(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private ServerPlayer b(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(partner);}
    private void server(Minecraft mc,Runnable run){mc.getSingleplayerServer().execute(()->{try{run.run();}catch(Throwable ex){ex.printStackTrace();failure="CCI server: "+ex;}});}
    private void setup(Minecraft mc)throws RuntimeException{
        var a=a(mc);var level=a.serverLevel();mc.getSingleplayerServer().setDifficulty(Difficulty.PEACEFUL,true);level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
        BlockPos base=a.blockPosition().above(4);origin=Vec3.atBottomCenterOf(base);
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){level.setBlockAndUpdate(base.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState());for(int y=0;y<=7;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.AIR.defaultBlockState());}
        var profile=new GameProfile(UUID.nameUUIDFromBytes("CCI_Companion".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"CCI_Companion");
        var b=new ServerPlayer(a.server,level,profile,ClientInformation.createDefault());var connection=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(connection);
        ChannelAttributes.setPayloadSetup(connection,ChannelAttributes.getPayloadSetup(a.connection.getConnection()));var type=ChannelAttributes.getConnectionType(a.connection.getConnection());ChannelAttributes.setConnectionType(connection,type);
        a.server.getPlayerList().placeNewPlayer(connection,b,new CommonListenerCookie(profile,0,ClientInformation.createDefault(),false,type));b.connection=new ServerChecks.Recorder(b);partner=b.getUUID();
        if(net.neoforged.fml.ModList.get().isLoaded("civitatum")){character(a,"male",2.1f);character(b,"female",1.6f);}else{a.getAttribute(Attributes.SCALE).setBaseValue(1.1666667);b.getAttribute(Attributes.SCALE).setBaseValue(.8888889);}
        reset(mc);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private void character(ServerPlayer p,String sex,float height){try{
        var dataClass=Class.forName("ru.teacraft.civitatum.CharacterData");var c=Class.forName("ru.teacraft.civitatum.RaceCatalog").getMethod("defaults",String.class,String.class).invoke(null,"human",sex);dataClass.getField("height").setFloat(c,height);
        var state=Class.forName("ru.teacraft.civitatum.RaceState").getConstructor(dataClass,int.class,long.class).newInstance(c,0,0L);
        p.setData((java.util.function.Supplier)Class.forName("ru.teacraft.civitatum.Civitatum").getField("STATE").get(null),state);
        Class.forName("ru.teacraft.civitatum.RaceService").getMethod("apply",ServerPlayer.class).invoke(null,p);Class.forName("ru.teacraft.civitatum.RaceNetwork").getMethod("sync",ServerPlayer.class).invoke(null,p);
    }catch(Exception ex){throw new IllegalStateException(ex);}}
    private void reset(Minecraft mc){var a=a(mc);var b=b(mc);CoupleServer.end(a,null);CoupleServer.end(b,null);a.teleportTo(origin.x,origin.y,origin.z);b.teleportTo(origin.x,origin.y,origin.z+1.5);for(var p:List.of(a,b)){p.setOnGround(true);p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;p.setDeltaMovement(Vec3.ZERO);p.setShiftKeyDown(false);p.yBodyRot=p.yBodyRotO=0;p.setYRot(0);}}
    private void camera(Minecraft mc){observer=new RemotePlayer(mc.level,new GameProfile(UUID.randomUUID(),"CCI_Camera"));observer.setPos(origin.add(3.2,.1,3.4));var center=origin.add(0,1.05,.5);var direction=center.subtract(observer.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(direction.z,direction.x))-90;float pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z)));observer.setYRot(yaw);observer.yRotO=yaw;observer.setXRot(pitch);observer.xRotO=pitch;observer.yBodyRot=observer.yBodyRotO=yaw;mc.setCameraEntity(observer);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=true;}
    private void bends(Minecraft mc,net.minecraft.world.entity.player.Player p){if(!net.neoforged.fml.ModList.get().isLoaded("bendylib"))return;try{var model=((net.minecraft.client.renderer.entity.player.PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(p)).getModel();var api=Class.forName("io.github.kosmx.bendylib.ModelPartAccessor");var cube=api.getMethod("getCuboid",net.minecraft.client.model.geom.ModelPart.class,int.class).invoke(null,model.rightArm,0);var active=Class.forName("io.github.kosmx.bendylib.MutableCuboid").getMethod("getActiveMutator").invoke(cube);check(active!=null,"real bendy-lib mutator is active on elbow");}catch(Exception ex){throw new IllegalStateException(ex);}}
    private void capture(Minecraft mc,String file){var dir=new java.io.File("../build/cci020-captures"+(net.neoforged.fml.ModList.get().isLoaded("emotecraft")?"-emotecraft":""));dir.mkdirs();net.minecraft.client.Screenshot.grab(dir,file,mc.getMainRenderTarget(),m->System.out.println("CCI_CAPTURE "+m.getString()));}
}
