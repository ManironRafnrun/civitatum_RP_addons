package team.civitatum.starclock.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.starclock.client.RitualClientState;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("RETURN"))
    private void starclock$ritualCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                        float partialTick, CallbackInfo callback) {
        var mc = Minecraft.getInstance();
        if (detached || entity != mc.player || !RitualClientState.isActive()) return;
        Camera camera = (Camera) (Object) this;
        float envelope = RitualClientState.cameraEnvelope(partialTick);
        double phase = RitualClientState.phaseTicks(partialTick) * Math.PI / 20;
        float yaw = (float) Math.sin(phase) * 0.18F * envelope;
        float pitch = (float) Math.sin(phase * 2) * 0.12F * envelope;
        setRotation(camera.getYRot() + yaw, camera.getXRot() + pitch);
        camera.rotation().rotateZ((float) Math.toRadians(Math.sin(phase) * 0.35 * envelope));
        camera.getUpVector().set(0, 1, 0).rotate(camera.rotation());
        camera.getLeftVector().set(1, 0, 0).rotate(camera.rotation());
    }
}
