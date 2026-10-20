package team.civitatum.starclock.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import team.civitatum.starclock.network.ShowCardPayload;
import team.civitatum.starclock.prediction.Orientation;

public final class CardScreen extends Screen {
    private final ShowCardPayload card;

    public CardScreen(ShowCardPayload card) {
        super(Component.translatable("ui.civitatum_starclock.card"));
        this.card = card;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 50, height / 2 + 65, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;
        graphics.fill(0, 0, width, height, 0xD90B1020);
        graphics.fill(cx - 114, cy - 100, cx + 114, cy + 100, 0xFFE3C07B);
        graphics.fill(cx - 112, cy - 98, cx + 112, cy + 98, 0xFFE8DCC0);
        graphics.fill(cx - 106, cy - 92, cx + 106, cy + 57, 0xFF0B1020);
        graphics.drawCenteredString(font, Component.translatable(card.sign().translationKey()),
                cx, cy - 74, 0xFFE3C07B);
        if (card.orientation() != Orientation.NONE) {
            graphics.drawCenteredString(font, Component.translatable("orientation.civitatum_starclock."
                    + (card.orientation() == Orientation.UPRIGHT ? "upright" : "reversed")),
                    cx, cy - 52, 0xFFE8DCC0);
        }
        String meaning = card.sign().translationKey() + "."
                + (card.orientation() == Orientation.NONE ? "any"
                : card.orientation() == Orientation.UPRIGHT ? "upright" : "reversed");
        int y = cy - 22;
        for (var line : font.split(Component.translatable(meaning), 184)) {
            graphics.drawCenteredString(font, line, cx, y, 0xFFE8DCC0);
            y += 12;
        }
        graphics.drawCenteredString(font, Component.translatable("tooltip.civitatum_starclock.tier",
                card.tier().name()), cx, cy + 42, 0xFF8E6FB3);

        if (!card.category().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("ui.civitatum_starclock.event_type",
                    Component.translatable("event_type.civitatum_starclock." + card.category())),
                    cx, cy + 10, 0xFFE3C07B);
        }
        if (!card.target().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("ui.civitatum_starclock.target",
                    Component.translatable("target.civitatum_starclock." + card.target())),
                    cx, cy + 24, 0xFFE8DCC0);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
