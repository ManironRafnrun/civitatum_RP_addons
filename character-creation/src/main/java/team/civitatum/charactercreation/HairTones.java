package team.civitatum.charactercreation;

/** RGB arithmetic shared by the editor, compositor and tests. No client classes. */
public final class HairTones {
    private static final float[] LEVELS={1,.38f,.60f,.80f,1.15f,1.40f,1.75f};
    public static String labelKey(int index){return "appearance.civitatum.tone."+index;}
    public static int shade(int base,int index,int originalMain,int originalShade) {
        float level=index==0?Math.clamp(luma(originalShade)/Math.max(1,luma(originalMain)),.2f,2.5f):LEVELS[Math.clamp(index,1,6)];
        return scale(base,level);
    }
    private static float luma(int rgb){return (rgb>>16&255)*.2126f+(rgb>>8&255)*.7152f+(rgb&255)*.0722f;}
    private static int scale(int rgb,float level){
        int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
        // Scaling all components together preserves the selected hue, including bright tones.
        level=Math.min(level,255f/Math.max(1,Math.max(r,Math.max(g,b))));
        return Math.round(r*level)<<16|Math.round(g*level)<<8|Math.round(b*level);
    }
    /** Input pixels use NativeImage's ABGR order; alpha always belongs to the authored hair. */
    public static int weighted(int original,int neutral,int[] weights,int[] colors) {
        int result=original&0xFF000000;
        for(int shift=0;shift<=16;shift+=8){
            int sum=0;
            for(int i=0;i<weights.length;i++){
                int value=colors[i]<0?(original>>>shift&255):Math.round((neutral>>>shift&255)*(colors[i]>>>(16-shift)&255)/255f);
                sum+=weights[i]*value;
            }
            result|=Math.clamp(Math.round(sum/255f),0,255)<<shift;
        }
        return result;
    }
    private HairTones(){}
}
