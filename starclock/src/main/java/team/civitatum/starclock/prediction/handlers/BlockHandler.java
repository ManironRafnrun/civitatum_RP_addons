package team.civitatum.starclock.prediction.handlers;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import team.civitatum.starclock.config.StarClockConfig;
import team.civitatum.starclock.data.TimedBlock;
import team.civitatum.starclock.prediction.ActionContext;
import team.civitatum.starclock.prediction.Parameters;

public final class BlockHandler implements ActionHandler {
    @Override
    public boolean run(ActionContext context, JsonObject params) {
        if (!StarClockConfig.DESTRUCTIVE_EVENTS.get()) {
            context.player().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600));
            return true;
        }
        var pos = context.player().blockPosition().offset(Parameters.integer(params, "x", 2),
                Parameters.integer(params, "y", 0), Parameters.integer(params, "z", 0));
        if (!context.level().hasChunkAt(pos) || !context.level().getWorldBorder().isWithinBounds(pos)
                || !context.level().isEmptyBlock(pos) || !context.level().getFluidState(pos).isEmpty()
                || !context.level().getEntities(null, new AABB(pos)).isEmpty()) return false;
        var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(params.get("block").getAsString()))
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный блок"));
        // Дополнительные таблицы могут временно ставить только безопасные строительные блоки.
        if (!java.util.Set.of(net.minecraft.world.level.block.Blocks.STONE,
                net.minecraft.world.level.block.Blocks.COBBLESTONE).contains(block)) {
            throw new IllegalArgumentException("Недопустимый временный блок");
        }
        if (!context.level().setBlock(pos, block.defaultBlockState(), 3)) return false;
        context.data().addBlock(new TimedBlock(context.level().dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ(), params.get("block").getAsString(),
                context.now() + Parameters.integer(params, "seconds", 10) * 20L));
        return true;
    }
}
