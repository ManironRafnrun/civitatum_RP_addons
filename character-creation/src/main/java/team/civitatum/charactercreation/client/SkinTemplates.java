package team.civitatum.charactercreation.client;

import java.util.*;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import team.civitatum.charactercreation.*;

/**
 * One explicit compositing order shared by the editor and world rendering.
 */
public final class SkinTemplates {
    public static final int[] UNDERWEAR_COLORS = {0, 0xE0D5B9, 0x44434A, 0xA45B6C, 0x578FB8, 0x687E60, 0x9B7753};

    private record Entry(ResourceLocation skin, ResourceLocation glow) {
    }

    private static final Map<String, Entry> CACHE = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> GLOW = new HashMap<>();
    private static long nextId;

    public static String model(CharacterData c) {
        return c.armModel.equals("auto") ? (c.female() ? "slim" : "classic") : c.armModel;
    }

    public static PlayerSkin skin(CharacterData c, PlayerSkin original) {
        if (!c.selected) return original;
        var race = RaceCatalog.get(c.race);
        if (race == null) return original;
        String model = model(c);
        String key = c.race + "/" + c.sex + "/" + model + "/" + c.skin + "/" + c.eyes + "/" + c.skinOriginal + "/" + c.eyesOriginal + "/" + c.eyeHeight + "/" + c.hair + "/" + c.hairStyle + "/" + c.hairAsset + "/" + c.hairMain + "/" + c.hairDecor1 + "/" + c.hairDecor2 + "/" + c.hairLinkedShades + "/" + c.beard + "/" + c.mustache + "/" + c.usePlayerSkin + "/" + original.texture() + "/" + c.naked + "/" + c.underwearColor + "/" + c.wardrobeEnabled + "/" + DataJson.GSON.toJson(c.wardrobe) + "/" + c.tattoo + "/" + c.tattooColor + "/" + c.glow;
        Entry entry = CACHE.get(key);
        if (entry == null) {
            NativeImage image = c.usePlayerSkin ? AppearanceTextures.ownSkin(original.texture()) : new NativeImage(64, 64, true);
            if (image == null) return original;
            NativeImage emission = new NativeImage(image.getWidth(), image.getHeight(), true);
            if (c.usePlayerSkin)
                AppearanceTextures.remapArms(image, original.model() == PlayerSkin.Model.SLIM ? 3 : 4, model.equals("slim") ? 3 : 4);
            else
                AppearanceTextures.paint(image, "textures/character/skin/" + model + "/original.png", "textures/character/skin/" + model + "/neutral.png",
                        Map.of("textures/character/skin/" + model + "/mask_skin.png", c.skinOriginal ? -1 : race.color("skin", c.skin)), 0);
            // Skin -> race markings -> underwear -> eyes -> clothes -> facial hair -> hair.
            tattoos(image, emission, c, race);
            String underwear = "textures/character/underwear/underwear_" + (c.female() ? "fem" : "male") + ".png";
            AppearanceTextures.underwear(image, emission, underwear, UNDERWEAR_COLORS[c.underwearColor], UnderwearVisibility.of(c));
            if (!c.usePlayerSkin) {
                String path = "textures/character/eyes/" + model;
                var colors = new LinkedHashMap<String, Integer>();
                colors.put(path + "/mask_iris.png", c.eyesOriginal ? -1 : race.color("eyes", c.eyes));
                colors.put(path + "/mask_upper_contour.png", hairColor(c));
                AppearanceTextures.paint(image, path + "/original.png", path + "/neutral.png", colors, -c.eyeHeight);
                if (race.id.equals("jahar"))
                    for (int x : new int[]{9, 10, 13, 14}) set(emission, x, 13 - c.eyeHeight, 0xFFFFFFFF);
            }
            if (!c.naked && c.wardrobeEnabled) for (String group : AppearanceCatalog.GROUPS) {
                var choice = c.wardrobe.get(group);
                var asset = choice == null ? null : AppearanceCatalog.clothing(choice.asset);
                if (asset != null && asset.permits(c.sex)) {
                    AppearanceTextures.apply(image, asset, model, choice.main, choice.decor1, choice.decor2);
                    var v = asset.models().get(model);
                    AppearanceTextures.occlude(emission, v.directory() + "/original.png");
                }
            }
            if (RaceCatalog.beardAllowed(c)) {
                facialHair(image, emission, "beard", c.beard, c);
                facialHair(image, emission, "mustache", c.mustache, c);
            }
            var hair = AppearanceCatalog.hair(c.hairAsset);
            if (hair != null && race.hairAllowed) {
                AppearanceTextures.applyHair(image, hair, model, c);
                AppearanceTextures.occlude(emission, hair.models().get(model).directory() + "/original.png");
            } else if (!c.usePlayerSkin && c.hairStyle > 0 && race.hairAllowed) {
                int color = rgba(hairColor(c));
                fill(image, 0, 0, 32, 8, color);
                fill(image, 0, 8, 8, 4, color);
                fill(image, 16, 8, 16, 4, color);
                fill(image, 8, 8, 8, c.hairStyle == 2 ? 3 : 1, color);
            }
            String id = Long.toString(nextId++);
            var location = register("generated/skin/" + id, image);
            ResourceLocation glow = null;
            if (race.id.equals("jahar") || (race.id.equals("orthosian") && c.glow && c.tattoo > 0))
                glow = register("generated/glow/" + id, emission);
            else emission.close();
            entry = new Entry(location, glow);
            CACHE.put(key, entry);
            if (glow != null) GLOW.put(location, glow);
            if (CACHE.size() > 128) {
                var it = CACHE.entrySet().iterator();
                var first = it.next();
                it.remove();
                release(first.getValue());
            }
        }
        return new PlayerSkin(entry.skin, original.textureUrl(), original.capeTexture(), original.elytraTexture(), model.equals("slim") ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, original.secure());
    }

    public static int hairColor(CharacterData c) {
        var a = AppearanceCatalog.hair(c.hairAsset);
        if (a != null) {
            var colors = a.controls().get("main").colors();
            return colors.get(AppearanceCatalog.color(a, "main", c.hairMain)).rgb();
        }
        return RaceCatalog.get(c.race).color("hair", c.hair);
    }

    private static void facialHair(NativeImage image, NativeImage emission, String kind, int style, CharacterData c) {
        if (style < 1 || style > 5) return;
        String path = "textures/character/facial_hair/" + kind + "_" + style + ".png";
        AppearanceTextures.paint(image, path, path, Map.of(path, hairColor(c)), 0, true);
        AppearanceTextures.occlude(emission, path);
    }

    private static void tattoos(NativeImage image, NativeImage emission, CharacterData c, RaceDefinition r) {
        if (c.tattoo <= 0 || (!r.id.equals("jahar") && !r.id.equals("orthosian"))) return;
        int color = rgba(r.color("tattoo", c.tattooColor));
        for (int y = 9; y <= 14; y++)
            for (int x : new int[]{8, 15}) mark(image, emission, x, y, color, c.glow && r.id.equals("orthosian"));
        if (c.tattoo % 2 == 0)
            for (int x = 8; x <= 15; x++) mark(image, emission, x, 10, color, c.glow && r.id.equals("orthosian"));
        if (c.tattoo >= 3)
            for (int y = 8; y <= 11; y++) mark(image, emission, 11, y, color, c.glow && r.id.equals("orthosian"));
        if (r.id.equals("orthosian")) for (int row = 0; row < c.tattoo; row++)
            for (int x = 20; x < 28; x++) mark(image, emission, x, 21 + row, color, c.glow);
    }

    private static void mark(NativeImage image, NativeImage emission, int x, int y, int color, boolean glow) {
        set(image, x, y, color);
        if (glow) set(emission, x, y, color);
    }

    private static void set(NativeImage image, int x, int y, int color) {
        int s = image.getWidth() / 64;
        for (int dy = 0; dy < s; dy++) for (int dx = 0; dx < s; dx++) image.setPixelRGBA(x * s + dx, y * s + dy, color);
    }

    private static void fill(NativeImage image, int x, int y, int w, int h, int color) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) set(image, xx, yy, color);
    }

    private static int rgba(int rgb) {
        return 0xFF000000 | (rgb & 255) << 16 | rgb & 0xFF00 | (rgb >> 16) & 255;
    }

    private static ResourceLocation register(String path, NativeImage image) {
        var id = Civitatum.res(path);
        var texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return id;
    }

    public static ResourceLocation glow(ResourceLocation skin) {
        return GLOW.get(skin);
    }

    private static void release(Entry e) {
        var m = Minecraft.getInstance().getTextureManager();
        m.release(e.skin);
        GLOW.remove(e.skin);
        if (e.glow != null) m.release(e.glow);
    }

    public static void clear() {
        CACHE.values().forEach(SkinTemplates::release);
        CACHE.clear();
        GLOW.clear();
        AppearanceTextures.clear();
    }
}
