package team.civitatum.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import java.time.LocalDate;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.ModListScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Title screen laid out on a virtual 960x540 canvas with a seasonal parallax background.
 * F5 reloads the config, F8 skips to the next track.
 */
public final class SeasonalTitleScreen extends TitleScreen {
    private static final ResourceLocation LOGO = CivitatumMenu.resource("textures/label.png");
    private static ResourceLocation loadedBackground;
    private static Season loadedSeason;
    private float scale;
    private float virtualWidth;
    private float virtualHeight;
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
        this.scale = Math.min((float)this.width / 960.0F, (float)this.height / 540.0F);
        this.virtualWidth = (float)this.width / this.scale;
        this.virtualHeight = (float)this.height / this.scale;
        MenuConfig c = MenuConfig.current;
        int left = Math.min(c.menuLeft, (int)this.virtualWidth - 220);
        int y = (int)this.virtualHeight / 2 + c.menuVerticalOffset;
        this.add(left, y, 200, "Одиночное приключение", () -> this.minecraft.setScreen(new SelectWorldScreen(this)));
        TextMenuButton multiplayer = this.add(left, y + 24, 200, "Отправиться с группой", () -> {
            if (this.minecraft.allowsMultiplayer()) {
                this.minecraft.setScreen((Screen)(this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this)));
            }
        });
        multiplayer.active = this.minecraft.allowsMultiplayer();
        if (!multiplayer.active) {
            multiplayer.setTooltip(Tooltip.create(this.multiplayerDisabledReason()));
        }

        this.add(left - 1, y + 46, 200, "Гардероб", () -> Wardrobe.open(this));
        this.add(left, y + 68, 200, "Моды", () -> this.minecraft.setScreen(new ModListScreen(this)));
        this.add(left, y + 94, 98, "Настройки…", () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options)));
        this.add(left + 101, y + 94, 98, "Выйти из игры", this.minecraft::stop);
        if (c.showLanguageAndAccessibility) {
            this.add(
                (int)this.virtualWidth - 46,
                3,
                20,
                "Яз",
                () -> this.minecraft.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager()))
            );
            this.add((int)this.virtualWidth - 23, 3, 20, "A", () -> this.minecraft.setScreen(new AccessibilityOptionsScreen(this, this.minecraft.options)));
        }

        this.season = Season.resolve(c.season, LocalDate.now(), c.southernHemisphere);
        this.ensureBackground();
        MenuMusic.enter();
        this.lastFrame = Util.getMillis();
    }

    private TextMenuButton add(int x, int y, int w, String text, Runnable action) {
        return this.addRenderableWidget(
            new TextMenuButton(
                Math.round((float)x * this.scale),
                Math.round((float)y * this.scale),
                Math.round((float)w * this.scale),
                Math.max(1, Math.round(20.0F * this.scale)),
                Component.literal(text),
                this.scale,
                action
            )
        );
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
        if (++this.calendarTicks >= 1200) {
            this.calendarTicks = 0;
            this.season = Season.resolve(MenuConfig.current.season, LocalDate.now(), MenuConfig.current.southernHemisphere);
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
        BackgroundGeometry b = BackgroundGeometry.fit(this.width, this.height, 1672, 941, intensity, this.panX, this.panY);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(loadedBackground, b.x(), b.y(), b.width(), b.height(), 0.0F, 0.0F, 1672, 941, 1672, 941);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        MenuConfig c = MenuConfig.current;
        graphics.pose().pushPose();
        graphics.pose().scale(this.scale, this.scale, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(LOGO, Math.round((this.virtualWidth - (float)c.logoWidth) / 2.0F), c.logoTop, c.logoWidth, c.logoHeight, 0.0F, 0.0F, 1064, 261, 1064, 261);
        RenderSystem.disableBlend();
        String music = this.font.plainSubstrByWidth(MenuMusic.label(), Math.max(100, (int)this.virtualWidth - 105));
        graphics.drawString(this.font, music, 4, 6, -1, true);
        String footer = this.font.plainSubstrByWidth(c.footer, (int)this.virtualWidth / 2 - 12);
        graphics.drawString(this.font, footer, 4, (int)this.virtualHeight - 14, -1, true);
        String copyright = "© Mojang AB. Не распространять!";
        graphics.drawString(this.font, copyright, (int)this.virtualWidth - this.font.width(copyright) - 4, (int)this.virtualHeight - 9, -1, true);
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
