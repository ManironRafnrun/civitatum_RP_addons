package ru.teacraft.civitatum.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.teacraft.civitatum.client.*;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void civitatum$idle(LivingEntity player,float walk,float amount,float age,float yaw,float pitch,CallbackInfo ci) {
        if(ClientCharacters.isPreview(player))PreviewPose.apply((PlayerModel<?>)(Object)this,ClientCharacters.previewTime);
    }
}
