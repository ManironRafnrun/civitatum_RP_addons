package team.civitatum.hud.parcool;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import team.civitatum.hud.client.ParCoolEnergyIcons;

/**
 * Draws ParCool's stamina icons in this HUD's colours. {@code LightStaminaHUD.render} picks the sprite for each of
 * its ten icons and draws it with its only {@code blit} call; the icon is drawn from the recoloured texture instead,
 * at the same place and size, so ParCool's choice of state, blinking and shaking stay as they are.
 */
@Mixin(targets = "com.alrex.parcool.client.hud.stamina.LightStaminaHUD")
public abstract class LightStaminaHudMixin {
    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(IIIIIL"
            + "net/minecraft/client/renderer/texture/TextureAtlasSprite;)V")
    )
    private void civitatumHud$icon(GuiGraphics graphics, int x, int y, int z, int width, int height,
                                   TextureAtlasSprite sprite) {
        ParCoolEnergyIcons.draw(graphics, x, y, z, width, height, sprite);
    }
}
