package team.civitatum.hud.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import team.civitatum.hud.client.MorrowindSkin;
import team.civitatum.hud.client.RestyledScreens;

/**
 * Applied Energistics 2's panel of upgrade slots draws a light rim in its code, past its restyled pictures: in the
 * restyled screens it is the gold of the frames, as vanilla's white is. Without AE2 installed there is nothing to
 * change.
 */
@Pseudo
@Mixin(targets = "appeng.client.gui.widgets.UpgradesPanel", remap = false)
public abstract class AeUpgradesPanelMixin {
    /** AE2's light grey of the rim, as its code has it. */
    private static final int AE_RIM = 0xFFF2F2F2;

    @ModifyConstant(method = "drawBackgroundLayer(Lnet/minecraft/client/gui/GuiGraphics;"
        + "Lnet/minecraft/client/renderer/Rect2i;Lappeng/client/Point;)V",
        constant = @Constant(intValue = AE_RIM), require = 0)
    private int civitatumHud$goldRim(int colour) {
        return RestyledScreens.restyled(Minecraft.getInstance().screen) ? MorrowindSkin.FRAME_GOLD : colour;
    }
}
