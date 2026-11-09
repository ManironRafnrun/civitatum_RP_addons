package team.civitatum.charactercreation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import team.civitatum.charactercreation.power.Hooks;

/**
 * The Beast tames a wolf at the first try: its taming roll in tryToTame (the only nextInt there) comes out as 0, the
 * winning number. NeoForge's AnimalTameEvent may still refuse.
 */
@Mixin(Wolf.class)
public abstract class WolfTamingMixin {
    @ModifyExpressionValue(method = "tryToTame",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    private int civitatum$sure(int roll, @Local(argsOnly = true) Player player) {
        return Hooks.isTamer(player) ? 0 : roll;
    }
}
