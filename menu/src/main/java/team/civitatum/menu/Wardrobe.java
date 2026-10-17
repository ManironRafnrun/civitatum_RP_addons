package team.civitatum.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.neoforged.fml.ModList;

/** Opens the NCL Skins gallery when that mod is present, otherwise the vanilla skin settings. */
public final class Wardrobe {
    /** NCL Skins keeps its runtime under one of these names depending on its version. */
    private static final String[] NCL_RUNTIMES = {
        "com.naocraftlab.skins.compat.client.resourcelocation.playerinfo.ImmediateClientRuntime",
        "com.naocraftlab.skins.compat.client.resourcelocation.skinlookup.ImmediateClientRuntime"
    };

    private Wardrobe() {
    }

    public static void open(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ModList.get().isLoaded("nclskins")) {
            for (String name : NCL_RUNTIMES) {
                try {
                    Class<?> type = Class.forName(name);
                    Object runtime = type.getMethod("instance").invoke(null);
                    minecraft.setScreen((Screen) type.getMethod("createScreen", Screen.class).invoke(runtime, parent));
                    return;
                } catch (ClassNotFoundException e) {
                    // try the next known location
                } catch (LinkageError | ReflectiveOperationException e) {
                    CivitatumMenu.LOG.warn("Cannot open the NCL gallery directly; using skin settings", e);
                    break;
                }
            }
        }
        minecraft.setScreen(new SkinCustomizationScreen(parent, minecraft.options));
    }
}
