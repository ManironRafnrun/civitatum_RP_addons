package team.civitatum.charactercreation.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.charactercreation.power.TowerKey;

/** The Tower's key opens a locked container; a double chest asks both halves here. */
@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerBlockEntityMixin {
    @Inject(method = "canOpen", at = @At("HEAD"), cancellable = true)
    private void civitatum$key(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (TowerKey.isOpening(player)) cir.setReturnValue(true);
    }
}
