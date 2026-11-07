package team.civitatum.hud.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import team.civitatum.hud.client.MorrowindSkin;
import team.civitatum.hud.client.RestyledScreens;

/**
 * The slot highlight in vanilla's see-through white, wherever it comes from, turns gold while a restyled screen is
 * open. Every highlight ends in this fill, whether a screen asks vanilla for it with the default colour (the grid of
 * Tom's Storage terminals) or draws it itself; {@link SlotHoverMixin} covers those that ask for the slot's colour.
 */
@Mixin(GuiGraphics.class)
public abstract class SlotHighlightFillMixin {
    /** Vanilla's highlight, see-through white. */
    private static final int WHITE_HOVER = 0x80FFFFFF;
    private static final String FILL = "fillGradient(Lnet/minecraft/client/renderer/RenderType;IIIIIII)V";

    @ModifyVariable(method = FILL, at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int civitatumHud$goldFrom(int colour) {
        return gold(colour);
    }

    @ModifyVariable(method = FILL, at = @At("HEAD"), argsOnly = true, ordinal = 5)
    private int civitatumHud$goldTo(int colour) {
        return gold(colour);
    }

    private static int gold(int colour) {
        return colour == WHITE_HOVER && RestyledScreens.restyled(Minecraft.getInstance().screen)
            ? MorrowindSkin.SLOT_HOVER : colour;
    }
}
