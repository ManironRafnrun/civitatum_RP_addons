package team.civitatum.coupleinteractions.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.coupleinteractions.CarryHooks;

@Mixin(ItemInHandLayer.class)
public abstract class HeldItemMixin {
    // ItemInHandLayer also has a bridge render(..., Entity, ...); the descriptor pins the real one
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
        + "ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void couple$hands(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity,
                             float walk, float speed, float partial, float age, float yaw, float pitch, CallbackInfo ci) {
        if (entity instanceof Player player && CarryHooks.occupied(player)) ci.cancel();
    }
}
