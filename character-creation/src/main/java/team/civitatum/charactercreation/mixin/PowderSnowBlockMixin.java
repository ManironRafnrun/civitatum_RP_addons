package team.civitatum.charactercreation.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.charactercreation.power.Hooks;

/** The frost dragonborn stands on powder snow; asked on the client too, which predicts the movement. */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void civitatum$walk(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Hooks.isSnowWalker(entity)) cir.setReturnValue(true);
    }
}
