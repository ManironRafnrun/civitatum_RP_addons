package team.civitatum.coupleinteractions.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.coupleinteractions.server.CoupleServer;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportMixin {
    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"))
    private void couple$teleport(double x, double y, double z, CallbackInfo ci) {
        CoupleServer.beforeTeleport((ServerPlayer) (Object) this);
    }
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"))
    private void couple$teleportLevel(net.minecraft.server.level.ServerLevel level, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        CoupleServer.beforeTeleport((ServerPlayer) (Object) this);
    }
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"))
    private void couple$teleportCommand(net.minecraft.server.level.ServerLevel level, double x, double y, double z,
                                       java.util.Set<net.minecraft.world.entity.RelativeMovement> relative, float yaw, float pitch, CallbackInfoReturnable<Boolean> cir) {
        CoupleServer.beforeTeleport((ServerPlayer) (Object) this);
    }
    @Inject(method = "teleportRelative", at = @At("HEAD"))
    private void couple$teleportRelative(double x, double y, double z, CallbackInfo ci) {
        CoupleServer.beforeTeleport((ServerPlayer) (Object) this);
    }
}
