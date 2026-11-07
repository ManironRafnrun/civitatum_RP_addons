package team.civitatum.hud.client;

import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** The mod's config screen in the mod list; every change is saved at once. */
public final class HudSettingsScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    /** Button height plus a gap. */
    private static final int ROW = 24;
    /** The style button, three size sliders, an empty row and Done. */
    private static final int ROWS = 6;
    private static final int TEXT = 0xFFE8D9B7;
    private final Screen parent;

    public HudSettingsScreen(Screen parent) {
        super(CivitatumInventoryScreen.tr("settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = (width - BUTTON_WIDTH) / 2, y = top();
        var style = addRenderableWidget(new MorrowindButton(styleLabel(), b -> {
            var preferences = InventoryPreferences.get();
            preferences.hotbarStyle = preferences.hotbarStyle.next();
            preferences.save();
            b.setMessage(styleLabel());
        }, BUTTON_WIDTH));
        style.setPosition(x, y);
        var preferences = InventoryPreferences.get();
        slider("bars_scale", preferences.barsScale, v -> InventoryPreferences.get().barsScale = v, x, y + ROW);
        slider("hotbar_scale", preferences.hotbarScale, v -> InventoryPreferences.get().hotbarScale = v,
            x, y + 2 * ROW);
        slider("effects_scale", preferences.effectsScale, v -> InventoryPreferences.get().effectsScale = v,
            x, y + 3 * ROW);
        var done = addRenderableWidget(new MorrowindButton(CommonComponents.GUI_DONE, b -> onClose(), BUTTON_WIDTH));
        done.setPosition(x, y + (ROWS - 1) * ROW);
    }

    private void slider(String label, double current, DoubleConsumer change, int x, int y) {
        var slider = addRenderableWidget(new InventorySlider(label, current,
            InventoryPreferences.MIN_HUD_SCALE, InventoryPreferences.MAX_HUD_SCALE, change));
        slider.setWidth(BUTTON_WIDTH);
        slider.setPosition(x, y);
    }

    private int top() {
        return (height - ROWS * ROW) / 2;
    }

    private static Component styleLabel() {
        return CivitatumInventoryScreen.tr("hotbar", InventoryPreferences.get().hotbarStyle.label());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float tick) {
        super.render(g, mx, my, tick);
        g.drawCenteredString(font, title, width / 2, top() - ROW, TEXT);
    }

    /** The sliders save when released; arrow keys move them without a release. */
    @Override
    public void removed() {
        InventoryPreferences.get().save();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
