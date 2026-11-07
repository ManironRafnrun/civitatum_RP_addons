package team.civitatum.hud.ftb;

import dev.ftb.mods.ftblibrary.sidebar.SidebarButtonManager;
import dev.ftb.mods.ftblibrary.sidebar.SidebarGroupGuiButton;
import dev.ftb.mods.ftblibrary.sidebar.SidebarGuiButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.hud.client.SidebarRow;

/**
 * Fixes the sidebar as {@link SidebarRow}: every button in one row in their sort order, wherever FTB Library's config
 * or its button editor would put them. The editor itself is switched off, it has nothing left to change.
 */
@Mixin(SidebarGroupGuiButton.class)
public abstract class SidebarGroupMixin {
    @Shadow
    boolean gridStartBottom;
    @Shadow
    boolean gridStartRight;
    @Shadow
    int yRenderStart;
    @Shadow
    int xRenderStart;
    @Shadow
    private int lastMouseClickButton;
    @Shadow
    private int currentGirdWidth;

    /** The method then counts the visible buttons of each row, so hidden ones leave no gap. */
    @Inject(method = "ensureGridAlignment", at = @At("HEAD"))
    private void civitatumHud$putIntoOneRow(CallbackInfo ci) {
        int column = 0;
        for (SidebarGuiButton button : SidebarButtonManager.INSTANCE.getButtonList()) {
            button.setEnabled(true);
            button.setGridLocation(column++, 0);
        }
    }

    @Inject(method = "updateWidgetSize", at = @At("TAIL"))
    private void civitatumHud$placeRow(CallbackInfo ci) {
        var window = Minecraft.getInstance().getWindow();
        gridStartBottom = false;
        gridStartRight = false;
        xRenderStart = SidebarRow.left(window.getGuiScaledWidth(), currentGirdWidth);
        yRenderStart = SidebarRow.top(window.getGuiScaledHeight());
        civitatumHud$setBounds();
    }

    @Unique
    private void civitatumHud$setBounds() {
        var widget = (AbstractWidget) (Object) this;
        widget.setX(xRenderStart);
        widget.setY(yRenderStart);
        widget.setWidth(SidebarRow.width(currentGirdWidth));
        widget.setHeight(SidebarRow.CELL);
        SidebarGroupGuiButton.lastDrawnArea = new Rect2i(widget.getX(), widget.getY(), widget.getWidth(),
            widget.getHeight());
    }

    /** A right click opened the button editor. */
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void civitatumHud$noEditor(CallbackInfo ci) {
        if (lastMouseClickButton == 1) ci.cancel();
    }
}
