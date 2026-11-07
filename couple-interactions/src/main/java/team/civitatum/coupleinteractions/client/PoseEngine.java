package team.civitatum.coupleinteractions.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import team.civitatum.coupleinteractions.*;
import team.civitatum.coupleinteractions.core.*;

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
    public static void apply(HumanoidModel<?> m, PoseFrame frame) {
        if (frame == null) return;
        float w = frame.weight;
        frame.bones.forEach((name, b) -> {
            var part = part(m, name);
            if (part == null) return;
            part.xRot = Mth.lerp(w, part.xRot, b.pitch());
            part.yRot = Mth.lerp(w, part.yRot, b.yaw());
            part.zRot = Mth.lerp(w, part.zRot, b.roll());
            if (b.position()) {
                part.x = Mth.lerp(w, part.x, b.x());
                part.y = Mth.lerp(w, part.y, b.y());
                part.z = Mth.lerp(w, part.z, b.z());
            }
        });
        m.hat.copyFrom(m.head);
    }

    /** The model part a bone of {@link PoseFrame} drives; the frame uses PlayerAnimator's bone names. */
    private static ModelPart part(HumanoidModel<?> m, String bone) {
        return switch (bone) {
            case "head" -> m.head;
            case "torso" -> m.body;
            case "leftArm" -> m.leftArm;
            case "rightArm" -> m.rightArm;
            case "leftLeg" -> m.leftLeg;
            case "rightLeg" -> m.rightLeg;
            default -> null;
        };
    }
    private PoseEngine(){}
}
