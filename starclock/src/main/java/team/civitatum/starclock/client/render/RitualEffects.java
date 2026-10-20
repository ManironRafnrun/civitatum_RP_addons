package team.civitatum.starclock.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent;
import team.civitatum.starclock.client.RitualClientState;
import team.civitatum.starclock.item.StarToolItem;

public final class RitualEffects {
    private static boolean timely(double startTick) {
        var state = RitualClientState.current();
        return state != null && !state.drawing()
                && Math.abs(RitualClientState.animationTicks(0) - startTick) <= 3;
    }

    public static void sound(SoundKeyframeEvent<StarToolItem> event) {
        var keyframe = event.getKeyframeData();
        if (!timely(keyframe.getStartTick())) return;
        var id = ResourceLocation.parse(keyframe.getSound());
        if (BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(BuiltInRegistries.SOUND_EVENT.get(id), 1.0F, 0.45F));
        }
    }

    public static void particle(ParticleKeyframeEvent<StarToolItem> event) {
        var keyframe = event.getKeyframeData();
        var mc = Minecraft.getInstance();
        if (!timely(keyframe.getStartTick()) || mc.player == null || mc.level == null) return;
        Vector3f colour = switch (keyframe.getEffect()) {
            case "starclock:amethyst" -> new Vector3f(0.557F, 0.435F, 0.702F);
            case "starclock:gold" -> new Vector3f(0.890F, 0.753F, 0.482F);
            default -> throw new IllegalArgumentException("Неизвестная частица обряда: " + keyframe.getEffect());
        };
        Vec3 center = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(0.7));
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4;
            mc.level.addParticle(new DustParticleOptions(colour, 0.65F),
                    center.x + Math.cos(angle) * 0.08, center.y - 0.13,
                    center.z + Math.sin(angle) * 0.08, 0, 0.012, 0);
        }
    }

    private RitualEffects() {}
}
