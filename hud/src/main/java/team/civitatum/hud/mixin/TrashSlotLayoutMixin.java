package team.civitatum.hud.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.hud.client.TrashSlotBridge;

/**
 * TrashSlot's cell on every menu with the player's inventory in it, from the start: TrashSlot itself shows it so
 * only on vanilla's inventory, crafting table and chests ({@link TrashSlotBridge#layout}).
 */
@Pseudo
@Mixin(targets = "net.blay09.mods.trashslot.client.LayoutManager", remap = false)
public abstract class TrashSlotLayoutMixin {
    @Inject(method = "getLayout", at = @At("RETURN"), cancellable = true, require = 0)
    private static void civitatumHud$shownAtStart(AbstractContainerScreen<?> screen,
                                                  CallbackInfoReturnable<Object> cir) {
        cir.setReturnValue(TrashSlotBridge.layout(screen, cir.getReturnValue()));
    }
}
