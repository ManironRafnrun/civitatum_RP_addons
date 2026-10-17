package team.civitatum.coupleinteractions.mixin;

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
import team.civitatum.coupleinteractions.CarryGeometry;
import team.civitatum.coupleinteractions.CarryHooks;
import team.civitatum.coupleinteractions.core.CarryPose;
import team.civitatum.coupleinteractions.SocialHooks;
import team.civitatum.coupleinteractions.core.Interaction;
import team.civitatum.coupleinteractions.client.PoseEngine;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method="renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",at=@At("HEAD"),cancellable=true)
    private void couple$previewName(AbstractClientPlayer player,net.minecraft.network.chat.Component name,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light,float partial,CallbackInfo ci){if(player==PoseEngine.previewFirst || player==PoseEngine.previewSecond)ci.cancel();}
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("HEAD"), cancellable = true)
    private void couple$rotation(AbstractClientPlayer player, PoseStack pose, float bob, float yaw, float partial, float scale, CallbackInfo ci) {
        if(PoseEngine.animator)team.civitatum.coupleinteractions.client.AnimatorCompat.prepare(player,partial);
        Player carrier = CarryHooks.carrier(player);
        if(carrier==null){
            var s=SocialHooks.state(player);var frame=PoseEngine.frame(player,partial);
            if(s!=null && !s.mounted() && frame!=null){
                boolean first=s.first().equals(player.getUUID());
                float facing=s.yaw()+(first || s.action()==Interaction.REST.ordinal()?0:180);
                pose.mulPose(Axis.YP.rotationDegrees(180-facing));
                pose.translate(0,frame.rootY*frame.weight,0);
                pose.mulPose(Axis.XP.rotation(frame.rootPitch*frame.weight));pose.mulPose(Axis.ZP.rotation(frame.rootRoll*frame.weight));ci.cancel();
            }
            return;
        }
        float bodyYaw = Mth.rotLerp(partial, carrier.yBodyRotO, carrier.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        pose.translate(0, CarryPose.LIFT, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(PoseEngine.tuning(player).bank()));
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
