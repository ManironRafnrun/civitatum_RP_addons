package team.civitatum.charactercreation.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.charactercreation.client.FemaleGenderBridge;

/**
 * FGM loads the local player's settings from its file after joining and saves them from its own screen, over the
 * character's: the character is written again at the next frame.
 */
@Pseudo
@Mixin(targets="com.wildfire.main.entitydata.PlayerConfig",remap=false)
public abstract class FemaleGenderConfigMixin {
    @Inject(method="loadCachedPlayer",at=@At("RETURN"),require=1)
    private static void civitatum$loaded(CallbackInfoReturnable<?> cir) {
        FemaleGenderBridge.invalidate();
    }

    @Inject(method="saveGenderInfo",at=@At("RETURN"),require=1)
    private static void civitatum$saved(CallbackInfo ci) {
        FemaleGenderBridge.invalidate();
    }
}
