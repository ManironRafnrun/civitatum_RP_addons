package team.civitatum.charactercreation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import team.civitatum.charactercreation.power.Hooks;

/**
 * The Beast tames a parrot at the first try: its taming roll in mobInteract (the only nextInt there) comes out as 0,
 * the winning number. NeoForge's AnimalTameEvent may still refuse.
 */
@Mixin(Parrot.class)
public abstract class ParrotTamingMixin {
    @ModifyExpressionValue(method = "mobInteract",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    private int civitatum$sure(int roll, @Local(argsOnly = true) Player player) {
        return Hooks.isTamer(player) ? 0 : roll;
    }
}
