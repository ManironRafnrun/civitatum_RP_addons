package team.civitatum.hud.xaero;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.core.XaeroMinimapCore;

/**
 * Keeps Xaero's "Potion Effects Push Box" off: it pushes the HUD away from the effects' vanilla place in the top
 * right corner, but the effects now sit next to the minimap. This method is the only place that switches the box on
 * (from Xaero's effects layer listener and from its optional in-game HUD mixin).
 */
@Mixin(XaeroMinimapCore.class)
public abstract class XaeroMinimapCoreMixin {
    @Inject(method = "onRenderStatusEffectOverlayPost", at = @At("HEAD"), cancellable = true)
    private static void civitatumHud$keepPushBoxOff(GuiGraphics graphics, CallbackInfo ci) {
        ci.cancel();
    }
}
