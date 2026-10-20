package team.civitatum.starclock.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.starclock.client.RitualClientState;

// Только рантайм-переключатель. Код и ассеты Punchy не включаются в Starclock.
@Pseudo
@Mixin(targets = "punchy.config.PunchyConfig", remap = false)
public abstract class PunchyConfigMixin {
    @Inject(method = "isModEnabled", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void starclock$pausePunchy(CallbackInfoReturnable<Boolean> callback) {
        if (RitualClientState.isActive()) callback.setReturnValue(false);
    }
}
