package team.civitatum.menu;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Client-only seasonal title screen: replaces the vanilla one and its menu music. */
@Mod(value = CivitatumMenu.ID, dist = Dist.CLIENT)
public final class CivitatumMenu {
    public static final String ID = "civitatum_menu";
    public static final Logger LOG = LogUtils.getLogger();

    public CivitatumMenu() {
        MenuConfig.load();
        NeoForge.EVENT_BUS.addListener(this::opening);
        // last, so that it sees the buttons other mods add to the title screen
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::initialized);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::sound);
    }

    public static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    private void opening(ScreenEvent.Opening event) {
        if (MenuConfig.current.enabled
            && !Minecraft.getInstance().isDemo()
            && event.getNewScreen() instanceof TitleScreen
            && !(event.getNewScreen() instanceof SeasonalTitleScreen)) {
            event.setNewScreen(new SeasonalTitleScreen());
        }
    }

    private void initialized(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof SeasonalTitleScreen screen) {
            ForeignButtons.arrange(screen, event);
        }
    }

    private void tick(ClientTickEvent.Post event) {
        MenuMusic.tick(Minecraft.getInstance());
    }

    /** Vanilla menu music would play over our own playlist. */
    private void sound(PlaySoundEvent event) {
        if (MenuConfig.current.enabled
            && MenuMusic.sessionActive()
            && event.getSound() != null
            && event.getSound().getLocation().equals(ResourceLocation.withDefaultNamespace("music.menu"))) {
            event.setSound(null);
        }
    }
}
