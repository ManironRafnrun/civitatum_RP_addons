package ru.teacraft.civitatum.smoke;

import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.teacraft.civitatum.*;
import ru.teacraft.civitatum.client.*;

/** Real-client UI, pose, compositing, command and item roundtrips; excluded from the release. */
@Mod(value="civitatum_smoke",dist=Dist.CLIENT)
public final class SmokeClient {
    private int phase,ticks,checks;
    private boolean oldHide;
    public SmokeClient(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private void tick(ClientTickEvent.Post e){
        var game=Minecraft.getInstance();ticks++;
        if(phase>2 && ticks>600)throw new IllegalStateException("CCC timed out in phase "+phase);
        if(ticks%100==0)System.out.println("CCC_PHASE "+phase+" "+(game.screen==null?"world":game.screen.getClass().getSimpleName()));
        if(game.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){game.options.onboardAccessibility=false;game.options.save();game.setScreen(new TitleScreen());}
        if(phase==0 && game.screen instanceof TitleScreen && ticks>50){phase=1;ticks=0;CreateWorldScreen.openFresh(game,game.screen);}
        else if(phase==1 && game.screen instanceof CreateWorldScreen screen && ticks>25){phase=2;ticks=0;try{var m=CreateWorldScreen.class.getDeclaredMethod("onCreate");m.setAccessible(true);m.invoke(screen);}catch(Exception ex){throw new IllegalStateException(ex);}}
        else if(phase==2 && game.player!=null && game.level!=null && game.screen==null){
            game.options.guiScale().set(2);game.resizeDisplay();oldHide=game.options.hideGui;game.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            command(game,"civitatum race choose");next(3);
        }
        else if(phase==3 && game.screen instanceof CharacterScreen s && ticks>45){
            check(game.options.getCameraType()==CameraType.FIRST_PERSON,"editor forces first person");check(game.options.hideGui,"HUD hidden");capture(game,"01-type-and-world.png");
            click(s,870,211);check(s.draft().female() && s.draft().armModel.equals("slim"),"female button selects Slim");click(s,425,516);check(!s.draft().female(),"type reset restores sex");click(s,870,211);check(s.draft().female(),"sex selectable after reset");s.tabForTest(9);s.zoomForTest(2.6f);next(4);
        }
        else if(phase==4 && game.screen instanceof CharacterScreen s && ticks>35){
            capture(game,"02-eyes-original.png");click(s,897,317);release(s,897,317);check(s.draft().eyeHeight==2,"eye slider input");s.draft().hair=3;next(5);
        }
        else if(phase==5 && game.screen instanceof CharacterScreen s && ticks>35){
            capture(game,"03-eyes-raised.png");compositing(game,s);AppearanceChecks.run();s.draft().eyeHeight=0;s.zoomForTest(1);s.draft().hairAsset="ombre_wreath";s.tabForTest(3);next(6);
        }
        else if(phase==6 && game.screen instanceof CharacterScreen s && ticks>35){
            capture(game,"04-hair-original.png");click(s,727,273);click(s,762,340);click(s,797,407);
            check(s.draft().hairMain==1 && s.draft().hairDecor1==2 && s.draft().hairDecor2==3,"three hair color controls");next(7);
        }
        else if(phase==7 && game.screen instanceof CharacterScreen s && ticks>35){capture(game,"05-hair-three-channels.png");s.tabForTest(6);set(s,"garmentGroup",4);next(8);}
        else if(phase==8 && game.screen instanceof CharacterScreen s && ticks>35){capture(game,"06-clothing.png");click(s,727,187);check(s.draft().naked,"underwear checkbox");next(9);}
        else if(phase==9 && game.screen instanceof CharacterScreen s && ticks>35){capture(game,"07-underwear.png");s.tabForTest(5);s.draft().bust=0;s.angleForTest(-1.15f);next(10);}
        else if(phase==10 && game.screen instanceof CharacterScreen s && ticks>35){capture(game,"08-bust-zero.png");click(s,897,209);release(s,897,209);check(s.draft().bust==.8f,"bust slider input");next(11);}
        else if(phase==11 && game.screen instanceof CharacterScreen s && ticks>35){capture(game,"09-bust-full.png");s.selectForTest("forest_algnor","female");s.draft().tail=3;s.tabForTest(7);s.angleForTest(-.8f);next(12);}
        else if(phase==12 && game.screen instanceof CharacterScreen s && ticks>35){capture(game,"10-algnor-legs.png");s.selectForTest("human","female");s.tabForTest(11);s.draft().birthsign="lady";s.draft().name="Арина";s.draft().hairAsset="ponytail_streak";s.draft().hairDecor1=3;next(13);}
        else if(phase==13 && game.screen instanceof CharacterScreen s && ticks>35){capture(game,"11-birthsign.png");s.stageForTest(3);next(14);}
        else if(phase==14 && game.screen instanceof CharacterScreen s && ticks>35){capture(game,"12-confirmation.png");s.stageForTest(0);next(26);}
        else if(phase==26 && game.screen instanceof CharacterScreen s && ticks>15){capture(game,"12b-back-from-confirmation.png");s.stageForTest(3);next(27);}
        else if(phase==27 && game.screen instanceof CharacterScreen s && ticks>15){click(s,560,390);next(15);}
        else if(phase==15 && game.screen==null && ticks>50){
            var c=ClientCharacters.get(game.player);check(c.selected && c.female() && c.name.equals("Арина") && c.birthsign.equals("lady"),"creation server roundtrip");
            check(c.hairAsset.equals("ponytail_streak") && c.hairDecor1==3,"hair synchronized");check(game.player.getMaxHealth()==24,"birthsign bonus applied");
            check(game.options.getCameraType()==CameraType.THIRD_PERSON_BACK,"camera restored");check(game.options.hideGui==oldHide,"HUD restored");
            check(!net.neoforged.fml.ModList.get().isLoaded("wildfire_gender") || FemaleGenderBridge.available(),"FGM bridge available when loaded");
            command(game,"civitatum race naked true");next(16);
        }
        else if(phase==16 && ticks>35){check(ClientCharacters.get(game.player).naked,"naked true synchronized");command(game,"civitatum race naked false");next(17);}
        else if(phase==17 && ticks>35){check(!ClientCharacters.get(game.player).naked,"naked false synchronized");command(game,"civitatum race looks");next(18);}
        else if(phase==18 && game.screen instanceof CharacterScreen s && ticks>35){
            check(s.mode()==EditorMode.FULL,"looks opens full editor");s.draft().sex="male";s.draft().bust=0;s.draft().armModel="classic";s.draft().height=1.9f;s.draft().birthsign="steed";s.draft().eyeHeight=1;
            AppearanceCatalog.outfit(s.draft(),"ruby_sash");PacketDistributor.sendToServer(new RaceNetwork.Serverbound(0,s.tokenForTest(),RaceCatalog.validate(s.draft()).json()));next(19);
        }
        else if(phase==19 && game.screen==null && ticks>45){
            var c=ClientCharacters.get(game.player);check(c.sex.equals("male") && c.height==1.9f && c.birthsign.equals("steed"),"full editor changes body and sex");
            check(game.player.getMaxHealth()==20,"previous birthsign removed");
            game.getSingleplayerServer().execute(()->{var p=serverPlayer(game);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().add(new net.minecraft.world.item.ItemStack(Civitatum.MIRROR.get()));RaceService.openCosmetic(p,Civitatum.MIRROR.get());});next(20);
        }
        else if(phase==20 && game.screen instanceof CharacterScreen s && ticks>35){
            check(s.mode()==EditorMode.COSMETIC,"mirror opens cosmetic editor");capture(game,"13-cosmetic-editor.png");s.draft().height=1.65f;
            PacketDistributor.sendToServer(new RaceNetwork.Serverbound(0,s.tokenForTest(),RaceCatalog.validate(s.draft()).json()));next(21);
        }
        else if(phase==21 && game.screen instanceof CharacterScreen s && ticks>35){
            check(ClientCharacters.get(game.player).height==1.9f,"forged body change rejected");s.draft().height=1.9f;s.draft().hairAsset="black_long";
            PacketDistributor.sendToServer(new RaceNetwork.Serverbound(0,s.tokenForTest(),RaceCatalog.validate(s.draft()).json()));next(22);
        }
        else if(phase==22 && game.screen==null && ticks>45){
            check(ClientCharacters.get(game.player).hairAsset.equals("black_long"),"mirror cosmetic save");
            game.getSingleplayerServer().execute(()->{var p=serverPlayer(game);if(p.getInventory().contains(new net.minecraft.world.item.ItemStack(Civitatum.MIRROR.get())))throw new IllegalStateException("Mirror not consumed");RaceService.open(p,false,false);});
            game.options.guiScale().set(3);game.resizeDisplay();next(23);
        }
        else if(phase==23 && game.screen instanceof CharacterScreen s && ticks>35){capture(game,"14-compact-editor.png");s.onClose();next(24);}
        else if(phase==24 && game.screen==null && ticks>35){
            check(game.options.getCameraType()==CameraType.THIRD_PERSON_BACK && game.options.hideGui==oldHide,"cancel restores view");
            System.out.println("CCC_041_CLIENT_PASS checks="+checks+" female_adapter="+FemaleGenderBridge.available());phase=25;game.stop();
        }
    }
    private static net.minecraft.server.level.ServerPlayer serverPlayer(Minecraft g){return g.getSingleplayerServer().getPlayerList().getPlayer(g.player.getUUID());}
    private void command(Minecraft g,String command){g.getSingleplayerServer().execute(()->g.getSingleplayerServer().getCommands().performPrefixedCommand(serverPlayer(g).createCommandSourceStack().withPermission(2),command));}
    private void next(int value){phase=value;ticks=0;}
    private void check(boolean good,String message){if(!good)throw new IllegalStateException("CCC check: "+message);checks++;}
    private void click(CharacterScreen s,int x,int y){float zoom=Math.min(s.width/960f,s.height/540f);s.mouseClicked((s.width-960*zoom)/2+x*zoom,(s.height-540*zoom)/2+y*zoom,0);}
    private void release(CharacterScreen s,int x,int y){float zoom=Math.min(s.width/960f,s.height/540f);s.mouseReleased((s.width-960*zoom)/2+x*zoom,(s.height-540*zoom)/2+y*zoom,0);}
    private void set(CharacterScreen s,String name,Object v){try{var f=CharacterScreen.class.getDeclaredField(name);f.setAccessible(true);f.set(s,v);}catch(Exception e){throw new IllegalStateException(e);}}
    private void compositing(Minecraft g,CharacterScreen s){
        var original=g.player.getSkin();var c=RaceCatalog.defaults("human","female");c.hairStyle=0;c.hairAsset="";c.naked=true;c.wardrobeEnabled=false;c.eyeHeight=0;
        var skin=SkinTemplates.skin(c,original);check(skin.model()==PlayerSkin.Model.SLIM,"renderer uses Slim");
        var image=((DynamicTexture)g.getTextureManager().getTexture(skin.texture())).getPixels();int iris=image.getPixelRGBA(10,13),base=image.getPixelRGBA(10,11);
        c.eyeHeight=2;var shifted=SkinTemplates.skin(c,original);var other=((DynamicTexture)g.getTextureManager().getTexture(shifted.texture())).getPixels();
        check(other.getPixelRGBA(10,11)==iris && other.getPixelRGBA(10,13)!=iris,"eye height moves eyes and clears old location");
        c.armModel="classic";check(SkinTemplates.skin(c,original).model()==PlayerSkin.Model.WIDE,"Classic switch");
        c.hairAsset="ombre_wreath";c.hairMain=2;var tinted=SkinTemplates.skin(c,original);var t=((DynamicTexture)g.getTextureManager().getTexture(tinted.texture())).getPixels();
        check(t.getPixelRGBA(9,10)!=other.getPixelRGBA(9,10),"eye contour follows main hair color");
        c.eyeHeight=0;c.armModel="slim";c.naked=false;AppearanceCatalog.outfit(c,"many_layers");var dressed=SkinTemplates.skin(c,original);c.naked=true;var bare=SkinTemplates.skin(c,original);
        var d=((DynamicTexture)g.getTextureManager().getTexture(dressed.texture())).getPixels();var b=((DynamicTexture)g.getTextureManager().getTexture(bare.texture())).getPixels();
        boolean changed=false;for(int y=20;y<32;y++)for(int x=20;x<28;x++)changed|=d.getPixelRGBA(x,y)!=b.getPixelRGBA(x,y);check(changed,"decorative costume layer toggles");
        c.usePlayerSkin=true;check(SkinTemplates.skin(c,original).model()==PlayerSkin.Model.SLIM,"own skin with selectable Slim");
        check(IdleAnimation.value("head","yaw",15)!=IdleAnimation.value("head","yaw",200),"authored idle moves");
        System.out.println("CCC_041_COMPOSITOR_PASS");
    }
    private void capture(Minecraft game,String name){var dir=new java.io.File("../build/ccc041-captures"+(net.neoforged.fml.ModList.get().isLoaded("wildfire_gender")?"-female":""));dir.mkdirs();net.minecraft.client.Screenshot.grab(dir,name,game.getMainRenderTarget(),m->System.out.println("CCC_CAPTURE "+m.getString()));}
}
