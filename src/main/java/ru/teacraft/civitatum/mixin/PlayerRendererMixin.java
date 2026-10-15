package ru.teacraft.civitatum.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.teacraft.civitatum.client.ClientCharacters;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method="renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",at=@At("HEAD"),cancellable=true)
    private void civitatum$previewName(AbstractClientPlayer p,net.minecraft.network.chat.Component name,PoseStack pose,
            net.minecraft.client.renderer.MultiBufferSource buffers,int light,float partial,CallbackInfo ci) {
        if(ClientCharacters.isPreview(p))ci.cancel();
    }
    @Inject(method="scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V",at=@At("TAIL"))
    private void civitatum$pose(AbstractClientPlayer p,PoseStack pose,float partial,CallbackInfo ci) {
        var c=ClientCharacters.get(p);
        if(!c.selected) return;
        if(ClientCharacters.isPreview(p)) {
            float ratio=c.height/1.8f;
            pose.scale(ratio,ratio,ratio);
        }
        if(p.isCrouching() && c.height>=2.2) {
            float vanillaCrouch=1.5f*c.height/1.8f;
            if(vanillaCrouch>1.8f) pose.scale(1,1.8f/vanillaCrouch,1);
        }
    }
}
