package team.civitatum.charactercreation.client;

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.power.Traits;

/**
 * «Звёздный свет» of the astral elf: the elf is a light of level 6 through LambDynamicLights. Loaded only by that
 * mod, through the "lambdynlights:initializer" entry point in neoforge.mods.toml; without it this class is never
 * touched. The light is drawn by clients only: monsters still spawn by the server's light.
 */
public final class StarlightCompat implements DynamicLightsInitializer {
    /** "вокруг тебя 6 блоков света". */
    private static final int LIGHT = 6;

    private static final EntityLuminance STARLIGHT = new EntityLuminance() {
        @Override
        public Type type() { return TYPE; }

        @Override
        public int getLuminance(ItemLightSourceManager items, Entity entity) {
            return Traits.isRace(entity, "astral_elf") ? LIGHT : 0;
        }
    };
    private static final EntityLuminance.Type TYPE = EntityLuminance.Type.registerSimple(Civitatum.res("starlight"),
        STARLIGHT);

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        context.entityLightSourceManager().onRegisterEvent().register(c -> c.register(EntityType.PLAYER, STARLIGHT));
    }

    @Override
    public void onInitializeDynamicLights(ItemLightSourceManager items) {}

}
