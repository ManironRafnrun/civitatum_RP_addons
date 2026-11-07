package team.civitatum.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A frameless icon button styled like {@link TextMenuButton}: the white icon is tinted and shadowed like its labels.
 * The label is shown as a tooltip.
 */
public final class IconMenuButton extends AbstractButton {
    /** Side of the bundled icons, one line of the vanilla font plus the descender. */
    private static final int ICON_SIZE = 9;
    /** Vanilla draws a text shadow at a quarter of the text brightness. */
    private static final float SHADOW_BRIGHTNESS = 0.25F;

    private final Runnable action;
    private final ResourceLocation icon;
    private final float iconScale;

    public IconMenuButton(int x, int y, int size, Component label, ResourceLocation icon, float scale,
        Runnable action) {
        super(x, y, size, size, label);
        this.action = action;
        this.icon = icon;
        this.iconScale = scale;
        setTooltip(Tooltip.create(label));
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int color = TextMenuButton.colorOf(this);
        float r = (float) (color >> 16 & 0xFF) / 255.0F;
        float g = (float) (color >> 8 & 0xFF) / 255.0F;
        float b = (float) (color & 0xFF) / 255.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(getX() + (width - ICON_SIZE * iconScale) / 2.0F,
            getY() + (height - ICON_SIZE * iconScale) / 2.0F, 0.0F);
        graphics.pose().scale(iconScale, iconScale, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r * SHADOW_BRIGHTNESS, g * SHADOW_BRIGHTNESS, b * SHADOW_BRIGHTNESS, 1.0F);
        graphics.blit(icon, 1, 1, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        RenderSystem.setShaderColor(r, g, b, 1.0F);
        graphics.blit(icon, 0, 0, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
