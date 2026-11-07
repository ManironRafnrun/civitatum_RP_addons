package team.civitatum.starclock.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.starclock.client.render.FirstPersonRitualRenderer;

@Mixin(value = ItemInHandRenderer.class, priority = 1500)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void starclock$renderRitual(AbstractClientPlayer player, float partialTick, float pitch,
                                       InteractionHand hand, float swing, ItemStack stack, float equip,
                                       PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (FirstPersonRitualRenderer.render(player, hand, stack, pose, buffers, light)) callback.cancel();
    }
}
