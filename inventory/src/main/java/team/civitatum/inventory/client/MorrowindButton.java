package team.civitatum.inventory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class MorrowindButton extends Button {
    public MorrowindButton(Component label,OnPress action,int width) {super(0,0,width,18,label,action,DEFAULT_NARRATION);}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float tick) {
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        MorrowindSkin.body(g,x+2,y+2,w-4,h-4);MorrowindSkin.frame(g,x,y,w,h,isHoveredOrFocused()&&active);
        if(isHoveredOrFocused()&&active)g.fill(x+2,y+2,x+w-2,y+h-2,0x205F4824);
        renderScrollingString(g,Minecraft.getInstance().font,2,active?0xFFE8D9B7:0xFF90846C);
    }
}
