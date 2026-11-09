package team.civitatum.charactercreation.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.charactercreation.client.EnderSense;

/** «Эндер-слух»: the outline drawn by this client only, so other players do not see it. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void civitatum$sense(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (EnderSense.isSensed(entity)) cir.setReturnValue(true);
    }
}
