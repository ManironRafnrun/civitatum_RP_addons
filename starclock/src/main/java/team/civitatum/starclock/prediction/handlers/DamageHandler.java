package team.civitatum.starclock.prediction.handlers;

import com.google.gson.JsonObject;
import team.civitatum.starclock.prediction.ActionContext;
import team.civitatum.starclock.prediction.Parameters;
import team.civitatum.starclock.prediction.SafeNumbers;

public final class DamageHandler implements ActionHandler {
    @Override
    public boolean run(ActionContext context, JsonObject params) {
        if ("hostile".equals(Parameters.text(params, "target", "player"))) {
            int radius = Parameters.integer(params, "radius", 8);
            for (var mob : context.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    context.player().getBoundingBox().inflate(radius),
                    entity -> entity instanceof net.minecraft.world.entity.monster.Enemy
                            && entity.distanceToSqr(context.player()) <= radius * radius)) {
                mob.hurt(context.level().damageSources().playerAttack(context.player()), params.get("amount").getAsFloat());
            }
        } else {
            float amount = SafeNumbers.damage(params.get("amount").getAsFloat(), context.player().getHealth(), context.tier());
            if (amount > 0) {
                team.civitatum.starclock.prediction.PredictionService.guardedDamage(
                        context, amount, Parameters.flag(params, "magic", false));
            }
        }
        return true;
    }
}
