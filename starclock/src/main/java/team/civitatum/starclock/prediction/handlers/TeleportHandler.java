package team.civitatum.starclock.prediction.handlers;

import com.google.gson.JsonObject;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import team.civitatum.starclock.prediction.ActionContext;
import team.civitatum.starclock.prediction.Parameters;
import team.civitatum.starclock.prediction.WorldSafety;

public final class TeleportHandler implements ActionHandler {
    @Override
    public boolean run(ActionContext context, JsonObject params) {
        var player = context.player();
        int distance = Parameters.integer(params, "distance", 16);
        String mode = Parameters.text(params, "mode", "random");
        Vec3 destination;
        if ("up".equals(mode)) {
            destination = player.position().add(0, distance, 0);
            if (!WorldSafety.free(context.level(), player, destination, false)) return false;
            // Падение не должно превращать пророчество в мгновенную казнь.
            if (player.getHealth() <= distance + 1) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200));
            }
        } else {
            double angle;
            if ("forward".equals(mode)) angle = Math.toRadians(player.getYRot() + 90);
            else angle = context.random().nextDouble() * Math.PI * 2;
            int minimum = Parameters.integer(params, "minimum", distance);
            int range = Math.max(minimum, distance);
            double length = minimum == range ? minimum : context.random().nextInt(minimum, range + 1);
            Vec3 wanted = player.position().add(Math.cos(angle) * length, 0, Math.sin(angle) * length);
            destination = WorldSafety.nearby(context.level(), player, wanted, 8);
            if (destination == null || destination.distanceToSqr(player.position()) < 4) return false;
        }
        player.teleportTo(destination.x, destination.y, destination.z);
        player.fallDistance = 0;
        return true;
    }
}
