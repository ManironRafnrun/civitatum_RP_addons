package team.civitatum.hud.client;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * ParCool 4.x: where its "Light" stamina HUD draws its row of ten icons, as in {@code LightStaminaHUD.render}.
 * The row's right edge is {@code guiWidth / 2 + 91}, its top {@code guiHeight - Gui.rightHeight}, both moved by the
 * offsets in ParCool's client config.
 */
public final class ParCoolBridge {
    public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath("parcool", "hud.stamina.host");
    /** Ten 9 px icons, 8 px apart. */
    public static final int WIDTH = 9 * 8 + 9, HEIGHT = 9;
    /** Vanilla's hotbar half width: ParCool lines the row up with the right end of the hotbar. */
    private static final int HOTBAR_HALF = 91;
    private static ModConfigSpec.IntValue offsetX, offsetY;
    private static boolean failed;

    /** False without ParCool or when its config could not be read; the row is then left where ParCool puts it. */
    public static boolean available() {
        if (failed || !CompatBridge.loaded("parcool")) return false;
        if (offsetX != null) return true;
        try {
            Object config = Class.forName("com.alrex.parcool.ParCool").getMethod("getConfig").invoke(null);
            Object client = config.getClass().getMethod("client").invoke(config);
            Object hud = client.getClass().getField("staminaHud").get(client);
            offsetX = (ModConfigSpec.IntValue) hud.getClass().getMethod("offsetHorizontal").invoke(hud);
            offsetY = (ModConfigSpec.IntValue) hud.getClass().getMethod("offsetVertical").invoke(hud);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            failed = true;
            LogUtils.getLogger().warn("Civitatum HUD: ParCool stamina adapter unavailable", ex);
            return false;
        }
    }

    public static int left(int guiWidth) {
        return guiWidth / 2 + HOTBAR_HALF + offsetX.get() - WIDTH;
    }

    /**
     * The icon's x with the row mirrored. ParCool puts its first icon at the right end of the row, so the row empties
     * from left to right; mirrored, it empties from right to left. The icons themselves are not flipped.
     */
    public static int mirror(int guiWidth, int x, int iconWidth) {
        return 2 * left(guiWidth) + WIDTH - iconWidth - x;
    }

    public static int top(int guiHeight, int rightHeight) {
        return guiHeight - rightHeight + offsetY.get();
    }
}
