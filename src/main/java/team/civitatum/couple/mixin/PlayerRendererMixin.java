package team.civitatum.couple.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.couple.CarryGeometry;
import team.civitatum.couple.CarryHooks;
import team.civitatum.couple.core.CarryPose;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("HEAD"), cancellable = true)
    private void couple$rotation(AbstractClientPlayer player, PoseStack pose, float bob, float yaw, float partial, float scale, CallbackInfo ci) {
        Player carrier = CarryHooks.carrier(player);
        if (carrier == null) return;
        float bodyYaw = Mth.rotLerp(partial, carrier.yBodyRotO, carrier.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        pose.translate(0, CarryPose.LIFT, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(CarryPose.BANK_DEGREES));
        // Turn the passenger onto their back before banking across the arms.
        // Reversing these rotations makes the body lean forwards instead.
        pose.mulPose(Axis.YP.rotationDegrees(CarryPose.LOCAL_YAW_DEGREES));
        pose.translate(0, -CarryPose.PIVOT_HEIGHT, 0);
        ci.cancel();
    }
    @Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void couple$offset(AbstractClientPlayer player, float partial, CallbackInfoReturnable<Vec3> cir) {
        Player carrier = CarryHooks.carrier(player);
        if (carrier != null) cir.setReturnValue(CarryGeometry.anchor(carrier, partial).subtract(player.getPosition(partial)));
    }
}
