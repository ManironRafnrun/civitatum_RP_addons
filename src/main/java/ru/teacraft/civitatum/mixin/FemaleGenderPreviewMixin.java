package ru.teacraft.civitatum.mixin;

import net.minecraft.world.entity.LivingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.teacraft.civitatum.client.ClientCharacters;

/** The editor uses a deterministic skin-textured preview; world physics remain owned by FGM. */
@Pseudo
@Mixin(targets="com.wildfire.render.GenderLayer",remap=false)
public abstract class FemaleGenderPreviewMixin {
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",at=@At("HEAD"),cancellable=true,require=1)
    private void civitatum$preview(PoseStack pose,MultiBufferSource buffers,int light,LivingEntity entity,
            float walk,float amount,float partial,float age,float yaw,float pitch,CallbackInfo ci) {
        if(ClientCharacters.isPreview(entity))ci.cancel();
    }
}
