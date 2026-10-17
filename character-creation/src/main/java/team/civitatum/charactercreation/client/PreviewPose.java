package team.civitatum.charactercreation.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.*;
import team.civitatum.charactercreation.IdleAnimation;

public final class PreviewPose {
    public static final class Saved implements AutoCloseable {
        private final java.util.Map<ModelPart,net.minecraft.client.model.geom.PartPose> poses=new java.util.LinkedHashMap<>();
        private final java.util.Map<ModelPart,Boolean> visibility=new java.util.LinkedHashMap<>();
        Saved(PlayerModel<?> m){for(var p:new ModelPart[]{m.head,m.hat,m.body,m.jacket,m.leftArm,m.leftSleeve,m.rightArm,m.rightSleeve,m.leftLeg,m.leftPants,m.rightLeg,m.rightPants}){poses.put(p,p.storePose());visibility.put(p,p.visible);p.resetPose();}}
        @Override public void close(){poses.forEach(ModelPart::loadPose);visibility.forEach((p,v)->p.visible=v);}
    }
    public static Saved preserve(PlayerModel<?> m){return new Saved(m);}
    public static void apply(PlayerModel<?> model,float elapsed) {
        var torso=rotation("torso",elapsed);
        var shift=new Vector3f(IdleAnimation.offset("torso","x",elapsed),IdleAnimation.offset("torso","y",elapsed),IdleAnimation.offset("torso","z",elapsed));
        set(model.body,"torso",elapsed);
        for(var entry:new Object[][]{{model.head,"head"},{model.leftArm,"leftArm"},{model.rightArm,"rightArm"}}) {
            var part=(ModelPart)entry[0];String name=(String)entry[1];
            var position=torso.transform(new Vector3f(part.x,part.y,part.z)).add(shift);
            position.add(IdleAnimation.offset(name,"x",elapsed),IdleAnimation.offset(name,"y",elapsed),IdleAnimation.offset(name,"z",elapsed));
            part.setPos(position.x,position.y,position.z);
            var angles=new Quaternionf(torso).mul(rotation(name,elapsed)).getEulerAnglesZYX(new Vector3f());
            part.setRotation(angles.x,angles.y,angles.z);
        }
        set(model.leftLeg,"leftLeg",elapsed);set(model.rightLeg,"rightLeg",elapsed);
        model.hat.copyFrom(model.head);model.jacket.copyFrom(model.body);model.leftSleeve.copyFrom(model.leftArm);model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);model.rightPants.copyFrom(model.rightLeg);
    }
    private static Quaternionf rotation(String name,float time){return new Quaternionf().rotationZYX(IdleAnimation.value(name,"roll",time),IdleAnimation.value(name,"yaw",time),IdleAnimation.value(name,"pitch",time));}
    private static void set(ModelPart part,String name,float time) {
        part.x+=IdleAnimation.offset(name,"x",time);part.y+=IdleAnimation.offset(name,"y",time);part.z+=IdleAnimation.offset(name,"z",time);
        part.setRotation(IdleAnimation.value(name,"pitch",time),IdleAnimation.value(name,"yaw",time),IdleAnimation.value(name,"roll",time));
    }
}
