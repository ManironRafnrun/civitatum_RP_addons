package team.civitatum.charactercreation.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.charactercreation.power.Hooks;

/** A lead held by the Spinner does not snap beyond 10 blocks ("поводок не рвётся"); Mob overrides the default. */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "leashTooFarBehaviour", at = @At("HEAD"), cancellable = true)
    private void civitatum$hold(CallbackInfo ci) {
        if (Hooks.isUnbreakableLeash((Mob) (Object) this)) ci.cancel();
    }
}
