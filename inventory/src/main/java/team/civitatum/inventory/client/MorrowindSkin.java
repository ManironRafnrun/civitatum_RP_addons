package team.civitatum.inventory.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Original tiled textures; panel alpha 102/255 = 40% opacity / 60% transparency. */
public final class MorrowindSkin {
    public static final int PANEL_ALPHA=102;
    private static final ResourceLocation STONE=ResourceLocation.fromNamespaceAndPath("civitatum_inventory","textures/gui/stone.png");
    public static void body(GuiGraphics g,int x,int y,int w,int h) {
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.setColor(1,1,1,(float)((1-InventoryPreferences.get().transparency)*255/PANEL_ALPHA));
        for(int dy=0;dy<h;dy+=32)for(int dx=0;dx<w;dx+=32)
            g.blit(STONE,x+dx,y+dy,0,0,Math.min(32,w-dx),Math.min(32,h-dy),32,32);
        g.flush();g.setColor(1,1,1,1);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
    public static void frame(GuiGraphics g,int x,int y,int w,int h,boolean lit) {
        int gold=lit?0xFFE2C18A:0xFFB29A6B;
        g.fill(x,y,x+w,y+1,0xFF17130E);g.fill(x,y+h-1,x+w,y+h,0xFF17130E);
        g.fill(x,y,x+1,y+h,0xFF17130E);g.fill(x+w-1,y,x+w,y+h,0xFF17130E);
        g.fill(x+1,y+1,x+w-1,y+2,gold);g.fill(x+1,y+1,x+2,y+h-1,gold);
        g.fill(x+2,y+h-2,x+w-1,y+h-1,0xFF54422C);g.fill(x+w-2,y+2,x+w-1,y+h-1,0xFF54422C);
    }
}
