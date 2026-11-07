package team.civitatum.hud.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.hud.client.RestyledScreens;

/**
 * Labels of the restyled block screens in a light colour with a shadow. Every {@code drawString} ends in one of these
 * two, so a title drawn as a component, a string or a character sequence is caught alike. A dark colour set in the
 * text's own style (AE2's sky stone chest names its title {@code #575e54}) would win over the light one, so it is
 * made light too.
 */
@Mixin(GuiGraphics.class)
public abstract class ContainerLabelMixin {
    @Shadow
    public abstract int drawString(Font font, String text, float x, float y, int color, boolean shadow);

    @Shadow
    public abstract int drawString(Font font, FormattedCharSequence text, float x, float y, int color,
                                   boolean shadow);

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I", at = @At("HEAD"),
        cancellable = true)
    private void civitatumHud$lightString(Font font, String text, float x, float y, int color, boolean shadow,
                                          CallbackInfoReturnable<Integer> result) {
        if (RestyledScreens.darkLabel(color)) {
            result.setReturnValue(drawString(font, text, x, y, RestyledScreens.LABEL, true));
        }
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I",
        at = @At("HEAD"), cancellable = true)
    private void civitatumHud$lightSequence(Font font, FormattedCharSequence text, float x, float y, int color,
                                            boolean shadow, CallbackInfoReturnable<Integer> result) {
        if (RestyledScreens.darkLabel(color)) {
            var light = RestyledScreens.lightStyles(text);
            result.setReturnValue(drawString(font, light, x, y, RestyledScreens.LABEL, true));
        }
    }
}
