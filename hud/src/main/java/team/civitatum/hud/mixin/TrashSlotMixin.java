package team.civitatum.hud.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.hud.client.CivitatumInventoryScreen;
import team.civitatum.hud.client.TrashSlotBridge;

/**
 * TrashSlot draws its cell after the screen's background, in its own frame and at the place it keeps for the
 * screen's class. Our full-screen menu has no such place, and draws each slot inside its window: the cell is ours
 * there ({@link TrashSlotBridge}). Its hints and the tooltip of the trashed item, drawn elsewhere, stay.
 */
@Pseudo
@Mixin(targets = "net.blay09.mods.trashslot.client.TrashSlotGuiHandler", remap = false)
public abstract class TrashSlotMixin {
    @Inject(method = "onBackgroundDrawn", at = @At("HEAD"), cancellable = true, require = 0)
    private static void civitatumHud$ownCell(CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof CivitatumInventoryScreen && TrashSlotBridge.active()) ci.cancel();
    }
}
