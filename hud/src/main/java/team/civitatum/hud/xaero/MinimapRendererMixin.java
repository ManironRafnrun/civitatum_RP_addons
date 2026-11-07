package team.civitatum.hud.xaero;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.hud.client.MinimapFrame;
import xaero.hud.minimap.module.MinimapRenderer;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.render.module.ModuleRenderContext;

/**
 * Frames the minimap right after Xaero draws it, where {@code MinimapFrameMixin} recorded the map. The only call to
 * {@code MinimapProcessor.onRender} is past every early return (hidden under F3 or a screen, the "No Minimap" effect,
 * a server ban), so it fires only for a minimap on screen.
 */
@Mixin(MinimapRenderer.class)
public abstract class MinimapRendererMixin {
    @Inject(
        method = "render(Lxaero/hud/minimap/module/MinimapSession;Lxaero/hud/render/module/ModuleRenderContext;"
            + "Lnet/minecraft/client/gui/GuiGraphics;F)V",
        at = @At(value = "INVOKE", target = "Lxaero/common/minimap/MinimapProcessor;onRender("
            + "Lnet/minecraft/client/gui/GuiGraphics;IIIIDIIFLxaero/common/graphics/CustomVertexConsumers;)V",
            shift = At.Shift.AFTER)
    )
    private void civitatumHud$frame(MinimapSession session, ModuleRenderContext context, GuiGraphics graphics,
                                    float partialTick, CallbackInfo ci) {
        MinimapFrame.draw(graphics);
    }
}
