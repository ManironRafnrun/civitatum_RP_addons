package team.civitatum.couple.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import team.civitatum.couple.*;
import team.civitatum.couple.core.*;

public final class PoseEngine {
    public static boolean animator;
    public static Player previewFirst,previewSecond;
    public static PoseSettings previewPose;
    public static PoseFrame frame(Player p,float partial){
        if(CccCompat.preview(p))return null;
        Player carrier=CarryHooks.carrier(p);
        var social=ClientState.social(p.getUUID());
        if(social!=null && p!=previewFirst && p!=previewSecond){
            var s=social.state();boolean first=s.first().equals(p.getUUID());
            var other=p.level().getPlayerByUUID(first?s.second():s.first());
            var frame=PoseFrame.social(Interaction.fromWire(s.action()),first,ClientState.ticks-social.started()+partial,s.duration(),p.getScale(),other==null?p.getScale():other.getScale(),s.mounted());
            if(s.mounted())return PoseFrame.carry(carrier!=null,carrier!=null?CarryHooks.tuning(p):PoseSettings.DEFAULT,p.walkAnimation.position(partial),p.walkAnimation.speed(partial)).overlay(frame);
            return frame;
        }
        if(carrier!=null || CarryHooks.carrying(p)){
            var tuning=p==previewSecond?previewPose:carrier!=null?CarryHooks.tuning(p):PoseSettings.DEFAULT;
            var f=PoseFrame.carry(carrier!=null,tuning,p.walkAnimation.position(partial),p.walkAnimation.speed(partial));
            if(carrier==null){f.bone("head",(float)Math.toRadians(p.getXRot()),(float)Math.toRadians(net.minecraft.util.Mth.wrapDegrees(p.getYHeadRot()-p.yBodyRot)),0,0);}
            return f;
        }
        return null;
    }
    public static void preview(Player first,Player second,PoseSettings pose){previewFirst=first;previewSecond=second;previewPose=pose;}
    public static void clearPreview(){previewFirst=previewSecond=null;previewPose=null;}
    public static Player previewCarrier(Player p){return p==previewSecond?previewFirst:null;}
    public static Player previewPassenger(Player p){return p==previewFirst?previewSecond:null;}
    public static PoseSettings tuning(Player p){return p==previewSecond?previewPose:CarryHooks.tuning(p);}
    public static void apply(HumanoidModel<?> m,PoseFrame frame){
        if(frame==null)return;
        Map<String,ModelPart> parts=Map.of("head",m.head,"torso",m.body,"leftArm",m.leftArm,"rightArm",m.rightArm,"leftLeg",m.leftLeg,"rightLeg",m.rightLeg);
        frame.bones.forEach((name,b)->{var part=parts.get(name);if(part==null)return;float w=frame.weight;part.xRot=net.minecraft.util.Mth.lerp(w,part.xRot,b.pitch());part.yRot=net.minecraft.util.Mth.lerp(w,part.yRot,b.yaw());part.zRot=net.minecraft.util.Mth.lerp(w,part.zRot,b.roll());if(b.position()){part.x=net.minecraft.util.Mth.lerp(w,part.x,b.x());part.y=net.minecraft.util.Mth.lerp(w,part.y,b.y());part.z=net.minecraft.util.Mth.lerp(w,part.z,b.z());}});m.hat.copyFrom(m.head);
    }
    private PoseEngine(){}
}
