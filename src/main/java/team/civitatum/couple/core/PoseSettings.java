package team.civitatum.couple.core;

import com.google.gson.Gson;

/** Recipient-owned tuning, bounded identically on client and server. Angles are degrees. */
public record PoseSettings(float head,float arm,float bank,float side,float up,float forward) {
    private static final Gson JSON=new Gson();
    public static final PoseSettings DEFAULT=new PoseSettings(24,55,-65,0,0,0);
    private static float bound(float n,float lo,float hi,float fallback){return Float.isFinite(n)?Math.clamp(n,lo,hi):fallback;}
    public PoseSettings safe(){return new PoseSettings(bound(head,-20,65,24),bound(arm,20,110,55),bound(bank,-80,-45,-65),bound(side,-.25f,.25f,0),bound(up,-.3f,.3f,0),bound(forward,-.25f,.35f,0));}
    public String json(){return JSON.toJson(safe());}
    public static PoseSettings read(String text){try{var p=JSON.fromJson(text,PoseSettings.class);return p==null?DEFAULT:p.safe();}catch(RuntimeException e){return DEFAULT;}}
    public float headRadians(){return (float)Math.toRadians(head);}
    public float armRadians(){return (float)Math.toRadians(arm);}
}
