package team.civitatum.starclock.prediction;

import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import team.civitatum.starclock.prediction.handlers.*;

public final class PredictionExecutor {
    private static final Map<String, ActionHandler> HANDLERS = Map.of(
            "spawn", new SpawnHandler(), "effect", new EffectHandler(), "loot", new LootHandler(),
            "block", new BlockHandler(), "teleport", new TeleportHandler(), "damage", new DamageHandler(),
            "durability", new DurabilityHandler(), "weather", new WeatherHandler(), "trader", new TraderHandler());

    public static boolean runNext(ActionContext context) {
        var action = context.prediction().plan().actions().get(context.prediction().nextAction());
        return HANDLERS.get(action.type()).run(context, action.params());
    }

    public static void announce(ActionContext context) {
        var player = context.player();
        boolean positive = context.prediction().plan().positive();
        player.displayClientMessage(Component.translatable(context.prediction().plan().omenKey()), false);
        context.level().sendParticles(positive ? ParticleTypes.ENCHANT : ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.8, 0.5, 0.02);
        context.level().playSound(null, player.blockPosition(),
                positive ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.ENDER_EYE_DEATH,
                SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    private PredictionExecutor() {}
}
