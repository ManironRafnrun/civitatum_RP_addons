package team.civitatum.hud.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * A square button with a 16 px icon, a texture or an item; the label is its tooltip and narration.
 * Lit while {@code on} is true.
 */
public final class MorrowindIconButton extends Button {
    public static final int WIDTH = 20, HEIGHT = 18;
    private static final int ICON = 16;
    private final ResourceLocation icon;
    private final Supplier<ItemStack> item;
    private final BooleanSupplier on;

    public MorrowindIconButton(ResourceLocation icon, Component label, BooleanSupplier on, OnPress action) {
        this(icon, null, label, on, action);
    }

    public MorrowindIconButton(Supplier<ItemStack> item, Component label, OnPress action) {
        this(null, item, label, () -> false, action);
    }

    private MorrowindIconButton(ResourceLocation icon, Supplier<ItemStack> item, Component label, BooleanSupplier on,
                                OnPress action) {
        super(0, 0, WIDTH, HEIGHT, label, action, DEFAULT_NARRATION);
        this.icon = icon;
        this.item = item;
        this.on = on;
        setTooltip(Tooltip.create(label));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean lit = on.getAsBoolean() || isHoveredOrFocused() && active;
        MorrowindSkin.body(g, x + 2, y + 2, w - 4, h - 4);
        MorrowindSkin.frame(g, x, y, w, h, lit);
        if (lit) g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x205F4824);
        if (item != null) {
            g.renderItem(item.get(), x + (w - ICON) / 2, y + (h - ICON) / 2);
            if (!active) g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x9918130E);
            return;
        }
        RenderSystem.enableBlend();
        // Dimmed while off, so the state reads at a glance.
        float shade = !active ? 0.4F : on.getAsBoolean() ? 1F : 0.65F;
        g.setColor(shade, shade, shade, 1);
        g.blit(icon, x + (w - ICON) / 2, y + (h - ICON) / 2, 0, 0, ICON, ICON, ICON, ICON);
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }
}
