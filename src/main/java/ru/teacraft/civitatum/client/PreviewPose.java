package ru.teacraft.civitatum.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.*;
import ru.teacraft.civitatum.IdleAnimation;

public final class PreviewPose {
    public static void apply(PlayerModel<?> model,float elapsed) {
        var torso=rotation("torso",elapsed);
        var shift=new Vector3f(IdleAnimation.value("torso","x",elapsed),-IdleAnimation.value("torso","y",elapsed),IdleAnimation.value("torso","z",elapsed));
        set(model.body,"torso",elapsed);
        for(var entry:new Object[][]{{model.head,"head"},{model.leftArm,"leftArm"},{model.rightArm,"rightArm"}}) {
            var part=(ModelPart)entry[0];String name=(String)entry[1];
            var position=torso.transform(new Vector3f(part.x,part.y,part.z)).add(shift);
            position.add(IdleAnimation.value(name,"x",elapsed),-IdleAnimation.value(name,"y",elapsed),IdleAnimation.value(name,"z",elapsed));
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
        part.x+=IdleAnimation.value(name,"x",time);part.y-=IdleAnimation.value(name,"y",time);part.z+=IdleAnimation.value(name,"z",time);
        part.setRotation(IdleAnimation.value(name,"pitch",time),IdleAnimation.value(name,"yaw",time),IdleAnimation.value(name,"roll",time));
    }
}
