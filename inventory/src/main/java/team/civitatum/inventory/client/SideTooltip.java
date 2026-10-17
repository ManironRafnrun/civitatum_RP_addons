package team.civitatum.inventory.client;

import java.util.List;
import java.util.ArrayList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** A stable reading position next to a window, bounded by the viewport. */
public final class SideTooltip {
    public static void render(GuiGraphics g,Font font,InventoryPanel panel,List<Component> text){
        if(text.isEmpty())return;
        int sw=g.guiWidth(),sh=g.guiHeight(),w=Math.min(230,Math.max(110,sw/3));
        int left=panel.x-8-w,right=panel.x+panel.width+8;
        int x=left>=4?left:right+w<=sw-4?right:Math.max(4,Math.min(sw-w-4,left));
        var lines=new ArrayList<FormattedCharSequence>();for(var line:text)lines.addAll(font.split(line,w-16));
        int h=Math.min(sh-16,lines.size()*12+16),y=Math.max(8,Math.min(panel.y+20,sh-h-8));
        g.pose().pushPose();g.pose().translate(0,0,800);
        g.fill(x,y,x+w,y+h,0xF21C1812);MorrowindSkin.frame(g,x,y,w,h,true);
        g.enableScissor(x+6,y+6,x+w-6,y+h-6);
        for(int i=0;i<lines.size()&&i*12<h-14;i++)g.drawString(font,lines.get(i),x+8,y+8+i*12,0xE9E3D4,false);
        g.disableScissor();g.flush();g.pose().popPose();
    }
}
