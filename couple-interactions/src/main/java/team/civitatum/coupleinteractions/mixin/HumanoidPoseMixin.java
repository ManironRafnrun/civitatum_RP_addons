package team.civitatum.coupleinteractions.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        if(team.civitatum.coupleinteractions.client.PoseEngine.animator && player instanceof net.minecraft.client.player.AbstractClientPlayer)return;
        var m=(HumanoidModel<?>)(Object)this;
        var frame=team.civitatum.coupleinteractions.client.PoseEngine.frame(player,age-player.tickCount);
        if(frame!=null){team.civitatum.coupleinteractions.client.PoseEngine.apply(m,frame);couple$posed=true;}
    }
}
