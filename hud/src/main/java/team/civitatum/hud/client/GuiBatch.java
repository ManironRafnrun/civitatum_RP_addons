package team.civitatum.hud.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Outside a managed batch every {@link GuiGraphics#fill} flushes the buffer on its own: a new buffer builder and a
 * draw call per rectangle. The frames and slots of this mod are runs of plain fills, so each run is drawn with one
 * flush. Fills are drawn in order and without the depth test either way, so the picture does not change.
 *
 * <p>Only for runs of fills: a {@code blit} inside would be drawn at once, ahead of the fills batched before it.
 * Not for use inside another managed batch: it ends that batch.
 */
final class GuiBatch {
    @SuppressWarnings("deprecation") // drawManaged is the only batching GuiGraphics offers in 1.21.1
    static void fills(GuiGraphics g, Runnable fills) {
        g.drawManaged(fills);
    }

    private GuiBatch() {}
}
