package team.civitatum.charactercreation.mixin;

import net.minecraft.world.entity.LivingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.charactercreation.client.ClientCharacters;
import team.civitatum.charactercreation.client.FemaleGenderBridge;

/**
 * The editor's figure is drawn by FGM as the world's players are, with the editor's values (see
 * FemaleGenderBridge.beginPreview): the built-in model, two plain boxes, did not show FGM's shape.
 */
@Pseudo
@Mixin(targets="com.wildfire.render.GenderLayer",remap=false)
public abstract class FemaleGenderPreviewMixin {
    @Unique
    private static final String RENDER = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V";

    @Inject(method=RENDER,at=@At("HEAD"),require=1)
    private void civitatum$beginPreview(PoseStack pose,MultiBufferSource buffers,int light,LivingEntity entity,
            float walk,float amount,float partial,float age,float yaw,float pitch,CallbackInfo ci) {
        if(ClientCharacters.isPreview(entity))FemaleGenderBridge.beginPreview(entity,ClientCharacters.preview);
    }

    @Inject(method=RENDER,at=@At("RETURN"),require=1)
    private void civitatum$endPreview(PoseStack pose,MultiBufferSource buffers,int light,LivingEntity entity,
            float walk,float amount,float partial,float age,float yaw,float pitch,CallbackInfo ci) {
        if(ClientCharacters.isPreview(entity))FemaleGenderBridge.endPreview(entity);
    }
}
