package team.civitatum.hud.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;

/**
 * Replaces the vanilla effects layer: the icons are drawn as in {@code Gui.renderEffects} (frame, blinking before the
 * end, NeoForge's custom icon renderers) but at 75%, in rows of up to 7 next to Xaero's minimap, or in the top right
 * corner of the screen without one. Beneficial effects come first; harmful ones start on the row below them.
 * The effects size setting scales the whole block about its corner at the minimap or the screen corner.
 */
final class StatusEffectsLayer implements LayeredDraw.Layer {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("hud/effect_background");
    private static final ResourceLocation AMBIENT_BACKGROUND =
        ResourceLocation.withDefaultNamespace("hud/effect_background_ambient");
    /** Vanilla's frame and icon sizes; the pose scales them to 75%. */
    private static final int VANILLA_FRAME = 24;
    private static final int VANILLA_ICON = 18;
    private static final int VANILLA_ICON_OFFSET = 3;
    /** Vanilla starts blinking an effect 200 ticks (10 s) before it ends. */
    private static final int BLINK_TICKS = 200;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.getActiveEffects().isEmpty()
            || minecraft.screen instanceof EffectRenderingInventoryScreen<?> screen && screen.canSeeEffects()) {
            return;
        }

        List<MobEffectInstance> beneficial = new ArrayList<>();
        List<MobEffectInstance> harmful = new ArrayList<>();
        minecraft.player.getActiveEffects().stream()
            .filter(effect -> IClientMobEffectExtensions.of(effect).isVisibleInGui(effect) && effect.showIcon())
            .sorted(Comparator.reverseOrder())
            .forEach(effect -> (effect.getEffect().value().isBeneficial() ? beneficial : harmful).add(effect));
        int widest = Math.max(beneficial.size(), harmful.size());
        float scale = (float) InventoryPreferences.get().effectsScale;
        boolean leftward;
        int edge;
        int top;
        if (MinimapBounds.isDrawn()) {
            leftward = EffectsPlacement.fitsLeftOf(MinimapBounds.x(), widest, scale);
            edge = leftward ? MinimapBounds.x() - EffectsPlacement.GAP
                : MinimapBounds.x() + MinimapBounds.size() + EffectsPlacement.GAP;
            top = MinimapBounds.y();
        } else {
            leftward = true;
            edge = graphics.guiWidth() - EffectsPlacement.SCREEN_MARGIN;
            top = EffectsPlacement.SCREEN_MARGIN + (minecraft.isDemo() ? EffectsPlacement.DEMO_SHIFT : 0);
        }

        // The block is laid out from its corner at the origin and scaled about that corner, next to the minimap.
        graphics.pose().pushPose();
        graphics.pose().translate(edge, top, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        this.renderKind(graphics, minecraft, beneficial, leftward, 0, 0, 0);
        this.renderKind(graphics, minecraft, harmful, leftward, 0, 0, EffectsPlacement.rows(beneficial.size()));
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }

    private void renderKind(GuiGraphics graphics, Minecraft minecraft, List<MobEffectInstance> effects,
                            boolean leftward, int edge, int top, int firstRow) {
        for (int i = 0; i < effects.size(); i++) {
            graphics.pose().pushPose();
            graphics.pose().translate(EffectsPlacement.x(leftward, edge, i), EffectsPlacement.y(top, firstRow, i), 0);
            graphics.pose().scale(EffectsPlacement.SCALE, EffectsPlacement.SCALE, 1.0F);
            renderIcon(graphics, minecraft, effects.get(i));
            graphics.pose().popPose();
        }
    }

    /** {@code Gui.renderEffects} for one effect, at the origin. */
    private static void renderIcon(GuiGraphics graphics, Minecraft minecraft, MobEffectInstance effect) {
        float alpha = 1.0F;
        if (effect.isAmbient()) {
            graphics.blitSprite(AMBIENT_BACKGROUND, 0, 0, VANILLA_FRAME, VANILLA_FRAME);
        } else {
            graphics.blitSprite(BACKGROUND, 0, 0, VANILLA_FRAME, VANILLA_FRAME);
            if (effect.endsWithin(BLINK_TICKS)) {
                int duration = effect.getDuration();
                int fade = 10 - duration / 20;
                alpha = Mth.clamp((float) duration / 10.0F / 5.0F * 0.5F, 0.0F, 0.5F)
                    + Mth.cos((float) duration * (float) Math.PI / 5.0F)
                    * Mth.clamp((float) fade / 10.0F * 0.25F, 0.0F, 0.25F);
            }
        }

        if (IClientMobEffectExtensions.of(effect).renderGuiIcon(effect, minecraft.gui, graphics, 0, 0, 0, alpha)) {
            return;
        }

        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(VANILLA_ICON_OFFSET, VANILLA_ICON_OFFSET, 0, VANILLA_ICON, VANILLA_ICON,
            minecraft.getMobEffectTextures().get(effect.getEffect()));
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
