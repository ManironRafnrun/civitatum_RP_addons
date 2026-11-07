package team.civitatum.hud.client;

import java.util.function.DoubleConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider drawn like {@link MorrowindButton}: a stone track in a frame and a framed stone handle. */
public final class InventorySlider extends AbstractSliderButton {
    /** The HUD sizes move in 1% steps. */
    private static final double PERCENT_STEPS = 100;
    /** As wide as vanilla's handle. */
    private static final int HANDLE = 8;
    private static final int TEXT = 0xFFE8D9B7, DISABLED_TEXT = 0xFF90846C, LIT_SHADE = 0x205F4824;
    private final String label;
    private final double min, max;
    private final DoubleConsumer change;

    public InventorySlider(String label, double current, double min, double max, DoubleConsumer change) {
        super(0, 0, 158, 18, Component.empty(), (current - min) / (max - min));
        this.label = label;
        this.min = min;
        this.max = max;
        this.change = change;
        updateMessage();
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float tick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean lit = isHoveredOrFocused() && active;
        MorrowindSkin.body(g, x + 2, y + 2, w - 4, h - 4);
        MorrowindSkin.frame(g, x, y, w, h, false);
        int hx = x + (int) (value * (w - HANDLE));
        MorrowindSkin.body(g, hx + 2, y + 2, HANDLE - 4, h - 4);
        MorrowindSkin.frame(g, hx, y, HANDLE, h, lit);
        if (lit) g.fill(hx + 2, y + 2, hx + HANDLE - 2, y + h - 2, LIT_SHADE);
        renderScrollingString(g, Minecraft.getInstance().font, 2, active ? TEXT : DISABLED_TEXT);
    }

    @Override
    protected void updateMessage() {
        setMessage(CivitatumInventoryScreen.tr(label, Math.round((min + (max - min) * value) * 100)));
    }

    @Override
    protected void applyValue() {
        double actual = Math.round((min + (max - min) * value) * PERCENT_STEPS) / PERCENT_STEPS;
        value = (actual - min) / (max - min);
        change.accept(actual);
    }

    @Override
    public void onRelease(double x, double y) {
        super.onRelease(x, y);
        InventoryPreferences.get().save();
    }
}
