package team.civitatum.charactercreation.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.charactercreation.power.Hooks;

/** The orc's «Воинская честь»: the same path as gold, so piglins treat it exactly as a player in gold. */
@Mixin(PiglinAi.class)
public abstract class PiglinAiMixin {
    @Inject(method = "isWearingGold", at = @At("HEAD"), cancellable = true)
    private static void civitatum$honour(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Hooks.isPiglinNeutral(entity)) cir.setReturnValue(true);
    }
}
