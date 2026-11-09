package team.civitatum.charactercreation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;
import team.civitatum.charactercreation.power.Hooks;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** The storm dragonborn's air; the client draws the bubbles from the same number. */
    @Inject(method = "getMaxAirSupply", at = @At("HEAD"), cancellable = true)
    private void civitatum$air(CallbackInfoReturnable<Integer> cir) {
        int air = Hooks.maxAir((Entity) (Object) this);
        if (air > 0) cir.setReturnValue(air);
    }

    /** The Spinner's lead holds anything leashable; only an entity already on a lead is refused. */
    @WrapOperation(method = "interact", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Leashable;canHaveALeashAttachedToIt()Z"))
    private boolean civitatum$leash(Leashable leashable, Operation<Boolean> original,
                                    @Local(argsOnly = true) Player player) {
        if (Hooks.mayLeash(player, (Entity) (Object) this)) return !leashable.isLeashed();
        return original.call(leashable);
    }

}
