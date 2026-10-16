package ru.teacraft.civitatum.client;

import java.util.*;
import java.util.function.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import ru.teacraft.civitatum.*;

/** Floating windows over the live world. Coordinates, hit tests and drags share one virtual canvas. */
public final class CharacterScreen extends Screen {
    private static final int W=960,H=540,INK=0xFFE9D7B6,MUTED=0xFFB5A58A,GOLD=0xFFC6A267;
    private record Control(int x,int y,int w,int h,String text,String tip,boolean enabled,boolean selected,Runnable action) {
        boolean inside(double mx,double my){return mx>=x && mx<x+w && my>=y && my<y+h;}
    }
    private record Slider(int x,int y,int w,float min,float max,float step,Consumer<Float> setter) {}
    private final long token;
    private final EditorMode mode;
    private final CharacterData initial;
    private final List<Control> controls=new ArrayList<>();
    private final List<Slider> sliders=new ArrayList<>();
    private CharacterData draft;
    private int category,tab=12,garmentGroup,portraitScroll,focus=-1;
    private String error="";
    private boolean nameFocus,sending,portraitDragging,breastAdvanced,confirming;
    private float zoom=1,panX,panY,angle=-.35f,previewZoom=1,startTick;
    private float lastPreviewSize;
    private double mouseX,mouseY;
    private Slider dragging;
    private net.minecraft.client.player.RemotePlayer previewModel;
    private CameraType oldCamera;
    private boolean oldHideGui,capturedView;
    public CharacterScreen(long token,boolean choose,CharacterData saved){this(token,choose?EditorMode.CREATE:EditorMode.FULL,saved);}
    public CharacterScreen(long token,EditorMode mode,CharacterData saved) {
        super(Component.literal("Civitatum — редактор персонажа"));this.token=token;this.mode=mode;
        draft=saved.selected?saved.copy():RaceCatalog.defaults("human","male");initial=draft.copy();
        if(mode==EditorMode.COSMETIC){category=1;tab=3;}
    }
    @Override protected void init(){
        if(!capturedView){oldCamera=minecraft.options.getCameraType();oldHideGui=minecraft.options.hideGui;capturedView=true;startTick=minecraft.player==null?0:minecraft.player.tickCount;}
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);minecraft.options.hideGui=true;
    }
    @Override public void removed(){if(capturedView){minecraft.options.setCameraType(oldCamera);minecraft.options.hideGui=oldHideGui;capturedView=false;}clearPreview();}
    @Override public boolean isPauseScreen(){return false;}
    public CharacterData draft(){return draft;}
    public EditorMode mode(){return mode;}
    public long tokenForTest(){return token;}
    public void error(String message){error=message;sending=false;confirming=false;}
    public void selectForTest(String race,String gender){draft=RaceCatalog.defaults(race,gender);}
    public void stageForTest(int stage){confirming=stage==3;}
    public void tabForTest(int value){tab=value;category=value==11?2:List.of(3,4,6,10).contains(value)?1:0;}
    public void angleForTest(float value){angle=value;}
    public void zoomForTest(float value){previewZoom=Math.clamp(value,1,3);}
    public float previewSizeForTest(){return lastPreviewSize;}
    private void geometry(){zoom=Math.min(width/(float)W,height/(float)H);panX=(width-W*zoom)/2;panY=(height-H*zoom)/2;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        geometry();mouseX=(mx-panX)/zoom;mouseY=(my-panY)/zoom;controls.clear();sliders.clear();
        g.pose().pushPose();g.pose().translate(panX,panY,0);g.pose().scale(zoom,zoom,1);
        if(!confirming)preview(g,258,136,438,336,partial);
        raceWindow(g);navigation(g);settingsWindow(g);footer(g);
        if(confirming)confirmation(g);
        for(int i=0;i<controls.size();i++)draw(g,controls.get(i),i);
        if(!error.isBlank()){panel(g,270,440,416,32);wrapped(g,error,280,448,395,0xFFFFBAA6,2);}
        if(nameFocus && (System.currentTimeMillis()/500)%2==0){int x=376+Math.min(310,font.width(draft.name));g.fill(x,111,x+1,120,INK);}
        Control hovered=null;for(var c:controls)if(c.inside(mouseX,mouseY) && !c.tip.isBlank())hovered=c;
        if(hovered!=null){
            var lines=font.split(Component.literal(hovered.tip),210);int tx=Math.clamp((int)mouseX+9,5,W-227),ty=Math.clamp((int)mouseY+14,5,H-lines.size()*11-8);
            g.pose().pushPose();g.pose().translate(0,0,600);panel(g,tx-4,ty-4,220,lines.size()*11+8);int y=ty;
            for(var line:lines){g.drawString(font,line,tx,y,INK,false);y+=11;}g.pose().popPose();
        }
        g.pose().popPose();
    }
    private void navigation(GuiGraphics g){
        panel(g,270,18,670,77);
        String[] names={"Тип","Внешность","Созвездие"};
        for(int i=0;i<3;i++){int n=i;button(280+i*219,26,211,24,names[i],mode.body() || i==1,()->{category=n;tab=n==0?12:n==1?3:11;nameFocus=false;},category==i);}
        String[] labels=category==0?new String[]{"Скин","Кожа","Глаза","Раса","Грудь","Рост"}:category==1?new String[]{"Волосы","Борода","Одежда","Татуировки"}:new String[]{"Знаки рождения"};
        int[] ids=category==0?new int[]{12,0,9,1,5,8}:category==1?new int[]{3,10,6,4}:new int[]{11};
        int step=650/labels.length;
        for(int i=0;i<labels.length;i++){int id=ids[i];boolean active=tab==id || id==1 && List.of(1,2,7,13).contains(tab);button(280+i*step,59,step-6,26,labels[i],true,()->{tab=id;nameFocus=false;},active);}
        text(g,"Имя",323,111,MUTED);button(365,102,341,26,draft.name.isBlank()?"Введите имя персонажа":draft.name,true,()->nameFocus=true,nameFocus);
    }
    private void raceWindow(GuiGraphics g){
        panel(g,18,108,232,424);var r=RaceCatalog.get(draft.race);
        g.fill(83,17,185,119,0xDD35291C);g.fill(86,20,182,116,GOLD);
        g.blit(Civitatum.res("textures/gui/portraits/"+r.id+".png"),88,22,92,92,0,0,1254,1254,1254,1254);
        centered(g,r.name,134,125,INK);
        var roots=RaceCatalog.roots();String root=r.parent==null?r.id:r.parent;
        for(int i=0;i<roots.size();i++){var race=roots.get(i);button(29+(i%2)*110,146+(i/2)*23,104,20,race.name,mode.race(),()->selectRace(race.family()?RaceCatalog.children(race.id).getFirst().id:race.id),race.id.equals(root));}
        g.fill(29,285,239,437,0x80201710);
        String content=r.description+"\n\n+ "+r.positive+"\n− "+r.negative;
        if(!r.active.isBlank())content+="\n\n"+r.active+"\nЦена: "+(r.magic && net.neoforged.fml.ModList.get().isLoaded("ars_nouveau")?"мана Ars Nouveau":r.food+" питания")+"; откат: "+r.cooldown+" сек.";
        var lines=font.split(Component.literal(content),192);int visible=13;portraitScroll=Math.clamp(portraitScroll,0,Math.max(0,lines.size()-visible));
        for(int i=0;i<visible && i+portraitScroll<lines.size();i++)g.drawString(font,lines.get(i+portraitScroll),37,293+i*10,INK,false);
        if(lines.size()>visible)centered(g,"Колесо: описание и способности",134,426,MUTED);
        var children=RaceCatalog.children(root);text(g,children.isEmpty()?"Подрасы отсутствуют":"Подраса",30,447,MUTED);
        for(int i=0;i<children.size();i++){var race=children.get(i);button(29+(i%2)*110,463+(i/2)*25,104,22,race.name,mode.race(),()->selectRace(race.id),race.id.equals(r.id));}
    }
    private void selectRace(String id){String name=draft.name;draft=RaceCatalog.defaults(id,draft.sex);draft.name=name;portraitScroll=0;}
    private void changeSex(String sex){
        draft.sex=sex;draft.armModel=sex.equals("female")?"slim":"classic";draft.bust=sex.equals("female")?.6f:0;
        AppearanceCatalog.outfit(draft,AppearanceCatalog.outfits(sex).getFirst().id());draft=RaceCatalog.validate(draft);
    }
    private void settingsWindow(GuiGraphics g){
        panel(g,704,136,238,396);var r=RaceCatalog.get(draft.race);
        String title=switch(tab){case 0->"Кожа";case 1,2,7,13->"Расовые элементы";case 3->"Волосы";case 4->"Татуировки";case 5->"Грудь";case 6->"Одежда";case 8->"Рост";case 9->"Глаза";case 10->"Борода";case 11->"Созвездие";default->"Тип персонажа";};
        centered(g,title,823,148,GOLD);g.fill(716,163,930,164,0x887F6846);
        if(List.of(1,2,7).contains(tab)){
            int[] ids={1,2,7};String[] names={"Уши","Рога","Хвост"};for(int i=0;i<3;i++){int id=ids[i];button(714+i*73,174,68,23,names[i],true,()->tab=id,tab==id);}
        }
        switch(tab){
            case 12->{
                text(g,"Пол",716,180,MUTED);button(716,198,103,25,"Мужской",mode.body(),()->changeSex("male"),!draft.female());button(824,198,103,25,"Женский",mode.body(),()->changeSex("female"),draft.female());
                text(g,"Источник скина",716,241,MUTED);button(716,260,103,25,"Имеющийся",mode.body(),()->draft.usePlayerSkin=true,draft.usePlayerSkin);button(824,260,103,25,"Новый",mode.body(),()->draft.usePlayerSkin=false,!draft.usePlayerSkin);
                text(g,"Модель рук",716,305,MUTED);button(716,324,103,25,"Classic",mode.body(),()->draft.armModel="classic",SkinTemplates.model(draft).equals("classic"));button(824,324,103,25,"Slim",mode.body(),()->draft.armModel="slim",SkinTemplates.model(draft).equals("slim"));
                wrapped(g,"Имеющийся скин сохраняет собственные цвета. Новый использует подготовленную основу кожи и глаз.",716,374,208,MUTED,7);
            }
            case 0->{
                palette(g,"Цвет кожи / чешуи",184,r.skinColors,draft.skin,mode.body() && !draft.usePlayerSkin,n->{draft.skin=n;draft.skinOriginal=false;},!draft.skinOriginal);
                button(716,246,211,24,"Исходная текстура",mode.body() && !draft.usePlayerSkin && r.id.equals("human"),()->draft.skinOriginal=true,draft.skinOriginal);
                wrapped(g,draft.usePlayerSkin?"Цвет кожи задаётся имеющимся скином.":"Светотень исходной текстуры сохраняется при перекраске.",716,295,208,MUTED,6);
            }
            case 9->{
                palette(g,"Цвет глаз",184,r.eyeColors,draft.eyes,mode.body() && !draft.usePlayerSkin,n->{draft.eyes=n;draft.eyesOriginal=false;},!draft.eyesOriginal);
                button(716,246,211,23,"Исходный цвет",mode.body() && !draft.usePlayerSkin && r.eyeColors.length>1,()->draft.eyesOriginal=true,draft.eyesOriginal);
                number(g,"Высота глаз",292,draft.eyeHeight,1,-2,2,n->draft.eyeHeight=Math.round(n),mode.body() && !draft.usePlayerSkin);
                wrapped(g,"Верхний контур связан с основным цветом волос. Настройки глаз применяются к новому скину.",716,362,208,MUTED,6);
            }
            case 1->{
                int[] options=r.pointedEars?new int[]{1,2,3,4}:new int[]{0,1,2,3,4};
                cycle(g,"Форма ушей",212,draft.ears,options,n->draft.ears=n,mode.body() && (r.pointedEars || Arrays.stream(r.earStyles).anyMatch(n->n>0)));
                number(g,"Наклон",268,draft.earPitch,5,-60,60,n->draft.earPitch=n,mode.body() && draft.ears>0);
                number(g,"Разворот",316,draft.earYaw,5,-60,60,n->draft.earYaw=n,mode.body() && draft.ears>0);
                number(g,"Крен",364,draft.earRoll,5,-60,60,n->draft.earRoll=n,mode.body() && draft.ears>0);
                number(g,"Высота крепления",412,draft.earOffset,.25f,-2,2,n->draft.earOffset=n,mode.body() && draft.ears>0);
            }
            case 2->{
                int[] styles=(!r.hornsRequired || r.algnor() && draft.female())?prepend(r.hornStyles,0):r.hornStyles;
                cycle(g,"Вид рогов",211,draft.horns,styles,n->draft.horns=n,mode.body() && r.hornsAllowed);
                palette(g,"Цвет рогов",261,r.hornColors,draft.hornColor,mode.body() && r.hornsAllowed,n->draft.hornColor=n,true);
                number(g,"Наклон",319,draft.hornPitch,5,-60,60,n->draft.hornPitch=n,mode.body() && r.hornsAllowed);
                number(g,"Разворот",366,draft.hornYaw,5,-60,60,n->draft.hornYaw=n,mode.body() && r.hornsAllowed);
                number(g,"Размер",413,draft.hornSize,.05f,.65f,1.35f,n->draft.hornSize=n,mode.body() && r.hornsAllowed);
                button(716,472,211,22,"Крепление: "+(draft.hornOnForehead?"лоб":"макушка"),mode.body() && r.hornsAllowed,()->draft.hornOnForehead=!draft.hornOnForehead);
                button(716,499,211,20,"Дополнительно: крен / высота",mode.body() && r.hornsAllowed,()->tab=13);
            }
            case 13->{number(g,"Крен рогов",190,draft.hornRoll,5,-60,60,n->draft.hornRoll=n,mode.body() && r.hornsAllowed);number(g,"Высота крепления",245,draft.hornOffset,.2f,-.2f,2,n->draft.hornOffset=n,mode.body() && r.hornsAllowed);button(716,310,211,24,"Назад к рогам",true,()->tab=2);}
            case 7->{cycle(g,"Вид хвоста",215,draft.tail,new int[]{0,1,2,3},n->draft.tail=n,mode.body() && r.tailAllowed);wrapped(g,r.tailAllowed?"Три варианта оленьего хвоста: короткий, удлинённый и пушистый.":"Для этой расы хвост недоступен.",716,280,208,MUTED,7);}
            case 8->{number(g,"Рост, блоки",195,draft.height,.05f,r.minHeight,r.maxHeight,n->draft.height=n,mode.body());wrapped(g,"Рост влияет на модель, камеру, размеры тела и досягаемость. Диапазон определяется расой.",716,270,208,MUTED,7);}
            case 3->hair(g,r);
            case 10->facialHair(g,r);
            case 4->{int[] options=new int[r.tattoos+1];for(int i=0;i<options.length;i++)options[i]=i;cycle(g,"Вид отметок",188,draft.tattoo,options,n->draft.tattoo=n,r.tattoos>0);palette(g,"Цвет отметок",249,r.tattooColors,draft.tattooColor,r.tattoos>0,n->draft.tattooColor=n,true);button(716,312,211,25,"Свечение: "+yes(draft.glow),r.id.equals("orthosian"),()->draft.glow=!draft.glow);wrapped(g,"Отметки находятся под нижним бельём и одеждой. Особенности зависят от расы.",716,365,208,MUTED,7);}
            case 5->bust(g,r);
            case 6->clothing(g);
            case 11->{
                var signs=BirthsignCatalog.all();for(int i=0;i<signs.size();i++){var sign=signs.get(i);button(716,178+i*26,211,23,sign.name(),mode.body(),()->draft.birthsign=sign.id(),draft.birthsign.equals(sign.id()));}
                var sign=BirthsignCatalog.get(draft.birthsign);g.fill(714,370,932,518,0x80201710);wrapped(g,sign.description()+"\n\n"+sign.effect(),722,381,200,INK,12);
            }
        }
    }
    private void hair(GuiGraphics g,RaceDefinition r){
        var list=AppearanceCatalog.hair();var asset=AppearanceCatalog.hair(draft.hairAsset);
        text(g,"Причёска",716,180,MUTED);chooser(716,198,211,asset==null?"Нет / старая":asset.name(),r.hairAllowed,delta->{
            int i=-1;for(int n=0;n<list.size();n++)if(list.get(n).id().equals(draft.hairAsset))i=n;int next=Math.floorMod(i+1+delta,list.size()+1)-1;
            draft.hairAsset=next<0?"":list.get(next).id();draft.hairStyle=0;draft.hairMain=draft.hairDecor1=draft.hairDecor2=0;
        });
        if(asset!=null){
            if(asset.linkedShades())button(716,229,211,22,"Оттенки от цвета волос: "+yes(draft.hairLinkedShades),true,()->{draft.hairLinkedShades=!draft.hairLinkedShades;draft.hairDecor1=draft.hairDecor2=0;});
            assetColors(g,asset,asset.linkedShades()?267:245,new int[]{draft.hairMain,draft.hairDecor1,draft.hairDecor2},(i,n)->{if(i==0)draft.hairMain=n;else if(i==1)draft.hairDecor1=n;else draft.hairDecor2=n;});
        }
        else {cycle(g,"Старая причёска",255,draft.hairStyle,new int[]{0,1,2,3,4},n->draft.hairStyle=n,r.hairAllowed);palette(g,"Цвет волос",315,r.hairColors,draft.hair,r.hairAllowed,n->draft.hair=n,true);}
    }
    private void facialHair(GuiGraphics g,RaceDefinition r){
        boolean enabled=RaceCatalog.beardAllowed(draft);
        text(g,"Борода",716,185,MUTED);chooser(716,202,211,FacialHair.BEARDS.get(Math.clamp(draft.beard,0,5)),enabled,n->draft.beard=Math.floorMod(draft.beard+n,6));
        text(g,"Усы",716,244,MUTED);chooser(716,261,211,FacialHair.MUSTACHES.get(Math.clamp(draft.mustache,0,5)),enabled,n->draft.mustache=Math.floorMod(draft.mustache+n,6));
        var hair=AppearanceCatalog.hair(draft.hairAsset);
        if(hair==null)palette(g,"Цвет волос и бороды",319,r.hairColors,draft.hair,enabled,n->draft.hair=n,true);
        else{
            text(g,"Цвет волос и бороды",716,319,MUTED);button(856,336,71,20,"Исходный",enabled,()->draft.hairMain=0,draft.hairMain==0);
            var colors=hair.controls().get("main").colors();
            for(int i=1;i<colors.size();i++){int n=i;var c=colors.get(i);swatch(g,716+(i-1)*35,365,29,26,c.rgb(),enabled,draft.hairMain==i,c.label(),()->draft.hairMain=n);}
        }
        wrapped(g,enabled?"Борода и усы — текстуры скина. Цвет связан с основным цветом волос.":"Для этой расы борода недоступна. Из женщин бороды и усы могут выбирать только дварфы.",716,418,208,MUTED,8);
    }
    private void clothing(GuiGraphics g){
        button(716,176,211,23,(draft.naked?"[x]":"[ ]")+" Только нижнее бельё",true,()->draft.naked=!draft.naked);
        var outfits=AppearanceCatalog.outfits(draft.sex);var outfit=outfits.stream().filter(o->o.id().equals(draft.outfitPreset)).findFirst().orElse(null);
        chooser(716,207,211,outfit==null?"Свой комплект":outfit.name(),true,delta->{int i=outfit==null?-1:outfits.indexOf(outfit),n=Math.floorMod(i+1+delta,outfits.size()+1)-1;AppearanceCatalog.outfit(draft,n<0?"":outfits.get(n).id());});
        String group=garmentGroup==9?"underwear":AppearanceCatalog.GROUPS.get(garmentGroup);
        chooser(716,240,211,garmentGroup==9?"Нижнее бельё":AppearanceCatalog.GROUP_NAMES.get(garmentGroup),true,delta->garmentGroup=Math.floorMod(garmentGroup+delta,10));
        if(garmentGroup==9){String[] palette={"#E0D5B9","#44434A","#A45B6C","#578FB8","#687E60","#9B7753"};palette(g,"Цвет нижнего белья",294,palette,draft.underwearColor-1,true,n->draft.underwearColor=n+1,draft.underwearColor>0);button(716,355,211,23,"Исходный цвет белья",true,()->draft.underwearColor=0,draft.underwearColor==0);return;}
        var choice=draft.wardrobe.get(group);var asset=choice==null?null:AppearanceCatalog.clothing(choice.asset);var list=AppearanceCatalog.clothes(draft.sex,group);
        chooser(716,273,211,asset==null?"Нет":asset.name(),!list.isEmpty(),delta->{int i=asset==null?-1:list.indexOf(asset),n=Math.floorMod(i+1+delta,list.size()+1)-1;draft.wardrobeEnabled=true;draft.outfitPreset="";if(n<0)draft.wardrobe.remove(group);else draft.wardrobe.put(group,new CharacterData.Garment(list.get(n).id()));});
        if(asset!=null)assetColors(g,asset,313,new int[]{choice.main,choice.decor1,choice.decor2},(i,n)->{if(i==0)choice.main=n;else if(i==1)choice.decor1=n;else choice.decor2=n;});
    }
    private void bust(GuiGraphics g,RaceDefinition r){
        boolean enabled=mode.body() && draft.female() && !r.id.equals("forged");
        if(breastAdvanced){number(g,"Подвижность",190,draft.bounce,.025f,0,.5f,n->draft.bounce=n,enabled);number(g,"Мягкость",240,draft.floppiness,.05f,.25f,1,n->draft.floppiness=n,enabled);button(716,305,211,24,"Физика под бронёй: "+yes(draft.armorPhysics),enabled,()->draft.armorPhysics=!draft.armorPhysics);button(716,350,211,24,"Основные настройки",true,()->breastAdvanced=false);return;}
        number(g,"Размер груди",185,draft.bust,.05f,0,.8f,n->draft.bust=n,enabled);
        number(g,"Смещение X",233,draft.bustX,.1f,-1,1,n->draft.bustX=n,enabled);number(g,"Смещение Y",281,draft.bustY,.1f,-1,1,n->draft.bustY=n,enabled);number(g,"Смещение Z",329,draft.bustZ,.1f,-1,0,n->draft.bustZ=n,enabled);number(g,"Расстояние",377,draft.cleavage,.01f,0,.1f,n->draft.cleavage=n,enabled);
        button(716,428,103,22,"Физика: "+yes(draft.breastPhysics),enabled,()->draft.breastPhysics=!draft.breastPhysics);button(824,428,103,22,"Броня: "+yes(draft.showInArmor),enabled,()->draft.showInArmor=!draft.showInArmor);
        button(716,455,211,22,"Форма: "+(draft.uniboob?"единая":"парная"),enabled,()->draft.uniboob=!draft.uniboob);button(716,483,211,24,"Дополнительные настройки",enabled,()->breastAdvanced=true);
    }
    private void footer(GuiGraphics g){
        panel(g,270,478,418,54);
        button(280,485,67,18,"Поворот <",true,()->angle-=.4f);button(353,485,67,18,"Поворот >",true,()->angle+=.4f);
        button(426,485,77,18,"Голова +",true,()->previewZoom=Math.min(3,previewZoom+.4f));button(509,485,77,18,"Голова −",true,()->previewZoom=Math.max(1,previewZoom-.4f));
        button(592,485,86,18,"Весь рост",true,()->previewZoom=1);
        button(280,508,92,18,"Отмена",!sending,this::onClose);button(378,508,145,18,"Сброс раздела",!sending && (mode.body() || category==1),this::resetSection);
        button(529,508,149,18,sending?"Сохранение…":"Подтвердить",!sending,()->{confirming=true;nameFocus=false;});
    }
    private void resetSection(){
        if(tab==12){
            var base=initial.race.equals(draft.race)?initial.copy():RaceCatalog.defaults(draft.race,initial.sex);
            if(!draft.sex.equals(base.sex)){draft.sex=base.sex;draft.bust=base.bust;draft.outfitPreset=base.outfitPreset;draft.wardrobe=base.wardrobe;draft.wardrobeEnabled=base.wardrobeEnabled;}
            draft.usePlayerSkin=base.usePlayerSkin;draft.armModel=base.armModel;return;
        }
        var source=initial.race.equals(draft.race) && initial.sex.equals(draft.sex)?initial.copy():RaceCatalog.defaults(draft.race,draft.sex);
        String[] fields=switch(tab){case 0->new String[]{"skin","skinOriginal"};case 9->new String[]{"eyes","eyesOriginal","eyeHeight"};case 1->new String[]{"ears","earPitch","earYaw","earRoll","earOffset"};case 2,13->new String[]{"horns","hornColor","hornPitch","hornYaw","hornRoll","hornOffset","hornSize","hornOnForehead"};case 7->new String[]{"tail"};case 8->new String[]{"height"};case 3->new String[]{"hairAsset","hairStyle","hair","hairMain","hairDecor1","hairDecor2","hairLinkedShades"};case 10->new String[]{"beard","mustache","hair","hairMain"};case 4->new String[]{"tattoo","tattooColor","glow"};case 6->new String[]{"wardrobe","wardrobeEnabled","outfitPreset","naked","underwearColor"};case 5->new String[]{"bust","bustX","bustY","bustZ","cleavage","bounce","floppiness","breastPhysics","armorPhysics","showInArmor","uniboob"};case 11->new String[]{"birthsign"};default->new String[]{"usePlayerSkin","armModel"};};
        try{for(String field:fields){var f=CharacterData.class.getField(field);f.set(draft,f.get(source));}}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private void confirmation(GuiGraphics g){
        panel(g,282,186,394,235);var r=RaceCatalog.get(draft.race);centered(g,"ПОДТВЕРЖДЕНИЕ",479,199,GOLD);
        wrapped(g,(draft.name.isBlank()?minecraft.player.getGameProfile().getName():draft.name)+"\n"+r.name+" • "+(draft.female()?"женский":"мужской")+" • "+format(draft.height)+" блока\n"+BirthsignCatalog.get(draft.birthsign).name()+"\n\n"+(mode==EditorMode.COSMETIC?"Изменятся только доступные внешние детали.":"Проверьте выбранные настройки персонажа."),298,226,360,INK,12);
        // Prevent click-through into controls behind this window.
        controls.replaceAll(c->new Control(c.x,c.y,c.w,c.h,c.text,c.tip,false,c.selected,c.action));sliders.clear();
        controls.add(new Control(282,186,394,235,"","",false,false,()->{}));
        button(298,378,165,28,"Назад к настройкам",!sending,()->confirming=false);
        button(478,378,180,28,sending?"Сохранение…":"Сохранить персонажа",!sending,()->{try{draft=RaceCatalog.validate(draft);sending=true;PacketDistributor.sendToServer(new RaceNetwork.Serverbound(0,token,draft.json()));}catch(RuntimeException e){error(e.getMessage());}});
    }
    private void preview(GuiGraphics g,int x,int y,int w,int h,float partial){
        if(minecraft.player==null)return;
        if(previewModel==null || previewModel.level()!=minecraft.level)previewModel=new net.minecraft.client.player.RemotePlayer(minecraft.level,minecraft.player.getGameProfile()){
            @Override public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part){return part!=net.minecraft.world.entity.player.PlayerModelPart.CAPE;}
        };
        var model=previewModel;model.yBodyRot=model.yBodyRotO=180;model.setYRot(180);model.yRotO=180;model.yHeadRot=model.yHeadRotO=180;model.setXRot(0);model.xRotO=0;model.tickCount=minecraft.player.tickCount;
        ClientCharacters.preview=draft;ClientCharacters.previewPlayer=model.getUUID();ClientCharacters.previewEntity=model;ClientCharacters.previewTime=IdleAnimation.playbackTime(minecraft.player.tickCount+partial-startTick);
        try{
            // One camera scale for every race/height. The renderer applies draft.height / 1.8.
            // Dividing by draft.height here used to cancel the entire visible height change.
            float size=(h-38)/2.65f*previewZoom;lastPreviewSize=size;
            float feet=previewZoom==1?y+h-8:y+h*.42f+size*draft.height*.84f;
            var renderer=(PlayerRenderer)minecraft.getEntityRenderDispatcher().getRenderer(model);
            try(var saved=PreviewPose.preserve(renderer.getModel())){
                g.pose().pushPose();try{
                    g.pose().translate(-panX/zoom,-panY/zoom,0);g.pose().scale(1/zoom,1/zoom,1);
                    g.enableScissor(Math.round(panX+x*zoom),Math.round(panY+y*zoom),Math.round(panX+(x+w)*zoom),Math.round(panY+(y+h)*zoom));
                    try{var rotation=new org.joml.Quaternionf().rotateZ((float)Math.PI).rotateY(angle).rotateX(-.04f);InventoryScreen.renderEntityInInventory(g,panX+(x+w/2f)*zoom,panY+feet*zoom,size*zoom,new org.joml.Vector3f(),rotation,null,model);}finally{g.disableScissor();}
                }finally{g.pose().popPose();}
            }
        }finally{clearPreview();}
        centered(g,"Рост: "+format(draft.height)+" блока",x+w/2,y+7,MUTED);
        if(previewZoom==1){
            int base=y+h-8,line=x+25;g.fill(line,base-Math.round(lastPreviewSize*2.5f*.9375f),line+1,base,GOLD);
            for(float mark:new float[]{1,1.5f,2,2.5f}){int yy=base-Math.round(lastPreviewSize*mark*.9375f);g.fill(line-3,yy,line+5,yy+1,GOLD);text(g,format(mark),line+9,yy-3,MUTED);}
        }
    }
    private static void clearPreview(){ClientCharacters.preview=null;ClientCharacters.previewPlayer=null;ClientCharacters.previewEntity=null;ClientCharacters.previewTime=0;}
    private void palette(GuiGraphics g,String label,int y,String[] colors,int chosen,boolean enabled,IntConsumer set,boolean selection){
        text(g,label,716,y,MUTED);int step=211/Math.max(6,colors.length);
        for(int i=0;i<colors.length;i++){int n=i,x=716+i*step;swatch(g,x,y+18,29,24,Integer.parseInt(colors[i].substring(1),16),enabled,selection && chosen==i,colors[i],()->set.accept(n));}
    }
    private void assetColors(GuiGraphics g,AppearanceCatalog.Asset asset,int y,int[] values,java.util.function.BiConsumer<Integer,Integer> set){
        int row=y;boolean linked=asset==AppearanceCatalog.hair(draft.hairAsset) && draft.hairLinkedShades;
        for(int i=0;i<3;i++){
            String key=AppearanceCatalog.CHANNELS.get(i);var c=asset.controls().get(key);if(c==null)continue;int channel=i;
            boolean shade=AppearanceCatalog.shadeChannel(asset,key,linked);int count=AppearanceCatalog.colorCount(asset,key,linked);
            text(g,i==0?"Основной цвет":shade?"Оттенок волос":"Декор "+i,716,row,MUTED);button(856,row-4,71,18,"Исходный",true,()->set.accept(channel,0),values[i]==0);
            for(int n=1;n<count;n++){int index=n;var color=c.colors().get(Math.min(n,c.colors().size()-1));int rgb=shade?AppearanceCatalog.tint(asset,key,n,draft.hairMain,true):color.rgb();
                swatch(g,716+(n-1)%6*35,row+19+(n-1)/6*29,29,26,rgb,true,values[i]==n,(shade?HairTones.LABELS[n]:color.label())+" • "+c.affects(),()->set.accept(channel,index));}
            row+=count>7?82:67;
        }
    }
    private void swatch(GuiGraphics g,int x,int y,int w,int h,int color,boolean enabled,boolean selected,String tip,Runnable action){
        g.fill(x,y,x+w,y+h,selected?GOLD:0xFF30271D);g.fill(x+3,y+3,x+w-3,y+h-3,0xFF000000|color);
        if(!enabled)g.fill(x+3,y+3,x+w-3,y+h-3,0xAA403B34);controls.add(new Control(x,y,w,h,"",tip,enabled,selected,action));
    }
    private void number(GuiGraphics g,String label,int y,float value,float step,float min,float max,Consumer<Float> set,boolean enabled){
        text(g,label,716,y,enabled?INK:MUTED);String v=step>=1?Integer.toString(Math.round(value)):format(value);g.drawString(font,v,927-font.width(v),y,MUTED,false);
        button(716,y+15,22,20,"−",enabled,()->set.accept(Math.max(min,Math.round((value-step)*1000)/1000f)));button(905,y+15,22,20,"+",enabled,()->set.accept(Math.min(max,Math.round((value+step)*1000)/1000f)));
        int x=745,w=153;g.fill(x,y+23,x+w,y+27,0xFF372D22);int thumb=x+Math.round((value-min)/(max-min)*(w-4));g.fill(thumb,y+18,thumb+4,y+32,enabled?GOLD:0xFF6A5D4A);
        if(enabled)sliders.add(new Slider(x,y+15,w,min,max,step,set));
    }
    private void cycle(GuiGraphics g,String label,int y,int value,int[] values,IntConsumer set,boolean enabled){
        text(g,label,716,y,MUTED);chooser(716,y+17,211,value==0?"Нет":"Вариант "+value,enabled,delta->{int index=0;for(int i=0;i<values.length;i++)if(values[i]==value)index=i;set.accept(values[Math.floorMod(index+delta,values.length)]);});
    }
    private void chooser(int x,int y,int w,String value,boolean enabled,IntConsumer set){button(x,y,23,25,"<",enabled,()->set.accept(-1));button(x+28,y,w-56,25,value,enabled,()->set.accept(1));button(x+w-23,y,23,25,">",enabled,()->set.accept(1));}
    private static int[] prepend(int[] a,int first){int[] b=new int[a.length+1];b[0]=first;System.arraycopy(a,0,b,1,a.length);return b;}
    private void button(int x,int y,int w,int h,String label,boolean enabled,Runnable action){button(x,y,w,h,label,enabled,action,false);}
    private void button(int x,int y,int w,int h,String label,boolean enabled,Runnable action,boolean selected){controls.add(new Control(x,y,w,h,label,label,enabled,selected,action));}
    private void draw(GuiGraphics g,Control c,int index){
        if(c.text.isEmpty())return;boolean hover=c.inside(mouseX,mouseY)||focus==index;
        g.fill(c.x,c.y,c.x+c.w,c.y+c.h,c.selected?0xCC6A5030:hover && c.enabled?0xDD4A3924:0xC018130E);
        int border=c.selected?GOLD:c.enabled?0xAA8E7450:0x77544734;g.fill(c.x,c.y,c.x+c.w,c.y+1,border);g.fill(c.x,c.y+c.h-1,c.x+c.w,c.y+c.h,border);
        g.fill(c.x,c.y,c.x+1,c.y+c.h,border);g.fill(c.x+c.w-1,c.y,c.x+c.w,c.y+c.h,border);
        String text=c.text;while(font.width(text)>c.w-8 && text.length()>1)text=text.substring(0,text.length()-1);
        centered(g,text,c.x+c.w/2,c.y+(c.h-8)/2,c.enabled?INK:0xFF7E725F);
    }
    private void panel(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,0xB3292018);g.fill(x,y,x+w,y+2,0xCCA78A5E);g.fill(x,y+h-2,x+w,y+h,0xCC7E6441);g.fill(x,y,x+2,y+h,0xCC9A7F55);g.fill(x+w-2,y,x+w,y+h,0xCC9A7F55);}
    private void text(GuiGraphics g,String value,int x,int y,int color){g.drawString(font,value,x,y,color,true);}
    private void centered(GuiGraphics g,String value,int x,int y,int color){text(g,value,x-font.width(value)/2,y,color);}
    private void wrapped(GuiGraphics g,String value,int x,int y,int width,int color,int count){int row=0;for(var line:font.split(Component.literal(value),width)){if(row>=count)break;g.drawString(font,line,x,y+row++*11,color,true);}}
    private static String yes(boolean v){return v?"да":"нет";}
    private static String format(float v){return String.format(Locale.ROOT,"%.2f",v).replace('.',',');}
    private void slide(Slider slider,double x){float f=(float)Math.clamp((x-slider.x)/slider.w,0,1);float value=slider.min+Math.round(f*(slider.max-slider.min)/slider.step)*slider.step;slider.setter.accept(Math.clamp(Math.round(value*1000)/1000f,slider.min,slider.max));}
    @Override public boolean mouseClicked(double x,double y,int button){
        geometry();double mx=(x-panX)/zoom,my=(y-panY)/zoom;
        if(button==0){
            for(int i=controls.size()-1;i>=0;i--){var c=controls.get(i);if(c.inside(mx,my)){if(c.enabled){focus=i;error="";if(!c.text.equals(draft.name) && !(c.x==365 && c.y==102))nameFocus=false;c.action.run();}return true;}}
            if(!confirming)for(var slider:sliders)if(mx>=slider.x-3 && mx<=slider.x+slider.w+3 && my>=slider.y && my<slider.y+20){dragging=slider;slide(slider,mx);return true;}
            if(!confirming && mx>=258 && mx<696 && my>=136 && my<472){portraitDragging=true;nameFocus=false;return true;}
        }
        nameFocus=false;return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging!=null){slide(dragging,(x-panX)/zoom);return true;}if(portraitDragging){angle+=(float)(dx/zoom)*.012f;return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){dragging=null;portraitDragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){geometry();double mx=(x-panX)/zoom,my=(y-panY)/zoom;if(mx<250 && my>=285 && my<=437)portraitScroll=Math.max(0,portraitScroll-(int)vertical*3);else if(mx>=258 && mx<=696 && my>=136 && my<=472)previewZoom=Math.clamp(previewZoom+(float)vertical*.2f,1,3);return true;}
    @Override public boolean charTyped(char c,int modifiers){if(nameFocus && !Character.isISOControl(c) && c!='§' && draft.name.length()<32){draft.name+=c;return true;}return super.charTyped(c,modifiers);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(nameFocus){if(key==GLFW.GLFW_KEY_BACKSPACE){if(!draft.name.isEmpty())draft.name=draft.name.substring(0,draft.name.length()-1);return true;}if(key==GLFW.GLFW_KEY_V && Screen.hasControlDown()){String s=minecraft.keyboardHandler.getClipboard().replaceAll("[\\p{Cntrl}§]","");draft.name=(draft.name+s).substring(0,Math.min(32,draft.name.length()+s.length()));return true;}if(key==GLFW.GLFW_KEY_ENTER){nameFocus=false;return true;}}
        if(key==GLFW.GLFW_KEY_ESCAPE && confirming){confirming=false;return true;}if(key==GLFW.GLFW_KEY_TAB){for(int i=0;i<controls.size();i++){focus=Math.floorMod(focus+1,controls.size());if(controls.get(focus).enabled)break;}return true;}
        if((key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_SPACE) && focus>=0 && focus<controls.size()){var c=controls.get(focus);if(c.enabled)c.action.run();return true;}return super.keyPressed(key,scan,modifiers);
    }
    @Override public void onClose(){clearPreview();PacketDistributor.sendToServer(new RaceNetwork.Serverbound(3,token,""));minecraft.setScreen(null);}
}
