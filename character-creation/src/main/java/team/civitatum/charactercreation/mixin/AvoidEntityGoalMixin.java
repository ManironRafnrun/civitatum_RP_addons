package team.civitatum.charactercreation.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.charactercreation.power.Hooks;

/** Animals do not run from the Beast: the goal that would start fleeing from it does not start. */
@Mixin(AvoidEntityGoal.class)
public abstract class AvoidEntityGoalMixin<T extends LivingEntity> {
    @Shadow
    protected T toAvoid;

    @Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
    private void civitatum$fearless(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && Hooks.isFearless(toAvoid)) cir.setReturnValue(false);
    }
}
