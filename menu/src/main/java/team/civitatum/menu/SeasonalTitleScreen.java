package team.civitatum.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import java.time.LocalDate;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * Title screen laid out on a virtual 960x540 canvas with a seasonal parallax background.
 * F5 reloads the config, F8 skips to the next track.
 */
public final class SeasonalTitleScreen extends TitleScreen {
    private static final ResourceLocation LOGO = CivitatumMenu.resource("textures/label.png");
    /** The same logo with its subtitle redrawn in English; shown for every language but {@link #RUSSIAN}. */
    private static final ResourceLocation LOGO_EN = CivitatumMenu.resource("textures/label_en.png");
    private static final String RUSSIAN = "ru_ru";
    /** Virtual canvas every coordinate of the layout is given on; the screen scales it to fit. */
    private static final float CANVAS_WIDTH = 960.0F;
    private static final float CANVAS_HEIGHT = 540.0F;
    /** Pixel sizes of the bundled textures. */
    private static final int BACKGROUND_WIDTH = 1672;
    private static final int BACKGROUND_HEIGHT = 941;
    private static final int LOGO_WIDTH = 1064;
    private static final int LOGO_HEIGHT = 261;
    /** The season is rechecked once a minute (1200 ticks), so a menu left open over midnight follows the calendar. */
    private static final int CALENDAR_CHECK_TICKS = 1200;
    /** Vanilla's own copyright line, already translated by the game. */
    private static final Component COPYRIGHT = Component.translatable("title.credits");
    /** Side of a top-right button; it and the spacing below are on the virtual canvas like the rest of the menu. */
    private static final int TOP_RIGHT_SIZE = 20;
    private static final int TOP_RIGHT_MARGIN = 3;
    private static final int TOP_RIGHT_GAP = 3;
    private static ResourceLocation loadedBackground;
    private static Season loadedSeason;
    private float scale;
    private float virtualWidth;
    private float virtualHeight;
    /** Screen x of the left edge of the top-right button row; the music label ends before it. */
    private int topRightLeft;
    private double panX;
    private double panY;
    private long lastFrame;
    private int calendarTicks;
    private Season season;

    public SeasonalTitleScreen() {
        super(false);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.scale = Math.min((float)this.width / CANVAS_WIDTH, (float)this.height / CANVAS_HEIGHT);
        this.virtualWidth = (float)this.width / this.scale;
        this.virtualHeight = (float)this.height / this.scale;
        MenuConfig c = MenuConfig.current;
        int left = Math.min(c.menuLeft, (int)this.virtualWidth - 220);
        int y = (int)this.virtualHeight / 2 + c.menuVerticalOffset;
        this.add(left, y, 200, "singleplayer", () -> this.minecraft.setScreen(new SelectWorldScreen(this)));
        TextMenuButton multiplayer = this.add(left, y + 24, 200, "multiplayer", () -> {
            if (this.minecraft.allowsMultiplayer()) {
                this.minecraft.setScreen(this.minecraft.options.skipMultiplayerWarning
                    ? new JoinMultiplayerScreen(this) : new SafetyScreen(this));
            }
        });
        multiplayer.active = this.minecraft.allowsMultiplayer();
        if (!multiplayer.active) {
            multiplayer.setTooltip(Tooltip.create(this.multiplayerDisabledReason()));
        }

        this.add(left - 1, y + 46, 200, "wardrobe", () -> Wardrobe.open(this));
        this.add(left, y + 68, 200, "mods", () -> ModsMenu.open(this));
        this.add(left, y + 90, 200, "options",
            () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options)));
        this.add(left, y + 116, 200, "quit", this.minecraft::stop);
        this.topRightLeft = this.width - Math.round((float)TOP_RIGHT_MARGIN * this.scale);
        if (c.showLanguageAndAccessibility) {
            this.addTopRightIcon("accessibility", Component.translatable("options.accessibility"),
                () -> this.minecraft.setScreen(new AccessibilityOptionsScreen(this, this.minecraft.options)));
            this.addTopRightIcon("language", Component.translatable("options.language"),
                () -> this.minecraft.setScreen(
                    new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager())));
        }

        this.season = Season.resolve(c.season, LocalDate.now(), c.southernHemisphere);
        this.ensureBackground();
        MenuMusic.enter();
        this.lastFrame = Util.getMillis();
    }

    /** {@code key}: the label under {@code menu.civitatum_menu.<key>} in the lang files. */
    private TextMenuButton add(int x, int y, int w, String key, Runnable action) {
        return this.addRenderableWidget(
            new TextMenuButton(
                Math.round((float)x * this.scale),
                Math.round((float)y * this.scale),
                Math.round((float)w * this.scale),
                Math.max(1, Math.round(20.0F * this.scale)),
                Component.translatable("menu.civitatum_menu." + key),
                this.scale,
                action
            )
        );
    }

    /** {@code icon}: the texture {@code textures/icon/<icon>.png}. */
    private void addTopRightIcon(String icon, Component label, Runnable action) {
        this.addRenderableWidget(this.placeTopRight(new IconMenuButton(0, 0, this.topRightSize(), label,
            CivitatumMenu.resource("textures/icon/" + icon + ".png"), this.scale, action)));
    }

    /**
     * A text button in the top-right row, left of the buttons already there; the caller adds it to the screen.
     * Stands in for the buttons other mods add to this screen, see {@link ForeignButtons}.
     */
    TextMenuButton topRightText(Component label, Runnable action) {
        int size = this.topRightSize();
        return this.placeTopRight(new TextMenuButton(0, 0, size, size, label, this.scale, action));
    }

    private int topRightSize() {
        return Math.max(1, Math.round((float)TOP_RIGHT_SIZE * this.scale));
    }

    private <T extends AbstractWidget> T placeTopRight(T widget) {
        this.topRightLeft -= widget.getWidth();
        widget.setPosition(this.topRightLeft, Math.round((float)TOP_RIGHT_MARGIN * this.scale));
        this.topRightLeft -= Math.round((float)TOP_RIGHT_GAP * this.scale);
        return widget;
    }

    private Component multiplayerDisabledReason() {
        String key = "title.multiplayer.disabled";
        if (this.minecraft.isNameBanned()) {
            key = key + ".banned.name";
        } else if (this.minecraft.multiplayerBan() != null) {
            key = key + (this.minecraft.multiplayerBan().expires() == null ? ".banned.permanent" : ".banned.temporary");
        }

        return Component.translatable(key);
    }

    private void ensureBackground() {
        if (loadedSeason != this.season || loadedBackground == null) {
            releaseBackground(this.minecraft);
            loadedSeason = this.season;
            loadedBackground = CivitatumMenu.resource("textures/" + this.season.fileName());
            this.minecraft.getTextureManager().getTexture(loadedBackground).setFilter(true, false);
        }
    }

    static void releaseBackground(Minecraft minecraft) {
        if (loadedBackground != null) {
            minecraft.getTextureManager().release(loadedBackground);
        }

        loadedBackground = null;
        loadedSeason = null;
    }

    @Override
    public void tick() {
        if (++this.calendarTicks >= CALENDAR_CHECK_TICKS) {
            this.calendarTicks = 0;
            MenuConfig c = MenuConfig.current;
            this.season = Season.resolve(c.season, LocalDate.now(), c.southernHemisphere);
            this.ensureBackground();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.ensureBackground();
        long now = Util.getMillis();
        double dt = Math.min(0.1, Math.max(0.0, (double)(now - this.lastFrame) / 1000.0));
        this.lastFrame = now;
        double blend = 1.0 - Math.exp(-MenuConfig.current.parallaxResponse * dt);
        double targetX = 2.0 * (double)mouseX / (double)Math.max(1, this.width) - 1.0;
        double targetY = 2.0 * (double)mouseY / (double)Math.max(1, this.height) - 1.0;
        this.panX = this.panX + (targetX - this.panX) * blend;
        this.panY = this.panY + (targetY - this.panY) * blend;
        double intensity = MenuConfig.current.parallax ? MenuConfig.current.parallaxIntensity : 0.0;
        BackgroundGeometry b = BackgroundGeometry.fit(
            this.width, this.height, BACKGROUND_WIDTH, BACKGROUND_HEIGHT, intensity, this.panX, this.panY);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(loadedBackground, b.x(), b.y(), b.width(), b.height(), 0.0F, 0.0F,
            BACKGROUND_WIDTH, BACKGROUND_HEIGHT, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        MenuConfig c = MenuConfig.current;
        graphics.pose().pushPose();
        graphics.pose().scale(this.scale, this.scale, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int logoX = Math.round((this.virtualWidth - (float)c.logoWidth) / 2.0F);
        ResourceLocation logo = RUSSIAN.equals(this.minecraft.getLanguageManager().getSelected()) ? LOGO : LOGO_EN;
        graphics.blit(logo, logoX, c.logoTop, c.logoWidth, c.logoHeight,
            0.0F, 0.0F, LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);
        RenderSystem.disableBlend();
        String music = this.font.plainSubstrByWidth(MenuMusic.label(),
            Math.max(100, (int)((float)this.topRightLeft / this.scale) - 8));
        graphics.drawString(this.font, music, 4, 6, -1, true);
        String footer = this.font.plainSubstrByWidth(Language.getInstance().getOrDefault(c.footer),
            (int)this.virtualWidth / 2 - 12);
        graphics.drawString(this.font, footer, 4, (int)this.virtualHeight - 14, -1, true);
        graphics.drawString(this.font, COPYRIGHT, (int)this.virtualWidth - this.font.width(COPYRIGHT) - 4,
            (int)this.virtualHeight - 9, -1, true);
        graphics.pose().popPose();

        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_F5) {
            MenuConfig.load();
            releaseBackground(this.minecraft);
            if (!MenuConfig.current.enabled) {
                this.minecraft.setScreen(new TitleScreen());
            } else {
                this.rebuildWidgets();
            }

            return true;
        } else if (key == GLFW.GLFW_KEY_F8) {
            MenuMusic.next();
            return true;
        } else {
            return super.keyPressed(key, scanCode, modifiers);
        }
    }
}
