package team.civitatum.couple.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.couple.CarryGeometry;
import team.civitatum.couple.CarryHooks;
import team.civitatum.couple.server.CoupleServer;

@Mixin(Entity.class)
public abstract class EntityCarryMixin {
    @Inject(method="push(Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true)
    private void couple$noPartnerPush(Entity other,CallbackInfo ci){
        if((Object)this instanceof Player a && other instanceof Player b){var s=team.civitatum.couple.SocialHooks.state(a);if(s!=null && (s.first().equals(b.getUUID()) || s.second().equals(b.getUUID())))ci.cancel();}
    }
    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V", at = @At("HEAD"), cancellable = true)
    private void couple$position(Entity passenger, Entity.MoveFunction move, CallbackInfo ci) {
        if ((Object) this instanceof Player carrier && passenger instanceof Player carried && CarryHooks.pair(carrier, carried)) {
            Vec3 pos = CarryGeometry.anchorNow(carrier);
            move.accept(passenger, pos.x, pos.y, pos.z);
            ci.cancel();
        }
    }
    @Inject(method = "getDismountLocationForPassenger", at = @At("HEAD"), cancellable = true)
    private void couple$dismount(LivingEntity passenger, CallbackInfoReturnable<Vec3> cir) {
        if ((Object) this instanceof Player carrier && passenger instanceof Player carried
                && !carrier.level().isClientSide && CarryHooks.pair(carrier, carried)) {
            cir.setReturnValue(CoupleServer.dismount(carrier, carried));
        }
    }
    @Inject(method = "showVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void couple$health(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player carrier && CarryHooks.carrying(carrier)) cir.setReturnValue(false);
    }
}
