package team.civitatum.starclock.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.starclock.client.RitualClientState;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void starclock$blockRepeatedUse(CallbackInfo callback) {
        if (RitualClientState.isActive()) callback.cancel();
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void starclock$blockAttack(CallbackInfoReturnable<Boolean> callback) {
        if (RitualClientState.isActive()) callback.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void starclock$blockHeldAttack(boolean leftClick, CallbackInfo callback) {
        if (RitualClientState.isActive()) callback.cancel();
    }
}
