package team.civitatum.charactercreation.mixin;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.charactercreation.power.Hooks;

/**
 * Prices of the race and the sign, beside reputation and the Hero of the Village. updateSpecialPrices has a single
 * return at its end; the differences are reset by vanilla when the trade closes.
 */
@Mixin(Villager.class)
public abstract class VillagerMixin {
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void civitatum$prices(Player player, CallbackInfo ci) {
        Hooks.prices((Villager) (Object) this, player);
    }
}
