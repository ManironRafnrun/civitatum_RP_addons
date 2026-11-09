package team.civitatum.charactercreation.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.charactercreation.power.Hooks;

/**
 * Both ways of taking a crafted item end here: a click (ItemStack.onCraftedBy) and a shift-click, which calls the
 * item before moving the stack, when the crafting event already sees a copy.
 */
@Mixin(Item.class)
public abstract class ItemMixin {
    @Inject(method = "onCraftedBy", at = @At("HEAD"))
    private void civitatum$crafted(ItemStack stack, Level level, Player player, CallbackInfo ci) {
        Hooks.crafted(stack, player);
    }
}
