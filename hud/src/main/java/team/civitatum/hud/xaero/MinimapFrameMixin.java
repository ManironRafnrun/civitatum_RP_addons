package team.civitatum.hud.xaero;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import team.civitatum.hud.client.MinimapFrame;
import xaero.common.minimap.render.MinimapRenderer;
import xaero.common.minimap.render.MinimapRendererHelper;
import xaero.hud.minimap.common.config.MinimapConfigConstants;

/**
 * Switches Xaero's own minimap frame off whatever its "Frame Style" setting is, and records where the map is drawn:
 * the frame of the inventory windows, square or round, goes around it instead ({@code MinimapRendererMixin}).
 * Xaero draws its frame only for a style below the last one, "Off"; the shader colour a coloured style sets is reset
 * right after the frame code. The square and the round map are each drawn by one call in this method.
 * The FBO and safe-mode renderers both inherit it.
 */
@Mixin(MinimapRenderer.class)
public abstract class MinimapFrameMixin {
    @ModifyExpressionValue(
        method = "renderMinimap",
        at = @At(value = "INVOKE", target = "Lxaero/lib/client/config/ClientConfigManager;getEffective("
            + "Lxaero/lib/common/config/option/ConfigOption;)Ljava/lang/Object;"),
        slice = @Slice(
            from = @At(value = "FIELD", target = "Lxaero/hud/minimap/common/config/option/MinimapProfiledConfigOptions;"
                + "FRAME:Lxaero/lib/common/config/option/RangeConfigOption;"),
            to = @At(value = "FIELD", target = "Lxaero/hud/minimap/common/config/MinimapConfigConstants;"
                + "FRAME_NAMES:[Lnet/minecraft/network/chat/Component;"))
    )
    private Object civitatumHud$frameOff(Object frameStyle) {
        return MinimapConfigConstants.FRAME_NAMES.length - 1;
    }

    /** The square map: a texture {@code width} by {@code height} at {@code (x, y)}. */
    @WrapOperation(
        method = "renderMinimap",
        at = @At(value = "INVOKE", target = "Lxaero/common/minimap/render/MinimapRendererHelper;"
            + "drawMyTexturedModalRectPre(Lcom/mojang/blaze3d/vertex/PoseStack;FFIIFFFFF)V")
    )
    private void civitatumHud$squareMap(MinimapRendererHelper helper, PoseStack pose, float x, float y, int u, int v,
                                        float width, float height, float textureWidth, float textureHeight,
                                        float depth, Operation<Void> original) {
        MinimapFrame.mapDrawn(pose.last().pose(), x, y, width, height, false);
        original.call(helper, pose, x, y, u, v, width, height, textureWidth, textureHeight, depth);
    }

    /** The round map: an ellipse inscribed in a square {@code size} wide at {@code (x, y)}. */
    @WrapOperation(
        method = "renderMinimap",
        at = @At(value = "INVOKE", target = "Lxaero/common/minimap/render/MinimapRendererHelper;"
            + "drawTexturedElipseInsideRectangle(Lcom/mojang/blaze3d/vertex/PoseStack;DIFFIIFF)V")
    )
    private void civitatumHud$roundMap(MinimapRendererHelper helper, PoseStack pose, double angle, int segments,
                                       float x, float y, int u, int v, float size, float textureSize,
                                       Operation<Void> original) {
        MinimapFrame.mapDrawn(pose.last().pose(), x, y, size, size, true);
        original.call(helper, pose, angle, segments, x, y, u, v, size, textureSize);
    }
}
