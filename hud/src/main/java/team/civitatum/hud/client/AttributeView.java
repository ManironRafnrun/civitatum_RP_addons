package team.civitatum.hud.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.effect.MobEffectInstance;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * NeoForge attribute formatting and Apothic's optional modifier-source registry.
 *
 * <p>The list is drawn every frame, so Apothic's classes and members are looked up once; a failed lookup is kept
 * as {@code null} and means "no Apothic data", as before.
 */
public final class AttributeView {
    private static final TagKey<Attribute> DYNAMIC =
        TagKey.create(Registries.ATTRIBUTE, ResourceLocation.fromNamespaceAndPath("apothic_attributes",
            "dynamic_base"));
    private static final String APOTHIC = "dev.shadowsoffire.apothic_attributes.";
    private static final int ROW = 25;
    private static final int TEXT = 0xE9E3D4, GOLD = 0xD3B478;
    private static List<Component> hover = List.of();
    private static Field hiddenAttributes;
    private static Method sourceTypes, extract, sourceData;
    private static boolean apothicLooked;

    private record Entry(String name, AttributeInstance instance) {}

    public static List<AttributeInstance> entries(Player player, boolean modified) {
        Set<?> hidden = hiddenAttributes();
        var entries = new ArrayList<Entry>();
        BuiltInRegistries.ATTRIBUTE.holders().map(player::getAttribute).filter(Objects::nonNull)
            .filter(a -> !hidden.contains(BuiltInRegistries.ATTRIBUTE.getKey(a.getAttribute().value())))
            .filter(a -> !modified || !a.getModifiers().isEmpty()
                || Double.compare(a.getBaseValue(), a.getValue()) != 0)
            .forEach(a -> entries.add(new Entry(name(a), a)));
        // The translated name is the sort key; it is built once per row instead of once per comparison.
        entries.sort(Comparator.comparing(Entry::name));
        return entries.stream().map(Entry::instance).toList();
    }

    private static String name(AttributeInstance a) {
        return Component.translatable(a.getAttribute().value().getDescriptionId()).getString();
    }

    private static Set<?> hiddenAttributes() {
        if (!CompatBridge.loaded("apothic_attributes")) return Set.of();
        lookUpApothic();
        try {
            return hiddenAttributes == null ? Set.of() : (Set<?>) hiddenAttributes.get(null);
        } catch (ReflectiveOperationException ignored) {
            return Set.of();
        }
    }

    private static void lookUpApothic() {
        if (apothicLooked) return;
        apothicLooked = true;
        try {
            hiddenAttributes = Class.forName(APOTHIC + "ALConfig").getField("hiddenAttributes");
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Class<?> type = Class.forName(APOTHIC + "client.ModifierSourceType");
            sourceTypes = type.getMethod("getTypes");
            extract = type.getMethod("extract", LivingEntity.class, BiConsumer.class);
            sourceData = Class.forName(APOTHIC + "client.ModifierSource").getMethod("getData");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            sourceTypes = null;
        }
    }

    public static int render(GuiGraphics g, Font font, Player player, InventoryPanel panel, int scroll,
                             boolean modified, int mx, int my) {
        var entries = entries(player, modified);
        int x = panel.x + 9, y = panel.y + 51, w = panel.width - 18, bottom = panel.y + panel.height - 32;
        int max = Math.max(0, entries.size() * ROW - (bottom - y));
        scroll = Math.clamp(scroll, 0, max);
        hover = List.of();
        g.enableScissor(x, y, x + w, bottom);
        for (int i = 0; i < entries.size(); i++) {
            var a = entries.get(i);
            int row = y + i * ROW - scroll;
            if (row + ROW - 1 < y || row >= bottom) continue;
            boolean hot = mx >= x && mx < x + w && my >= Math.max(y, row) && my < Math.min(bottom, row + ROW - 1);
            g.fill(x - 2, row - 2, x + w, row + 22, hot ? 0x664F4431 : i % 2 == 0 ? 0x242C261C : 0x1217130E);
            g.drawString(font, font.plainSubstrByWidth(name(a), w - 5), x + 3, row, TEXT, false);
            Component value = a.getAttribute().is(DYNAMIC) ? CivitatumInventoryScreen.tr("attributes.dynamic")
                : a.getAttribute().value().toValueComponent(null, a.getValue(), TooltipFlag.Default.NORMAL);
            g.drawString(font, value, x + w - font.width(value) - 4, row + 11, GOLD, false);
            g.fill(x, row + 23, x + w, row + 24, 0x354F4330);
            if (hot) hover = details(player, a);
        }
        g.disableScissor();
        return scroll;
    }

    public static void tooltip(GuiGraphics g, Font font, InventoryPanel panel) {
        SideTooltip.render(g, font, panel, hover);
    }

    private static List<Component> details(Player player, AttributeInstance a) {
        var attr = a.getAttribute().value();
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable(attr.getDescriptionId()).withColor(GOLD));
        String description = attr.getDescriptionId() + ".desc";
        if (I18n.exists(description)) lines.add(Component.translatable(description));
        if (a.getAttribute().is(DYNAMIC)) {
            lines.add(CivitatumInventoryScreen.tr("attributes.dynamic_hint"));
        } else {
            lines.add(CivitatumInventoryScreen.tr("attributes.current").copy()
                .append(attr.toValueComponent(null, a.getValue(), TooltipFlag.Default.NORMAL)));
            lines.add(CivitatumInventoryScreen.tr("attributes.base").copy()
                .append(attr.toValueComponent(null, a.getBaseValue(), TooltipFlag.Default.NORMAL)));
        }
        Map<ResourceLocation, Object> sources = sources(player);
        for (var modifier : a.getModifiers()) {
            var line = attr.toComponent(modifier, TooltipFlag.Default.NORMAL);
            Object source = sources.get(modifier.id());
            if (source != null) try {
                Object data = sourceData.invoke(source);
                if (data instanceof ItemStack item) line.append(" · ").append(item.getHoverName());
                else if (data instanceof MobEffectInstance effect) {
                    line.append(" · ").append(effect.getEffect().value().getDisplayName());
                }
            } catch (ReflectiveOperationException ignored) {
            }
            lines.add(line);
        }
        return lines;
    }

    /** Apothic's modifier sources: the item or effect each modifier comes from. Empty without Apothic. */
    private static Map<ResourceLocation, Object> sources(Player player) {
        Map<ResourceLocation, Object> sources = new HashMap<>();
        lookUpApothic();
        if (sourceTypes == null) return sources;
        try {
            BiConsumer<AttributeModifier, Object> collect = (m, data) -> sources.put(m.id(), data);
            for (Object source : (Collection<?>) sourceTypes.invoke(null)) extract.invoke(source, player, collect);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return sources;
    }
}
