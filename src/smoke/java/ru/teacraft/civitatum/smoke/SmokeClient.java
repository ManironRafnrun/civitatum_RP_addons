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
    private float smallPreviewSize;
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
            game.getSingleplayerServer().execute(()->RaceService.open(serverPlayer(game),true,false));next(42);
        }
        else if(phase==42 && game.screen instanceof CharacterScreen s && ticks>20){
            newPixels(game);s.selectForTest("human","female");s.draft().height=1.6f;s.draft().hairAsset="hc_blonde";s.tabForTest(8);s.zoomForTest(1);next(43);
        }
        else if(phase==43 && game.screen instanceof CharacterScreen s && ticks>20){
            smallPreviewSize=s.previewSizeForTest();heightTransform(game,s);capture(game,"15-height-160.png");s.draft().height=1.9f;next(44);
        }
        else if(phase==44 && game.screen instanceof CharacterScreen s && ticks>20){
            check(Math.abs(smallPreviewSize-s.previewSizeForTest())<.001,"changing draft height never auto-fits it away");heightTransform(game,s);capture(game,"16-height-190.png");
            s.draft().hairAsset="hc_tangled";s.draft().hairMain=2;s.draft().hairDecor1=0;s.tabForTest(3);next(45);
        }
        else if(phase==45 && game.screen instanceof CharacterScreen s && ticks>20){
            capture(game,"17-tangled-linked-shades.png");click(s,760,362);check(s.draft().hairDecor1==2,"linked shade swatch input");s.draft().hairAsset="hc_celestial";s.draft().hairMain=5;s.draft().hairDecor1=8;s.draft().hairDecor2=9;next(46);
        }
        else if(phase==46 && game.screen instanceof CharacterScreen s && ticks>20){
            capture(game,"18-celestial-three-colors.png");s.selectForTest("dwarf","female");s.draft().hairAsset="hc_medieval_male";s.draft().beard=5;s.draft().mustache=2;s.draft().hairMain=2;s.tabForTest(10);s.zoomForTest(2.1f);s.angleForTest(0);next(47);
        }
        else if(phase==47 && game.screen instanceof CharacterScreen s && ticks>20){
            capture(game,"19-dwarf-woman-textured-beard.png");check(RaceCatalog.beardAllowed(s.draft()),"dwarf women have beard controls");
            PacketDistributor.sendToServer(new RaceNetwork.Serverbound(0,s.tokenForTest(),RaceCatalog.validate(s.draft()).json()));next(48);
        }
        else if(phase==48 && game.screen==null && ticks>30){
            var c=ClientCharacters.get(game.player);check(c.race.equals("dwarf") && c.female() && c.beard==5 && c.mustache==2 && c.hairAsset.equals("hc_medieval_male"),"new hair, beard and mustache roundtrip through server");
            command(game,"civitatum race looks");next(49);
        }
        else if(phase==49 && game.screen instanceof CharacterScreen s && ticks>20){
            s.selectForTest("human","female");s.draft().beard=5;s.draft().mustache=2;s.tabForTest(10);s.zoomForTest(2.1f);next(50);
        }
        else if(phase==50 && game.screen instanceof CharacterScreen s && ticks>20){
            check(!RaceCatalog.beardAllowed(s.draft()),"human women cannot choose facial hair");capture(game,"20-human-woman-no-beard.png");s.onClose();next(51);
        }
        else if(phase==51 && game.screen==null && ticks>20){
            System.out.println("CCC_042_CLIENT_PASS checks="+checks+" female_adapter="+FemaleGenderBridge.available());phase=99;game.stop();
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
    private void heightTransform(Minecraft g,CharacterScreen s){try{
        var ghost=new net.minecraft.client.player.RemotePlayer(g.level,g.player.getGameProfile());
        ClientCharacters.preview=s.draft();ClientCharacters.previewEntity=ghost;
        var renderer=g.getEntityRenderDispatcher().getRenderer(ghost);var stack=new com.mojang.blaze3d.vertex.PoseStack();
        var method=net.minecraft.client.renderer.entity.player.PlayerRenderer.class.getDeclaredMethod("scale",net.minecraft.client.player.AbstractClientPlayer.class,com.mojang.blaze3d.vertex.PoseStack.class,float.class);method.setAccessible(true);method.invoke(renderer,ghost,stack,0f);
        float height=new org.joml.Vector3f(0,1.8f,0).mulDirection(stack.last().pose()).length();
        check(Math.abs(height-s.draft().height*.9375f)<.001,"real PlayerRenderer applies draft height before confirmation");
    }catch(Exception ex){throw new IllegalStateException(ex);}finally{ClientCharacters.preview=null;ClientCharacters.previewEntity=null;}}
    private void newPixels(Minecraft g){
        outerHair();
        var source=g.player.getSkin();var c=RaceCatalog.defaults("human","male");c.hairAsset="";c.beard=0;c.mustache=0;
        var clean=SkinTemplates.skin(c,source);c.beard=2;var bearded=SkinTemplates.skin(c,source);
        var a=((DynamicTexture)g.getTextureManager().getTexture(clean.texture())).getPixels();var b=((DynamicTexture)g.getTextureManager().getTexture(bearded.texture())).getPixels();
        check(!clean.texture().equals(bearded.texture()) && a.getPixelRGBA(11,15)!=b.getPixelRGBA(11,15),"beard is baked into cached skin, not a geometry layer");
        c.mustache=2;var mustache=SkinTemplates.skin(c,source);check(!mustache.texture().equals(bearded.texture()),"mustache has an independent texture cache key");
        c=RaceCatalog.defaults("human","female");c.hairAsset="";var woman=SkinTemplates.skin(c,source);c.beard=5;c.mustache=2;check(woman.texture().equals(SkinTemplates.skin(c,source).texture()) || equalTexture(g,woman,SkinTemplates.skin(c,source)),"forbidden beard is never rendered on human woman");
        int checked=0;
        for(var h:AppearanceCatalog.hair())if(h.id().startsWith("hc_"))for(String model:java.util.List.of("classic","slim")){
            c=RaceCatalog.defaults("human","female");c.hairAsset=h.id();c.armModel=model;c.hairMain=5;
            var skin=SkinTemplates.skin(c,source);var texture=((DynamicTexture)g.getTextureManager().getTexture(skin.texture())).getPixels();check(texture.getWidth()==64,"new hair renders in "+model);checked++;
        }
        System.out.println("CCC_042_NEW_HAIR_RENDER_PASS variants="+checked);
    }
    private void outerHair(){
        var c=RaceCatalog.defaults("human","female");c.hairAsset="hc_tangled";c.hairMain=2;
        try(var clear=new com.mojang.blaze3d.platform.NativeImage(64,64,true);var clothed=new com.mojang.blaze3d.platform.NativeImage(64,64,true)){
            for(int y=0;y<64;y++)for(int x=0;x<64;x++)clothed.setPixelRGBA(x,y,0xFF3377BB);
            var asset=AppearanceCatalog.hair(c.hairAsset);AppearanceTextures.applyHair(clear,asset,"slim",c);AppearanceTextures.applyHair(clothed,asset,"slim",c);int samples=0;
            for(int y=20;y<32;y++)for(int x=16;x<40;x++)if((clear.getPixelRGBA(x,y)>>>24)==255 && (clear.getPixelRGBA(x,y+16)>>>24)==0){check(clothed.getPixelRGBA(x,y+16)==clear.getPixelRGBA(x,y),"base-UV hair covers outer clothing without a new model");samples++;}
            check(samples>0,"long hairstyle exercises jacket coverage");
        }
        try(var clear=new com.mojang.blaze3d.platform.NativeImage(64,64,true);var clothed=new com.mojang.blaze3d.platform.NativeImage(64,64,true)){
            for(int y=0;y<64;y++)for(int x=0;x<64;x++)clothed.setPixelRGBA(x,y,0xFF3377BB);
            String path="textures/character/facial_hair/beard_5.png";var colors=java.util.Map.of(path,0x78513B);
            AppearanceTextures.paint(clear,path,path,colors,0,true);AppearanceTextures.paint(clothed,path,path,colors,0,true);int samples=0;
            for(int y=20;y<32;y++)for(int x=20;x<28;x++)if((clear.getPixelRGBA(x,y)>>>24)==255){check(clothed.getPixelRGBA(x,y+16)==clear.getPixelRGBA(x,y),"textured beard covers outer chest clothing");samples++;}
            check(samples>0,"dwarf beard exercises chest coverage");
        }
    }
    private boolean equalTexture(Minecraft g,PlayerSkin a,PlayerSkin b){var first=((DynamicTexture)g.getTextureManager().getTexture(a.texture())).getPixels();var second=((DynamicTexture)g.getTextureManager().getTexture(b.texture())).getPixels();for(int y=0;y<first.getHeight();y++)for(int x=0;x<first.getWidth();x++)if(first.getPixelRGBA(x,y)!=second.getPixelRGBA(x,y))return false;return true;}
    private void capture(Minecraft game,String name){var dir=new java.io.File("../build/ccc042-captures"+(net.neoforged.fml.ModList.get().isLoaded("wildfire_gender")?"-female":""));dir.mkdirs();net.minecraft.client.Screenshot.grab(dir,name,game.getMainRenderTarget(),m->System.out.println("CCC_CAPTURE "+m.getString()));}
}
