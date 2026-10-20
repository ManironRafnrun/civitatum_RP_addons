package team.civitatum.starclock.client;

import java.util.UUID;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// Время берётся из серверного пакета; экран не выбирает результат и не завершает обряд.
public final class ConstellationScreen extends Screen {
    private final UUID session;

    public ConstellationScreen(UUID session) {
        super(Component.translatable("ui.civitatum_starclock.drawing"));
        this.session = session;
    }

    @Override
    public void tick() {
        var state = RitualClientState.current();
        if (state == null || !state.drawing() || !session.equals(state.session())) minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var state = RitualClientState.current();
        if (state == null || !state.drawing() || !session.equals(state.session())) return;
        RitualArt.background(graphics, width, height);
        float progress = RitualClientState.phaseTicks(partialTick) / 40.0F;
        int size = Math.max(32, Math.min(width - 48, height - 64));
        int left = (width - size) / 2;
        int top = (height - size) / 2;
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFE3C07B);
        int edge = top + (int) (size * progress);
        graphics.enableScissor(left, top, left + size, edge);
        RitualArt.blit(graphics, "constellation/" + state.figure().id(), left, top, size, size, 512, 512);
        graphics.disableScissor();
        if (progress > 0 && progress < 1) {
            graphics.fill(left + size / 8, edge, left + size * 7 / 8, edge + 1, 0x448E6FB3);
            for (int index = 0; index < 7; index++) {
                float x = left + size * (index + 1) / 8.0F;
                float pulse = (float) (0.45 + 0.35 * Math.sin(progress * 23 + index * 2));
                RenderSystem.setShaderColor(1, 1, 1, pulse);
                RitualArt.blit(graphics, "particle/star", x - 3, edge - 3, 6, 6, 8, 8);
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        if (progress >= 0.8F) {
            graphics.drawCenteredString(font, Component.translatable(state.figure().translationKey()),
                    width / 2, height - 24, 0xFFE8DCC0);
        }
        graphics.fill(width / 2 - 50, height - 9, width / 2 + 50, height - 7, 0xFF2A2118);
        graphics.fill(width / 2 - 50, height - 9, width / 2 - 50 + (int) (100 * progress),
                height - 7, 0xFFE3C07B);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
