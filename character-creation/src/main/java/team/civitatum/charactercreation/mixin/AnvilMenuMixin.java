package team.civitatum.charactercreation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import team.civitatum.charactercreation.power.Hooks;

/**
 * The anvil's price for the dwarf, the goblin and the Smith. The price is the clamp of the summed costs, taken once
 * in createResult; changed there, the "too expensive" check and the levels taken both see the new price.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
    private AnvilMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) {
        super(type, id, inventory, access);
        throw new AssertionError();
    }

    @ModifyExpressionValue(method = "createResult",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(JJJ)J"))
    private long civitatum$cost(long cost) {
        return Hooks.anvilCost(this.player, cost);
    }

}
