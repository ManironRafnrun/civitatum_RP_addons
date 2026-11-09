package team.civitatum.charactercreation.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.charactercreation.power.WorldEvents;

/**
 * A potion shift-clicked out of the stand: PlayerBrewedPotionEvent gets a copy then, so the real stack is marked
 * here, before it moves. Slots 0..2 are the bottles.
 */
@Mixin(BrewingStandMenu.class)
public abstract class BrewingStandMenuMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"))
    private void civitatum$brewed(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index >= 0 && index < 3)
            WorldEvents.markBrewed(player, ((AbstractContainerMenu) (Object) this).slots.get(index).getItem());
    }
}
