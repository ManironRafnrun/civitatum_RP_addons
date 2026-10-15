package ru.teacraft.civitatum.client;

import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import ru.teacraft.civitatum.*;

/** Responsive virtual canvas: every interaction is converted through the same layout transform. */
public final class CharacterScreen extends Screen {
    public static final String ARRIVAL="Наконец-то вы прибыли, но в наших записях не указано, откуда";
    public static final String CONFIRM="Теперь, перед тем как поставить печать на эти бумаги, подтвердите правильность информации";
    private static final int W=800,H=450,INK=0xFF312822,GOLD=0xFFB38A45;
    private record Control(int x,int y,int w,int h,String text,String tip,boolean enabled,Runnable action) {
        boolean inside(double mx,double my) { return mx>=x && mx<x+w && my>=y && my<y+h; }
    }
    private final long token;
    private final boolean choose;
    private final List<Control> controls=new ArrayList<>();
    private CharacterData draft;
    private String root="human", child="", sex="male", error="";
    private int stage, tab, focus=-1, portraitScroll, garmentGroup;
    private boolean beardPage;
    private boolean nameFocus, sending, portraitDragging, breastAdvanced;
    private float zoom=1, panX, panY, angle=-.8f;
    private double mouseX,mouseY;
    private net.minecraft.client.player.RemotePlayer previewModel;
    public CharacterScreen(long token,boolean choose,CharacterData saved) {
        super(Component.literal("Право на Возвращение"));
        this.token=token;this.choose=choose;this.draft=saved.copy();this.sex=saved.sex;
        var r=RaceCatalog.get(saved.race);
        if(r!=null) { root=r.parent==null?r.id:r.parent;child=r.parent==null?"":r.id; }
        stage=choose?0:2;
    }
    public void error(String message) { error=message;sending=false; }
    public CharacterData draft() { return draft; }
    public void selectForTest(String race,String gender) {
        var r=RaceCatalog.get(race);
        root=r.parent==null?r.id:r.parent;child=r.parent==null?"":race;sex=gender;
        draft=RaceCatalog.defaults(race,gender);stage=2;
    }
    public void stageForTest(int value) { stage=value; }
    public long tokenForTest() { return token; }
    private void geometry() { zoom=Math.min(width/(float)W,height/(float)H);panX=(width-W*zoom)/2;panY=(height-H*zoom)/2; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        geometry();mouseX=(mx-panX)/zoom;mouseY=(my-panY)/zoom;
        controls.clear();
        g.fill(0,0,width,height,0xE615191D);
        g.pose().pushPose();g.pose().translate(panX,panY,0);g.pose().scale(zoom,zoom,1);
        parchment(g,18,12,764,426);
        text(g,"ПРАВО НА ВОЗВРАЩЕНИЕ  /  КАНЦЕЛЯРИЯ",400,25,0xFF746040,true);
        centeredWrapped(g,stage==3?CONFIRM:ARRIVAL,35,44,730,INK);
        String[] steps={"Происхождение","Подраса","Внешность","Подтверждение"};
        for(int i=0;i<4;i++) {
            int color=i==stage?GOLD:0xFFB8A88E;
            g.fill(42+i*186,77,210+i*186,79,color);
            text(g,(i+1)+". "+steps[i],126+i*186,83,i==stage?INK:0xFF827561,true);
        }
        switch(stage) {
            case 0 -> racePage(g);
            case 1 -> subracePage(g);
            case 2 -> appearancePage(g,partial);
            case 3 -> confirmationPage(g,partial);
        }
        if(!error.isBlank()) centeredWrapped(g,error,38,393,724,0xFF9B352C);
        if(stage==0 || stage==1) {
            button(42,413,110,18,"Закрыть",true,this::onClose);
            if(stage==1) button(162,413,110,18,"Назад",true,()->stage=0);
            button(624,411,134,22,"Продолжить",true,this::next);
        } else if(stage==2) {
            button(42,411,110,22,choose?"Назад":"Отмена",true,()->{ if(choose)stage=RaceCatalog.get(root).family()?1:0;else onClose(); });
            button(624,411,134,22,"Проверить бумаги",true,()->{draft=RaceCatalog.validate(draft);stage=3;nameFocus=false;});
        } else {
            button(42,411,110,22,"Исправить",!sending,()->stage=2);
            button(600,409,158,25,sending?"Сохраняем…":"Поставить печать",!sending,()->{
                sending=true;PacketDistributor.sendToServer(new RaceNetwork.Serverbound(0,token,RaceCatalog.validate(draft).json()));
            });
        }
        for(int i=0;i<controls.size();i++) draw(g,controls.get(i),i);
        if(nameFocus) {
            g.fill(476+Math.min(252,font.width(draft.name)),142,477+Math.min(252,font.width(draft.name)),151,0xFFF5F1E8);
        }
        Control hovered=null;
        for(var c:controls) if(c.inside(mouseX,mouseY) && !c.tip.isBlank()) hovered=c;
        if(hovered!=null) {
            var lines=font.split(Component.literal(hovered.tip),220);
            int tx=Math.min((int)mouseX+8,W-232),ty=Math.min((int)mouseY+12,H-lines.size()*11-12);
            g.pose().pushPose();g.pose().translate(0,0,500);
            g.fill(tx-4,ty-4,tx+224,ty+lines.size()*11+4,0xF02A2520);
            int yy=ty;for(var line:lines){g.drawString(font,line,tx,yy,0xFFE8DDC7,false);yy+=11;}g.pose().popPose();
        }
        g.pose().popPose();
    }
    private void racePage(GuiGraphics g) {
        var races=RaceCatalog.roots();
        panel(g,42,107,213,292);
        for(int i=0;i<races.size();i++) {
            var r=races.get(i);
            button(50,114+i*23,197,21,r.name,true,()->{root=r.id;var subs=RaceCatalog.children(root);child=subs.isEmpty()?"":subs.getFirst().id;portraitScroll=0;},root.equals(r.id));
        }
        var r=RaceCatalog.get(root);
        portrait(g,r,274,111,154);
        text(g,r.name,441,111,INK,false);
        wrapped(g,r.description,441,133,304,0xFF5B4B3A,11,12);
        button(278,283,92,23,"Мужской",true,()->sex="male",sex.equals("male"));
        button(376,283,92,23,"Женский",true,()->sex="female",sex.equals("female"));
        text(g,"Пол не меняет способности",277,312,0xFF786951,false);
        traits(g,r,278,334,467,54);
    }
    private void subracePage(GuiGraphics g) {
        var children=RaceCatalog.children(root);
        if(children.isEmpty()){stage=2;return;}
        if(children.stream().noneMatch(r->r.id.equals(child))) child=children.getFirst().id;
        panel(g,42,107,235,292);
        text(g,RaceCatalog.get(root).name+" • наследие",54,119,INK,false);
        for(int i=0;i<children.size();i++) {
            var r=children.get(i);
            button(51,141+i*43,217,36,r.name,true,()->child=r.id,child.equals(r.id));
        }
        var r=RaceCatalog.get(child);
        portrait(g,r,297,113,136);
        text(g,r.name,452,113,INK,false);
        wrapped(g,r.description,452,134,289,0xFF5B4B3A,11,11);
        traits(g,r,296,269,450,122);
    }
    private void next() {
        if(stage==0 && RaceCatalog.get(root).family()) { stage=1;return; }
        String id=stage==1?child:root;
        if(draft.selected && draft.race.equals(id)) {
            draft.sex=sex;draft=RaceCatalog.validate(draft);
        } else {
            boolean ownSkin=draft.usePlayerSkin;
            draft=RaceCatalog.defaults(id,sex);draft.usePlayerSkin=ownSkin;
        }
        stage=2;error="";
    }
    private void appearancePage(GuiGraphics g,float partial) {
        var r=RaceCatalog.get(draft.race);
        text(g,r.name+"  •  "+(draft.female()?"Женский":"Мужской"),42,108,INK,false);
        preview(g,42,130,232,259,partial);
        button(49,366,37,18,"<",true,()->angle-=.65f);
        button(230,366,37,18,">",true,()->angle+=.65f);
        String[] tabs={"Основа","Уши","Рога","Волосы","Отметины","Фигура","Одежда"};
        for(int i=0;i<tabs.length;i++) {int n=i;button(290+i*66,105,63,23,tabs[i],true,()->{tab=n;nameFocus=false;},tab==i);}
        panel(g,288,134,470,255);
        switch(tab) {
            case 0 -> {
                text(g,"Имя персонажа",302,142,INK,false);
                button(468,138,273,20,draft.name.isBlank()?"Введите имя (необязательно)":draft.name,true,()->{nameFocus=true;},nameFocus);
                text(g,"Скин",302,170,INK,false);
                button(435,163,143,23,"Мой скин",true,()->draft.usePlayerSkin=true,draft.usePlayerSkin);
                button(584,163,157,23,"Шаблон",true,()->draft.usePlayerSkin=false,!draft.usePlayerSkin);
                if(draft.usePlayerSkin) text(g,"Цвет кожи задаётся вашим скином",302,196,0xFF786951,false);
                colors(g,"Кожа / чешуя","skin",218,draft.skin,!draft.usePlayerSkin,n->draft.skin=n);
                colors(g,"Глаза","eyes",264,draft.eyes,true,n->draft.eyes=n);
                number(g,"Рост, блоки",317,draft.height,.05f,r.minHeight,r.maxHeight,n->draft.height=n,true);
                text(g,"Меняет модель, хитбокс, камеру и досягаемость",302,352,0xFF786951,false);
                text(g,"Причёски и костюм настраиваются по отдельности",302,370,0xFF786951,false);
            }
            case 1 -> {
                cycle(g,"Форма ушей",147,draft.ears,r.earStyles,n->draft.ears=n,true);
                text(g,r.pointedEars?"Острые уши обязательны":"Варианты ограничены происхождением",302,181,0xFF786951,false);
                number(g,"Наклон",213,draft.earPitch,5,-60,60,n->draft.earPitch=n,draft.ears!=0);
                number(g,"Разворот",247,draft.earYaw,5,-60,60,n->draft.earYaw=n,draft.ears!=0);
                number(g,"Крен",281,draft.earRoll,5,-60,60,n->draft.earRoll=n,draft.ears!=0);
                number(g,"Высота крепления",315,draft.earOffset,.25f,-2,2,n->draft.earOffset=n,draft.ears!=0);
                text(g,"Оба уха изменяются зеркально",302,354,0xFF786951,false);
            }
            case 2 -> {
                int[] styles=r.hornStyles;
                if(!r.hornsRequired || (r.algnor() && draft.female())) {styles=prepend(styles,0);}
                cycle(g,"Форма рогов",147,draft.horns,styles,n->draft.horns=n,r.hornsAllowed);
                colors(g,"Цвет рогов","horn",187,draft.hornColor,r.hornsAllowed,n->draft.hornColor=n);
                number(g,"Наклон",230,draft.hornPitch,5,-60,60,n->draft.hornPitch=n,r.hornsAllowed);
                number(g,"Разворот",261,draft.hornYaw,5,-60,60,n->draft.hornYaw=n,r.hornsAllowed);
                number(g,"Крен",292,draft.hornRoll,5,-60,60,n->draft.hornRoll=n,r.hornsAllowed);
                number(g,"Размер",323,draft.hornSize,.05f,.65f,1.35f,n->draft.hornSize=n,r.hornsAllowed);
                button(302,358,203,21,draft.hornOnForehead?"Крепление: лоб":"Крепление: макушка",r.hornsAllowed,()->draft.hornOnForehead=!draft.hornOnForehead);
                number(g,"Высота",360,draft.hornOffset,.2f,-.2f,2,n->draft.hornOffset=n,r.hornsAllowed,510);
            }
            case 3 -> hairPage(g,r);
            case 4 -> {
                int[] tattoos=new int[r.tattoos+1];for(int i=0;i<tattoos.length;i++)tattoos[i]=i;
                cycle(g,"Татуировка",147,draft.tattoo,tattoos,n->draft.tattoo=n,r.tattoos>0);
                colors(g,"Цвет узора","tattoo",190,draft.tattooColor,r.tattoos>0,n->draft.tattooColor=n);
                button(302,245,217,23,"Свечение: "+(draft.glow?"да":"нет"),r.id.equals("orthosian"),()->draft.glow=!draft.glow);
                text(g,r.id.equals("jahar")?"Белые глаза светятся всегда":"Свечение не освещает мир вокруг",302,285,0xFF786951,false);
            }
            case 6 -> clothingPage(g);
            case 5 -> {
                button(302,144,180,22,"Хвост: "+(draft.tail==0?"нет":"олений"),r.tailAllowed,()->draft.tail=1-draft.tail);
                boolean chest=draft.female() && !r.id.equals("forged");
                if(breastAdvanced) {
                    number(g,"Подвижность",186,draft.bounce,.025f,0,.5f,n->draft.bounce=n,chest);
                    number(g,"Мягкость",222,draft.floppiness,.05f,.25f,1,n->draft.floppiness=n,chest);
                    button(302,267,306,24,"Физика под бронёй: "+yes(draft.armorPhysics),chest,()->draft.armorPhysics=!draft.armorPhysics);
                    button(302,324,306,24,"Основные настройки груди",true,()->breastAdvanced=false);
                    text(g,FemaleGenderBridge.available()?"Рендер: Female Gender Mod 3.2.2":"Рендер: встроенная простая модель",302,371,0xFF786951,false);
                    break;
                }
                number(g,"Размер груди",186,draft.bust,.05f,0,.8f,n->draft.bust=n,chest);
                number(g,"Смещение X",216,draft.bustX,.1f,-1,1,n->draft.bustX=n,chest);
                number(g,"Смещение Y",246,draft.bustY,.1f,-1,1,n->draft.bustY=n,chest);
                number(g,"Смещение Z",276,draft.bustZ,.1f,-1,0,n->draft.bustZ=n,chest);
                number(g,"Расстояние",306,draft.cleavage,.01f,0,.1f,n->draft.cleavage=n,chest);
                button(302,341,144,20,"Физика: "+yes(draft.breastPhysics),chest,()->draft.breastPhysics=!draft.breastPhysics);
                button(451,341,142,20,"Под бронёй: "+yes(draft.showInArmor),chest,()->draft.showInArmor=!draft.showInArmor);
                button(598,341,143,20,"Форма: "+(draft.uniboob?"единая":"парная"),chest,()->draft.uniboob=!draft.uniboob);
                button(302,366,306,18,"Дополнительные настройки груди",chest,()->breastAdvanced=true);
            }
        }
    }
    private void hairPage(GuiGraphics g,RaceDefinition r) {
        button(302,145,210,23,beardPage?"← Причёски":"Настройки бороды",true,()->beardPage=!beardPage);
        if(beardPage) {
            cycle(g,"Борода",194,draft.beard,new int[]{0,1,2,3},n->draft.beard=n,r.beardAllowed);
            colors(g,"Цвет бороды","hair",239,draft.hair,r.beardAllowed,n->draft.hair=n);
            cycle(g,"Старая причёска",290,draft.hairStyle,new int[]{0,1,2,3,4},n->{draft.hairStyle=n;draft.hairAsset="";},r.hairAllowed);
            text(g,"Цвет старых причёсок совпадает с цветом бороды",302,340,0xFF786951,false);
            return;
        }
        var list=AppearanceCatalog.hair();var asset=AppearanceCatalog.hair(draft.hairAsset);
        text(g,"Причёска",302,186,INK,false);
        chooser(435,179,306,asset==null?(draft.hairStyle==0?"Нет":"Старая причёска"):asset.name(),r.hairAllowed,delta->{
            int i=-1;for(int n=0;n<list.size();n++)if(list.get(n).id().equals(draft.hairAsset))i=n;
            int next=Math.floorMod(i+1+delta,list.size()+1)-1;
            draft.hairAsset=next<0?"":list.get(next).id();draft.hairStyle=0;
            draft.hairMain=draft.hairDecor1=draft.hairDecor2=0;
        });
        if(asset!=null) assetColors(g,asset,225,new int[]{draft.hairMain,draft.hairDecor1,draft.hairDecor2},(i,n)->{
            if(i==0)draft.hairMain=n;else if(i==1)draft.hairDecor1=n;else draft.hairDecor2=n;
        },r.hairAllowed);
        if(!r.hairAllowed)text(g,"У этой расы волосы недоступны",302,367,0xFF786951,false);
    }
    private void clothingPage(GuiGraphics g) {
        var outfits=AppearanceCatalog.outfits(draft.sex);
        var outfit=outfits.stream().filter(o->o.id().equals(draft.outfitPreset)).findFirst().orElse(null);
        text(g,"Комплект",302,150,INK,false);
        chooser(435,141,306,outfit==null?(draft.wardrobeEnabled?"Свой комплект":"Базовая одежда"):outfit.name(),true,delta->{
            int i=outfit==null?-1:outfits.indexOf(outfit);int n=Math.floorMod(i+1+delta,outfits.size()+1)-1;
            AppearanceCatalog.outfit(draft,n<0?"":outfits.get(n).id());
        });
        String group=AppearanceCatalog.GROUPS.get(garmentGroup);
        text(g,"Часть костюма",302,185,INK,false);
        chooser(435,176,306,AppearanceCatalog.GROUP_NAMES.get(garmentGroup),true,delta->garmentGroup=Math.floorMod(garmentGroup+delta,AppearanceCatalog.GROUPS.size()));
        var choice=draft.wardrobe.get(group);var asset=choice==null?null:AppearanceCatalog.clothing(choice.asset);
        var list=AppearanceCatalog.clothes(draft.sex,group);
        chooser(302,211,439,asset==null?"Нет":asset.name(),!list.isEmpty(),delta->{
            int i=asset==null?-1:list.indexOf(asset),n=Math.floorMod(i+1+delta,list.size()+1)-1;
            draft.wardrobeEnabled=true;draft.outfitPreset="";
            if(n<0)draft.wardrobe.remove(group);else draft.wardrobe.put(group,new CharacterData.Garment(list.get(n).id()));
        });
        if(asset!=null)assetColors(g,asset,252,new int[]{choice.main,choice.decor1,choice.decor2},(i,n)->{
            if(i==0)choice.main=n;else if(i==1)choice.decor1=n;else choice.decor2=n;
        },true);
    }
    private void chooser(int x,int y,int w,String value,boolean enabled,IntConsumer change) {
        button(x,y,25,25,"<",enabled,()->change.accept(-1));
        button(x+29,y,w-58,25,value,enabled,()->change.accept(1));
        button(x+w-25,y,25,25,">",enabled,()->change.accept(1));
    }
    private void assetColors(GuiGraphics g,AppearanceCatalog.Asset asset,int y,int[] values,java.util.function.BiConsumer<Integer,Integer> setter,boolean enabled) {
        for(int i=0;i<3;i++) {
            var channel=asset.controls().get(AppearanceCatalog.CHANNELS.get(i));if(channel==null)continue;
            int row=y+i*44,ci=i;
            text(g,i==0?"Основной цвет":"Декор "+i,302,row,INK,false);
            button(302,row+13,111,18,"Исходный",enabled,()->setter.accept(ci,0),values[i]==0);
            int count=channel.colors().size()-1;
            for(int n=1;n<=count;n++) {
                int index=n,x=435+(n-1)*51;var color=channel.colors().get(n);
                controls.add(new Control(x,row,45,29,"",color.label()+" • "+channel.affects(),enabled,()->setter.accept(ci,index)));
                g.fill(x+3,row+3,x+42,row+26,0xFF000000|color.rgb());
                if(values[i]==n){g.fill(x,row,x+45,row+2,GOLD);g.fill(x,row+27,x+45,row+29,GOLD);}
            }
        }
    }
    private void confirmationPage(GuiGraphics g,float partial) {
        preview(g,42,107,234,281,partial);
        panel(g,291,107,466,281);
        var r=RaceCatalog.get(draft.race);
        text(g,"ЛИЧНОЕ ДЕЛО ПРИБЫВШЕГО",307,119,0xFF746040,false);
        text(g,"Имя: "+(draft.name.isBlank()?minecraft.player.getGameProfile().getName():draft.name),307,144,INK,false);
        text(g,"Происхождение: "+r.name,307,165,INK,false);
        text(g,"Пол: "+(draft.female()?"Женский":"Мужской")+"   •   Рост: "+format(draft.height)+" блока",307,186,INK,false);
        text(g,"Скин: "+(draft.usePlayerSkin?"собственный":"шаблон")+"   •   Отметины: "+draft.tattoo,307,207,INK,false);
        text(g,"Уши: "+style(draft.ears)+"   •   Рога: "+style(draft.horns)+"   •   Хвост: "+yes(draft.tail>0),307,228,INK,false);
        traits(g,r,307,252,433,129);
    }
    private void preview(GuiGraphics g,int x,int y,int w,int h,float partial) {
        g.fill(x,y,x+w,y+h,0xFF3B4244);
        for(int line=0;line<8;line++) g.fill(x+8,y+line*34,x+w-8,y+line*34+1,0xFF484F4D);
        text(g,"ПРЕДПРОСМОТР",x+w/2,y+9,0xFFD2C3A9,true);
        if(minecraft.player==null)return;
        if(previewModel==null || previewModel.level()!=minecraft.level || !previewModel.getUUID().equals(minecraft.player.getUUID()))
            previewModel=new net.minecraft.client.player.RemotePlayer(minecraft.level,minecraft.player.getGameProfile());
        var model=previewModel;
        model.yBodyRot=model.yBodyRotO=180;model.setYRot(180);model.yRotO=180;
        model.yHeadRot=model.yHeadRotO=180;model.setXRot(0);model.xRotO=0;
        model.tickCount=minecraft.player.tickCount;
        ClientCharacters.preview=draft;ClientCharacters.previewPlayer=model.getUUID();ClientCharacters.previewEntity=model;
        try {
            int size=Math.max(1,(int)((h-34)/2.45f*1.8f/draft.height));
            g.pose().pushPose();
            try {
                g.pose().translate(-panX/zoom,-panY/zoom,0);g.pose().scale(1/zoom,1/zoom,1);
                int left=Math.round(panX+(x+8)*zoom),top=Math.round(panY+(y+24)*zoom);
                int right=Math.round(panX+(x+w-8)*zoom),bottom=Math.round(panY+(y+h-10)*zoom);
                g.enableScissor(left,top,right,bottom);
                try {
                    var rotation=new org.joml.Quaternionf().rotateZ((float)Math.PI).rotateY(angle).rotateX(-.08f);
                    InventoryScreen.renderEntityInInventory(g,(left+right)/2f,bottom-8*zoom,Math.round(size*zoom),
                        new org.joml.Vector3f(),rotation,null,model);
                } finally {g.disableScissor();}
            } finally {g.pose().popPose();}
        } finally {
            ClientCharacters.preview=null;ClientCharacters.previewPlayer=null;ClientCharacters.previewEntity=null;
        }
    }

    private void portrait(GuiGraphics g,RaceDefinition r,int x,int y,int size) {
        g.fill(x-3,y-3,x+size+3,y+size+3,0xFF625C4A);
        g.fill(x,y,x+size,y+size,0xFF394145);
        // Original transparent portrait assets; no dragon survival artwork.
        g.blit(Civitatum.res("textures/gui/portraits/"+r.id+".png"),x,y,size,size,0,0,1254,1254,1254,1254);
    }
    private void traits(GuiGraphics g,RaceDefinition r,int x,int y,int w,int h) {
        String content="+ "+r.positive+"\n− "+r.negative;
        if(!r.active.isBlank()) {
            String price=r.magic && net.neoforged.fml.ModList.get().isLoaded("ars_nouveau")
                ? "мана Ars Nouveau" : r.food+" питания";
            content+="\n"+r.active+"\nЦена: "+price+"  •  Откат: "+r.cooldown+" сек.";
        }
        var lines=font.split(Component.literal(content),w-20);
        int visible=h/11;
        if(lines.size()>visible) visible=Math.max(1,(h-12)/11);
        if(lines.size()>visible) {
            portraitScroll=Math.max(0,Math.min(portraitScroll,lines.size()-visible));
            text(g,"Колесо мыши: особенности",x+w-font.width("Колесо мыши: особенности"),y+h-8,0xFF786951,false);
        } else portraitScroll=0;
        for(int i=0;i<visible && i+portraitScroll<lines.size();i++)
            g.drawString(font,lines.get(i+portraitScroll),x,y+i*11,0xFF534837,false);
    }
    private void colors(GuiGraphics g,String label,String part,int y,int chosen,boolean enabled,IntConsumer setter) {
        text(g,label,302,y,INK,false);
        var r=RaceCatalog.get(draft.race);var palette=r.palette(part);
        for(int i=0;i<palette.length;i++) {
            int index=i,x=435+i*51;
            var control=new Control(x,y-3,45,24,"",palette[i]+(enabled?"":" • цвет задаётся вашим скином или недоступен"),enabled,()->setter.accept(index));
            controls.add(control);
            g.fill(x+3,y,x+42,y+18,0xFF000000|r.color(part,i));
            if(i==chosen) {g.fill(x,y-3,x+45,y-1,GOLD);g.fill(x,y+20,x+45,y+22,GOLD);}
        }
    }
    private void number(GuiGraphics g,String name,int y,float value,float step,float min,float max,Consumer<Float> setter,boolean enabled) {
        number(g,name,y,value,step,min,max,setter,enabled,302);
    }
    private void number(GuiGraphics g,String name,int y,float value,float step,float min,float max,Consumer<Float> setter,boolean enabled,int labelX) {
        int buttons=labelX==302?584:620;
        text(g,name,labelX,y+3,INK,false);
        text(g,format(value),buttons-12,y+3,enabled?INK:0xFF998D79,true);
        button(buttons,y-2,53,22,"−",enabled,()->setter.accept(Math.max(min,Math.round((value-step)*100)/100f)));
        button(buttons+60,y-2,53,22,"+",enabled,()->setter.accept(Math.min(max,Math.round((value+step)*100)/100f)));
    }
    private void cycle(GuiGraphics g,String name,int y,int value,int[] options,IntConsumer setter,boolean enabled) {
        text(g,name,302,y+4,INK,false);
        button(435,y-2,306,25,style(value),enabled,()->{
            int current=0;for(int i=0;i<options.length;i++) if(options[i]==value)current=i;
            setter.accept(options[(current+1)%options.length]);
        });
    }
    private static int[] prepend(int[] array,int first) {int[] result=new int[array.length+1];result[0]=first;System.arraycopy(array,0,result,1,array.length);return result;}
    private static String yes(boolean value){return value?"да":"нет";}
    private static String format(float value){return String.format(Locale.ROOT,"%.2f",value).replace('.',',');}
    private static String style(int value){return value==0?"Нет":"Вариант "+value;}
    private void button(int x,int y,int w,int h,String label,boolean enabled,Runnable action){button(x,y,w,h,label,enabled,action,false);}
    private void button(int x,int y,int w,int h,String label,boolean enabled,Runnable action,boolean selected){
        controls.add(new Control(x,y,w,h,label,selected?"Выбрано":enabled?"":"Недоступно для выбранной расы / пола",enabled,action));
    }
    private void draw(GuiGraphics g,Control b,int index) {
        boolean hovered=b.inside(mouseX,mouseY)||focus==index;
        if(b.text.isEmpty()) {
            if(hovered) {g.fill(b.x,b.y,b.x+2,b.y+b.h,GOLD);g.fill(b.x+b.w-2,b.y,b.x+b.w,b.y+b.h,GOLD);}
            if(!b.enabled) g.fill(b.x+3,b.y+3,b.x+b.w-3,b.y+b.h-3,0xAA9A9386);
            return;
        }
        String sprite=!b.enabled?"widget/button_disabled":hovered?"widget/button_highlighted":"widget/button";
        g.blitSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace(sprite),b.x,b.y,b.w,b.h);
        if(b.tip.equals("Выбрано")) { g.fill(b.x,b.y,b.x+b.w,b.y+2,GOLD);g.fill(b.x,b.y+b.h-2,b.x+b.w,b.y+b.h,GOLD); }
        String label=b.text;
        while(font.width(label)>b.w-12 && label.length()>1)label=label.substring(0,label.length()-1);
        text(g,label,b.x+b.w/2,b.y+(b.h-8)/2,b.enabled?0xFFF5F1E8:0xFF9C9C94,true);
    }
    private void parchment(GuiGraphics g,int x,int y,int w,int h){
        g.fill(x,y,x+w,y+h,0xFF564532);g.fill(x+4,y+4,x+w-4,y+h-4,0xFFE8DBBA);
        g.fill(x+9,y+9,x+w-9,y+h-9,0xFFF0E5C9);
        for(int i=0;i<4;i++){int yy=y+18+i*94;g.fill(x+10,yy,x+w-10,yy+1,0x115B4127);}
        g.fill(x+12,y+12,x+18,y+18,GOLD);g.fill(x+w-18,y+12,x+w-12,y+18,GOLD);
        g.fill(x+12,y+h-18,x+18,y+h-12,GOLD);g.fill(x+w-18,y+h-18,x+w-12,y+h-12,GOLD);
    }
    private void panel(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,0xFFB7A37C);g.fill(x+1,y+1,x+w-1,y+h-1,0xFFE6D8B9);}
    private void text(GuiGraphics g,String s,int x,int y,int color,boolean centered){g.drawString(font,s,centered?x-font.width(s)/2:x,y,color,false);}
    private void centeredWrapped(GuiGraphics g,String s,int x,int y,int w,int color){
        var lines=font.split(Component.literal(s),w);for(var line:lines){g.drawString(font,line,x+(w-font.width(line))/2,y,color,false);y+=11;}
    }
    private void wrapped(GuiGraphics g,String s,int x,int y,int w,int color,int spacing,int maxLines){
        int i=0;for(var line:font.split(Component.literal(s),w)){if(i++>=maxLines)break;g.drawString(font,line,x,y,color,false);y+=spacing;}
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        geometry();double mx=(x-panX)/zoom,my=(y-panY)/zoom;
        if(button==0)for(int i=controls.size()-1;i>=0;i--) {
            var c=controls.get(i);
            if(c.inside(mx,my)) {
                if(c.enabled){focus=i;error="";if(!c.text.equals(draft.name))nameFocus=false;c.action.run();minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK,1));}
                return true;
            }
        }
        if(button==0 && stage==2 && mx>=42 && mx<274 && my>=130 && my<366){portraitDragging=true;return true;}
        nameFocus=false;return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(portraitDragging){angle+=(float)(dx/zoom)*.035f;return true;}return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){portraitDragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        portraitScroll=Math.max(0,portraitScroll-(int)vertical*2);return true;
    }
    @Override public boolean charTyped(char character,int modifiers) {
        if(nameFocus && !Character.isISOControl(character) && character!='§' && draft.name.length()<32){draft.name+=character;return true;}
        return super.charTyped(character,modifiers);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(nameFocus) {
            if(key==GLFW.GLFW_KEY_BACKSPACE){if(!draft.name.isEmpty())draft.name=draft.name.substring(0,draft.name.length()-1);return true;}
            if(key==GLFW.GLFW_KEY_ENTER){nameFocus=false;return true;}
        }
        if(key==GLFW.GLFW_KEY_TAB){focus=(focus+1)%Math.max(1,controls.size());return true;}
        if((key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_SPACE) && focus>=0 && focus<controls.size()){
            var c=controls.get(focus);if(c.enabled)c.action.run();return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void onClose() {
        ClientCharacters.preview=null;ClientCharacters.previewPlayer=null;ClientCharacters.previewEntity=null;
        PacketDistributor.sendToServer(new RaceNetwork.Serverbound(3,token,""));
        minecraft.setScreen(null);
    }
}
