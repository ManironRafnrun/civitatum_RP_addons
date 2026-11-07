package team.civitatum.hud.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.hud.client.CivitatumInventoryScreen;
import team.civitatum.hud.client.TrashCell;
import team.civitatum.hud.client.TrashSlotBridge;

/**
 * Where TrashSlot's cell is, for the clicks it takes and for JEI to keep clear of: in our menu, our cell in the items
 * window ({@link TrashCell}), not the place TrashSlot keeps for the screen's class.
 */
@Pseudo
@Mixin(targets = "net.blay09.mods.trashslot.client.gui.TrashSlotComponent", remap = false)
public abstract class TrashSlotComponentMixin {
    @Inject(method = "isInside", at = @At("HEAD"), cancellable = true, require = 0)
    private void civitatumHud$inside(int x, int y, CallbackInfoReturnable<Boolean> cir) {
        if (ours()) cir.setReturnValue(TrashCell.inside(x, y));
    }

    @Inject(method = "getRectangle", at = @At("HEAD"), cancellable = true, require = 0)
    private void civitatumHud$rectangle(CallbackInfoReturnable<Rect2i> cir) {
        if (!ours()) return;
        int[] cell = TrashCell.rectangle();
        cir.setReturnValue(new Rect2i(cell[0], cell[1], cell[2], cell[3]));
    }

    private static boolean ours() {
        return Minecraft.getInstance().screen instanceof CivitatumInventoryScreen && TrashSlotBridge.active();
    }
}
