package team.civitatum.starclock.client.render;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import team.civitatum.starclock.animation.RitualAnimationController;
import team.civitatum.starclock.client.RitualClientState;
import team.civitatum.starclock.item.StarToolItem;

public final class StarToolModel extends GeoModel<StarToolItem> {
    private UUID renderedSession;

    private static ResourceLocation asset(String directory, StarToolItem item, String extension) {
        return ResourceLocation.fromNamespaceAndPath("starclock", directory + "/" + item.kind().id() + extension);
    }

    @Override
    public ResourceLocation getModelResource(StarToolItem item) {
        return asset("geo", item, ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(StarToolItem item) {
        return asset("textures/model", item, ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(StarToolItem item) {
        return asset("animations", item, ".animation.json");
    }

    @Override
    public boolean crashIfBoneMissing() {
        return true;
    }

    @Override
    public void handleAnimations(StarToolItem item, long instanceId, AnimationState<StarToolItem> state, float partialTick) {
        AnimatableManager<StarToolItem> manager = item.getAnimatableInstanceCache().getManagerForId(instanceId);
        if (!java.util.Objects.equals(renderedSession, RitualClientState.session())) {
            renderedSession = RitualClientState.session();
            manager.clearSnapshotCache();
            manager.removeController("ritual");
            manager.addController(new RitualAnimationController(item)
                    .setSoundKeyframeHandler(RitualEffects::sound)
                    .setParticleKeyframeHandler(RitualEffects::particle));
        }
        double tick = RitualClientState.animationTicks(partialTick);
        ((RitualAnimationController) manager.getAnimationControllers().get("ritual")).seek(tick);
        state.animationTick = tick;
        var processor = getAnimationProcessor();
        processor.preAnimationSetup(state, tick);
        processor.tickAnimation(item, this, manager, tick, state, true);
    }
}
