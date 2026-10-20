package team.civitatum.starclock.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class RitualArt {
    public static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("starclock", "textures/" + path + ".png");
    }

    public static void blit(GuiGraphics graphics, String path, float x, float y,
                            float width, float height, int textureWidth, int textureHeight) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(width / textureWidth, height / textureHeight, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(texture(path), 0, 0, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
        pose.popPose();
    }

    public static void background(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xF50B1020);
        RenderSystem.setShaderColor(1, 1, 1, 0.5F);
        blit(graphics, "gui/starfield", 0, 0, width, height, 512, 512);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private RitualArt() {}
}
