package team.civitatum.hud.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PowersView {
    /** Effect icons: an 18 px icon in a 20 px frame, on a 24 px grid. */
    private static final int CELL = 24;
    /** Height of one ability row. */
    private static final int ROW = 24;
    /** The ability list keeps at least this much of the window below the effect icons. */
    private static final int ABILITY_SPACE = 80;
    /** Race benefits and limitations are shown as two extra icons before the effects. */
    private static final int RACE_ICONS = 2;
    private static final int GOLD = 0xD3B478, TEXT = 0xE9E3D4, MUTED = 0xABA799, SELECTED = 0xB5D78B;
    private static final ItemStack BENEFITS_ICON = new ItemStack(Items.OAK_SAPLING);
    private static final ItemStack LIMITATIONS_ICON = new ItemStack(Items.FLINT);
    private List<Component> hover = List.of();
    private int scroll;
    private int effectScroll;
    private final WheelSteps wheel = new WheelSteps();

    private static int columns(InventoryPanel p) {
        return Math.max(1, (p.width - 20) / CELL);
    }

    private static int iconCount(Player player, RacesBridge.Details race) {
        return player.getActiveEffects().size() + (race == null ? 0 : RACE_ICONS);
    }

    /** Bottom of the effect icons, limited so the ability list keeps its space. */
    private static int effectsBottom(InventoryPanel p, Player player, RacesBridge.Details race) {
        int columns = columns(p);
        int bottom = p.y + 46 + Math.max(1, (iconCount(player, race) + columns - 1) / columns) * CELL;
        return Math.min(bottom, p.y + p.height - ABILITY_SPACE);
    }

    public void render(GuiGraphics g, Font font, InventoryPanel p, Player player, int mx, int my) {
        hover = List.of();
        var race = RacesBridge.read(player);
        int columns = columns(p), i = 0, bottom = effectsBottom(p, player, race);
        g.drawString(font, CivitatumInventoryScreen.tr("effects"), p.x + 9, p.y + 26, GOLD, false);
        int count = iconCount(player, race), visibleRows = Math.max(1, (bottom - p.y - 42) / CELL);
        effectScroll = Math.clamp(effectScroll, 0, Math.max(0, (count + columns - 1) / columns - visibleRows));
        g.enableScissor(p.x + 6, p.y + 40, p.x + p.width - 6, bottom);
        if (race != null) {
            for (int n = 0; n < RACE_ICONS; n++) {
                int x = p.x + 10 + i % columns * CELL, y = p.y + 42 + (i / columns - effectScroll) * CELL;
                i++;
                if (y < p.y + 40 || y + 20 > bottom) continue;
                MorrowindSkin.frame(g, x - 1, y - 1, 20, 20, false);
                g.renderItem(n == 0 ? BENEFITS_ICON : LIMITATIONS_ICON, x + 1, y + 1);
                if (iconHovered(x, y, p, bottom, mx, my)) {
                    hover = List.of(Component.literal(race.race()).withColor(GOLD),
                        CivitatumInventoryScreen.tr(n == 0 ? "benefits" : "limitations"),
                        Component.literal(n == 0 ? race.positive() : race.negative()));
                }
            }
        }
        for (var effect : player.getActiveEffects()) {
            int x = p.x + 10 + i % columns * CELL, y = p.y + 42 + (i / columns - effectScroll) * CELL;
            i++;
            if (y < p.y + 40 || y + 20 > bottom) continue;
            MorrowindSkin.frame(g, x - 1, y - 1, 20, 20, false);
            g.blit(x + 1, y + 1, 0, 18, 18, Minecraft.getInstance().getMobEffectTextures().get(effect.getEffect()));
            if (iconHovered(x, y, p, bottom, mx, my)) {
                var name = effect.getEffect().value().getDisplayName().copy().append(" " + (effect.getAmplifier() + 1));
                hover = List.of(name,
                    MobEffectUtil.formatDuration(effect, 1, player.level().tickRateManager().tickrate()));
            }
        }
        g.disableScissor();
        if (i == 0) {
            String none = font.plainSubstrByWidth(CivitatumInventoryScreen.tr("no_effects").getString(), p.width - 20);
            g.drawString(font, none, p.x + 10, p.y + 47, MUTED, false);
        }
        g.fill(p.x + 6, bottom + 4, p.x + p.width - 6, bottom + 5, 0xFF8C754F);
        g.drawString(font, CivitatumInventoryScreen.tr("abilities"), p.x + 9, bottom + 12, GOLD, false);
        int top = bottom + 26, end = p.y + p.height - 26;
        var list = AbilitySelection.available(player);
        var selected = AbilitySelection.selected(player);
        scroll = Math.clamp(scroll, 0, Math.max(0, list.size() * ROW - (end - top)));
        g.enableScissor(p.x + 6, top, p.x + p.width - 6, end);
        for (int n = 0; n < list.size(); n++) {
            var a = list.get(n);
            int y = top + n * ROW - scroll;
            boolean active = selected != null && selected.id().equals(a.id());
            if (active) g.fill(p.x + 7, y, p.x + p.width - 7, y + 22, 0x663D5032);
            g.renderItem(a.icon(), p.x + 10, y + 3);
            String name = font.plainSubstrByWidth(a.name().getString(), p.width - 43);
            g.drawString(font, name, p.x + 31, y + 7, active ? SELECTED : TEXT, false);
            if (mx >= p.x + 7 && mx < p.x + p.width - 7 && my >= Math.max(y, top) && my < Math.min(y + 22, end)) {
                hover = a.description();
            }
        }
        g.disableScissor();
        if (list.isEmpty()) {
            var hint = CivitatumInventoryScreen.tr("no_race_description");
            g.drawWordWrap(font, hint, p.x + 10, top, p.width - 20, MUTED);
        }
    }

    private static boolean iconHovered(int x, int y, InventoryPanel p, int bottom, int mx, int my) {
        return mx >= x && mx < x + 20 && my >= Math.max(y, p.y + 40) && my < y + 20 && my < bottom;
    }

    public boolean click(InventoryPanel p, Player player, double mx, double my) {
        int top = effectsBottom(p, player, RacesBridge.read(player)) + 26;
        if (mx < p.x + 7 || mx >= p.x + p.width - 7 || my < top || my >= p.y + p.height - 26) return false;
        var list = AbilitySelection.available(player);
        int i = ((int) my - top + scroll) / ROW;
        if (i >= 0 && i < list.size()) {
            AbilitySelection.select(list.get(i).id());
            return true;
        }
        return false;
    }

    public void scroll(InventoryPanel p, Player player, double my, double delta) {
        if (my < effectsBottom(p, player, RacesBridge.read(player))) {
            effectScroll = Math.max(0, effectScroll - wheel.rows(delta));
        }
        else scroll = Math.max(0, scroll - (int) (delta * ROW));
    }

    public void tooltip(GuiGraphics g, Font font, InventoryPanel p) {
        SideTooltip.render(g, font, p, hover);
    }
}
