package team.civitatum.coupleinteractions.mixin;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.coupleinteractions.CarryGeometry;
import team.civitatum.coupleinteractions.CarryHooks;

@Mixin(Player.class)
public abstract class PlayerDimensionsMixin {
    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void couple$dimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        Player self = (Player) (Object) this;
        // Player's Entity constructor invokes dimensions before level is assigned.
        if(self.level()!=null){if(CarryHooks.carried(self))cir.setReturnValue(CarryGeometry.DIMENSIONS);else if(team.civitatum.coupleinteractions.SocialHooks.resting(self))cir.setReturnValue(EntityDimensions.scalable(.6f,1.05f).withEyeHeight(.85f));}
    }
}
