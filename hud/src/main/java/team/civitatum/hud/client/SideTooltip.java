package team.civitatum.hud.client;

import java.util.List;
import java.util.ArrayList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** A stable reading position next to a window, bounded by the viewport. */
public final class SideTooltip {
    private static final int LINE = 12;
    /** Above the windows and their items, which are drawn up to z = 210 + 40 per window. */
    private static final int Z = 800;

    public static void render(GuiGraphics g, Font font, InventoryPanel panel, List<Component> text) {
        if (text.isEmpty()) return;
        int sw = g.guiWidth(), sh = g.guiHeight(), w = Math.clamp(sw / 3, 110, 230);
        int left = panel.x - 8 - w, right = panel.x + panel.width + 8;
        int x = left >= 4 ? left : right + w <= sw - 4 ? right : Math.max(4, Math.min(sw - w - 4, left));
        var lines = new ArrayList<FormattedCharSequence>();
        for (var line : text) lines.addAll(font.split(line, w - 16));
        int h = Math.min(sh - 16, lines.size() * LINE + 16), y = Math.max(8, Math.min(panel.y + 20, sh - h - 8));
        g.pose().pushPose();
        g.pose().translate(0, 0, Z);
        g.fill(x, y, x + w, y + h, 0xF21C1812);
        MorrowindSkin.frame(g, x, y, w, h, true);
        g.enableScissor(x + 6, y + 6, x + w - 6, y + h - 6);
        for (int i = 0; i < lines.size() && i * LINE < h - 14; i++) {
            g.drawString(font, lines.get(i), x + 8, y + 8 + i * LINE, 0xE9E3D4, false);
        }
        g.disableScissor();
        g.flush();
        g.pose().popPose();
    }
}
