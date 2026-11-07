package team.civitatum.hud.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/** JEI finds and loads this class itself, only when it is installed: it hands over its runtime for the browser. */
@JeiPlugin
public final class CivitatumJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("civitatum_hud", "browser");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        JeiBrowser.runtime = runtime;
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiBrowser.runtime = null;
    }
}
