package team.civitatum.hud.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import team.civitatum.hud.client.MorrowindSkin;
import team.civitatum.hud.client.RestyledScreens;

/**
 * Applied Energistics 2 draws the slot under the cursor itself, a light blue rim over a blue fill, past
 * {@code getSlotColor}: in its restyled screens both are our gold. Without AE2 installed there is nothing to change.
 */
@Pseudo
@Mixin(targets = "appeng.client.gui.AEBaseScreen", remap = false)
public abstract class AeSlotHoverMixin {
    /** AE2's colours of the highlight, as its code has them. */
    private static final int AE_RIM = 0xFFDAFFFF, AE_FILL = 0x669CD3FF;
    private static final String HIGHLIGHT = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;"
        + "Lnet/minecraft/world/inventory/Slot;IIF)V";

    @ModifyConstant(method = HIGHLIGHT, constant = @Constant(intValue = AE_RIM), require = 0)
    private int civitatumHud$goldRim(int colour) {
        return RestyledScreens.restyled((net.minecraft.client.gui.screens.Screen) (Object) this)
            ? MorrowindSkin.SLOT_HOVER_EDGE : colour;
    }

    @ModifyConstant(method = HIGHLIGHT, constant = @Constant(intValue = AE_FILL), require = 0)
    private int civitatumHud$goldFill(int colour) {
        return RestyledScreens.restyled((net.minecraft.client.gui.screens.Screen) (Object) this)
            ? MorrowindSkin.SLOT_HOVER : colour;
    }
}
