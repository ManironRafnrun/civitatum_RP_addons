package team.civitatum.hud.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * ParCool's stamina icons in this HUD's colours ({@link EnergyRecolor}). Each icon is recoloured from ParCool's own
 * texture, as the active resource packs provide it, the first time it is drawn; a resource reload drops them.
 * ParCool's art is not copied into this mod.
 */
public final class ParCoolEnergyIcons {
    private static final Map<ResourceLocation, Icon> ICONS = new HashMap<>();
    private record Icon(ResourceLocation texture, int width, int height) {}

    private ParCoolEnergyIcons() {
    }

    /**
     * Draws in place of {@code GuiGraphics.blit(x, y, z, width, height, sprite)} in ParCool's stamina HUD; above the
     * bars the row is mirrored, to be spent from right to left.
     */
    public static void draw(GuiGraphics g, int x, int y, int z, int width, int height, TextureAtlasSprite sprite) {
        var icon = ICONS.computeIfAbsent(sprite.contents().name(), ParCoolEnergyIcons::recolor);
        if (CivitatumHud.placesParCoolEnergy()) x = ParCoolBridge.mirror(g.guiWidth(), x, width);
        g.blit(icon.texture(), x, y, z, 0, 0, width, height, icon.width(), icon.height());
    }

    public static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        ICONS.values().forEach(icon -> textures.release(icon.texture()));
        ICONS.clear();
    }

    /** Fails loudly: without the texture ParCool would draw nothing usable either. */
    private static Icon recolor(ResourceLocation sprite) {
        var file = sprite.withPath(path -> "textures/gui/sprites/" + path + ".png");
        NativeImage image;
        try (var stream = Minecraft.getInstance().getResourceManager().open(file)) {
            image = NativeImage.read(stream);
        } catch (Exception ex) {
            throw new IllegalStateException("Civitatum HUD: ParCool stamina icon " + file + " is missing", ex);
        }
        var ramp = EnergyRecolor.Ramp.of(sprite.getPath());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                // NativeImage holds ABGR; swapping red and blue converts either way.
                int argb = FastColor.ABGR32.fromArgb32(image.getPixelRGBA(x, y));
                image.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(EnergyRecolor.recolor(argb, ramp)));
            }
        }
        var texture = ResourceLocation.fromNamespaceAndPath("civitatum_hud",
            "parcool/" + sprite.getNamespace() + "/" + sprite.getPath());
        Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(image));
        return new Icon(texture, image.getWidth(), image.getHeight());
    }
}
