package team.civitatum.starclock.client;

import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import team.civitatum.starclock.network.ShowCardPayload;
import team.civitatum.starclock.prediction.Constellation;
import team.civitatum.starclock.prediction.Orientation;

public final class CardScreen extends Screen {
    private final ShowCardPayload card;
    private int age;
    private Button close;
    private float scale;
    private int left;
    private int top;

    public CardScreen(ShowCardPayload card) {
        super(Component.translatable("ui.civitatum_starclock.card"));
        this.card = card;
    }

    @Override
    protected void init() {
        scale = Math.min(1.4F, Math.min(width / 440.0F, height / 264.0F));
        left = Math.round((width - 420 * scale) / 2);
        top = Math.round((height - 244 * scale) / 2);
        close = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(Math.round(left + 256 * scale), Math.round(top + 210 * scale),
                        Math.max(60, Math.round(120 * scale)), 20).build());
        close.active = age >= 14;
    }

    @Override
    public void tick() {
        age++;
        if (age == 7) StarclockAudio.revealCard(card);
        close.active = age >= 14;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RitualArt.background(graphics, width, height);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(scale, scale, 1);
        float flip = Math.clamp((age + partialTick) / 14.0F, 0, 1);
        float horizontal = Math.max(0.025F, Math.abs((float) Math.cos(Math.PI * flip)));
        boolean front = flip >= 0.5F;
        pose.pushPose();
        pose.translate(88, 117, 0);
        pose.scale(horizontal, 1, 1);
        String face = !front ? "card_back" : card.sign() == Constellation.SERPENT ? "card_zmei" : "card_front";
        RitualArt.blit(graphics, "gui/" + face, -72, -108, 144, 216, 256, 384);
        if (front) {
            graphics.drawCenteredString(font, Component.translatable(card.sign().translationKey()),
                    0, -88, 0xFFE3C07B);
            if (card.sign() != Constellation.SERPENT) {
                pose.pushPose();
                if (card.orientation() == Orientation.REVERSED) pose.mulPose(Axis.ZP.rotationDegrees(180));
                RitualArt.blit(graphics, "constellation/" + card.sign().id(), -62, -67, 124, 124, 512, 512);
                pose.popPose();
            }
            graphics.drawCenteredString(font, Component.translatable("tooltip.civitatum_starclock.tier",
                    card.tier().name()), 0, 76, 0xFFE8DCC0);
        }
        pose.popPose();
        if (front) {
            int x = 185;
            graphics.drawString(font, title, x, 14, 0xFFE3C07B, false);
            graphics.fill(x, 31, 404, 32, 0xFF80623D);
            int y = 44;
            if (card.orientation() != Orientation.NONE) {
                String position = card.orientation() == Orientation.UPRIGHT ? "upright" : "reversed";
                RitualArt.blit(graphics, "gui/" + position, x, y - 2, 12, 12, 16, 16);
                y = paragraph(graphics, Component.translatable("orientation.civitatum_starclock." + position),
                        x + 18, y, 201, 0xFFE8DCC0) + 16;
            }
            String meaning = card.sign().translationKey() + "." + (card.orientation() == Orientation.NONE
                    ? "any" : card.orientation() == Orientation.UPRIGHT ? "upright" : "reversed");
            y = paragraph(graphics, Component.translatable(meaning), x, y, 219, 0xFFE8DCC0) + 18;
            if (!card.category().isEmpty()) {
                y = paragraph(graphics, Component.translatable("ui.civitatum_starclock.event_type",
                        Component.translatable("event_type.civitatum_starclock." + card.category())),
                        x, y, 219, 0xFFE3C07B) + 10;
            }
            if (!card.target().isEmpty()) {
                paragraph(graphics, Component.translatable("ui.civitatum_starclock.target",
                        Component.translatable("target.civitatum_starclock." + card.target())),
                        x, y, 219, 0xFFE8DCC0);
            }
        }
        pose.popPose();
        // Виджет остаётся зарегистрирован в Screen для ввода и навигации.
        // Рисуем его после карты без позднего прохода фона базового экрана.
        close.render(graphics, mouseX, mouseY, partialTick);
    }

    private int paragraph(GuiGraphics graphics, Component text, int x, int y, int availableWidth, int color) {
        for (var line : font.split(text, availableWidth)) {
            graphics.drawString(font, line, x, y, color, false);
            y += 12;
        }
        return y;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
