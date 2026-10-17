package team.civitatum.inventory.client;

public final class InventoryPanel {
    public final String id;
    public final int minWidth, minHeight;
    private final int defaultX, defaultY, defaultWidth, defaultHeight;
    public int x, y, width, height;
    public boolean visible = true;
    public InventoryPanel(String id, int x, int y, int width, int height, int minWidth, int minHeight) {
        this.id=id; this.x=x; this.y=y; this.width=width; this.height=height;
        this.minWidth=minWidth; this.minHeight=minHeight;
        defaultX=x;defaultY=y;defaultWidth=width;defaultHeight=height;
    }
    public boolean contains(double mx, double my) { return visible && mx>=x && my>=y && mx<x+width && my<y+height; }
    public boolean titleContains(double mx, double my) { return contains(mx,my) && my<y+18; }
    public boolean gripContains(double mx, double my) { return contains(mx,my) && mx>=x+width-10 && my>=y+height-10; }
    public static final int LEFT=1, RIGHT=2, TOP=4, BOTTOM=8;
    public int resizeEdges(double mx,double my) {
        if(!contains(mx,my))return 0;
        int edge=0;
        if(mx<x+4)edge|=LEFT; if(mx>=x+width-4)edge|=RIGHT;
        if(my<y+4)edge|=TOP; if(my>=y+height-4)edge|=BOTTOM;
        if(gripContains(mx,my))edge|=RIGHT|BOTTOM;
        return edge;
    }
    public void constrain(int screenWidth, int screenHeight) {
        width = Math.min(screenWidth-8, Math.max(minWidth,width));
        height = Math.min(screenHeight-69, Math.max(minHeight,height));
        x=Math.max(4,Math.min(screenWidth-width-4,x));
        y=Math.max(25,Math.min(screenHeight-height-44,y));
    }
    public void restore(InventoryPreferences.SavedPanel saved, int sw, int sh) {
        if(saved==null || !Float.isFinite(saved.x()) || !Float.isFinite(saved.y())) return;
        x=Math.round(saved.x()*sw); y=Math.round(saved.y()*sh);
        width=saved.width(); height=saved.height(); constrain(sw,sh);
    }
    public InventoryPreferences.SavedPanel save(int sw, int sh) {
        return new InventoryPreferences.SavedPanel((float)x/sw,(float)y/sh,width,height);
    }
    public void reset(int sw,int sh) {x=defaultX;y=defaultY;width=defaultWidth;height=defaultHeight;constrain(sw,sh);}
}
