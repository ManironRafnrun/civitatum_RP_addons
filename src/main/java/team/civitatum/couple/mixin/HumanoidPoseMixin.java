package team.civitatum.couple.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.couple.CarryHooks;
import team.civitatum.couple.core.CarryPose;

@Mixin(HumanoidModel.class)
public abstract class HumanoidPoseMixin {
    @Unique private boolean couple$posed;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void couple$reset(LivingEntity entity, float walk, float speed, float age, float yaw, float pitch, CallbackInfo ci) {
        if (!couple$posed) return;
        var m = (HumanoidModel<?>) (Object) this;
        m.head.resetPose(); m.hat.resetPose(); m.body.resetPose(); m.leftArm.resetPose(); m.rightArm.resetPose(); m.leftLeg.resetPose(); m.rightLeg.resetPose();
        couple$posed = false;
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void couple$pose(LivingEntity entity, float walk, float speed, float age, float yaw, float pitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        var m = (HumanoidModel<?>) (Object) this;
        if (CarryHooks.carried(player)) {
            m.riding = false;
            m.crouching = false;
            m.body.xRot = -0.10F; m.body.yRot = 0; m.body.zRot = 0;
            m.body.y = 0;
            m.head.y = 0; m.head.zRot = CarryPose.HEAD_ROLL;
            m.head.xRot = CarryPose.HEAD_PITCH;
            m.head.yRot = CarryPose.HEAD_YAW;
            m.rightArm.xRot = -0.95F; m.rightArm.yRot = -0.35F; m.rightArm.zRot = 0.2F;
            m.leftArm.xRot = -1.1F; m.leftArm.yRot = 0.2F; m.leftArm.zRot = -0.18F;
            m.rightArm.y = m.rightArm.getInitialPose().y;
            m.leftArm.y = m.leftArm.getInitialPose().y;
            m.rightLeg.xRot = -0.55F; m.rightLeg.yRot = 0.12F; m.rightLeg.zRot = 0.04F;
            m.leftLeg.xRot = -0.72F; m.leftLeg.yRot = -0.12F; m.leftLeg.zRot = -0.03F;
            m.leftLeg.y = m.rightLeg.y = 12; m.leftLeg.z = m.rightLeg.z = 0;
            couple$posed = true;
        } else if (CarryHooks.carrying(player)) {
            m.rightArm.xRot = -1.18F; m.rightArm.yRot = -0.22F; m.rightArm.zRot = 0.04F;
            m.leftArm.xRot = -1.32F; m.leftArm.yRot = 0.26F; m.leftArm.zRot = -0.04F;
            couple$posed = true;
        }
        if (couple$posed) m.hat.copyFrom(m.head);
    }
}
