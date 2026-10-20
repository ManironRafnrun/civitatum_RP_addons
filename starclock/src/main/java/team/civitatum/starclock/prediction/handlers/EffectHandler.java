package team.civitatum.starclock.prediction.handlers;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import team.civitatum.starclock.ability.TowerKeyService;
import team.civitatum.starclock.data.PeaceZone;
import team.civitatum.starclock.prediction.ActionContext;
import team.civitatum.starclock.prediction.Parameters;
import team.civitatum.starclock.prediction.SafeNumbers;

public final class EffectHandler implements ActionHandler {
    @Override
    public boolean run(ActionContext context, JsonObject params) {
        var player = context.player();
        if (params.has("ability")) {
            if (!"tower_key".equals(params.get("ability").getAsString())) throw new IllegalArgumentException("Неизвестная способность");
            TowerKeyService.grant(player);
        }
        if (Parameters.flag(params, "tameNearest", false)) {
            var wolves = context.level().getEntitiesOfClass(Wolf.class, player.getBoundingBox().inflate(16),
                    wolf -> !wolf.isTame());
            var nearest = wolves.stream().min(java.util.Comparator.comparingDouble(wolf -> wolf.distanceToSqr(player)));
            if (nearest.isPresent()) {
                nearest.get().tame(player);
                nearest.get().setTarget(null);
                nearest.get().setRemainingPersistentAngerTime(0);
                nearest.get().setPersistentAngerTarget(null);
            } else {
                JsonObject spawn = new JsonObject();
                spawn.addProperty("entity", "minecraft:wolf");
                spawn.addProperty("distance", 6);
                spawn.addProperty("count", 1);
                spawn.addProperty("scale", false);
                spawn.addProperty("mode", "tame");
                return new SpawnHandler().run(context, spawn);
            }
        }
        if (params.has("food")) {
            player.getFoodData().setFoodLevel(Math.clamp(params.get("food").getAsInt(), 0, 20));
            player.getFoodData().setSaturation((float) Math.clamp(Parameters.decimal(params, "saturation", 0), 0, 20));
        }
        if (params.has("experienceLevels")) {
            int amount = params.get("experienceLevels").getAsInt();
            player.giveExperienceLevels(amount < 0 ? -Math.min(-amount, player.experienceLevel) : amount);
        }
        if (params.has("fireSeconds")) player.igniteForSeconds(params.get("fireSeconds").getAsFloat());
        if (params.has("peaceSeconds")) {
            var pos = player.blockPosition();
            context.data().addZone(new PeaceZone(context.level().dimension().location().toString(),
                    pos.getX(), pos.getY(), pos.getZ(), Parameters.integer(params, "radius", 32),
                    context.now() + SafeNumbers.scaled(params.get("peaceSeconds").getAsInt(), context.tier()) * 20L));
        }
        List<LivingEntity> targets = targets(context, params);
        if (params.has("clear") && "negative".equals(params.get("clear").getAsString())) {
            for (var target : targets) {
                var effects = new ArrayList<>(target.getActiveEffects());
                effects.stream().filter(effect -> effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                        .forEach(effect -> target.removeEffect(effect.getEffect()));
            }
        }
        if (params.has("remove")) {
            for (var value : params.getAsJsonArray("remove")) {
                var effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(value.getAsString()))
                        .orElseThrow(() -> new IllegalArgumentException("Неизвестный эффект"));
                targets.forEach(target -> target.removeEffect(effect));
            }
        }
        if (params.has("effects")) {
            for (var value : params.getAsJsonArray("effects")) {
                var effect = value.getAsJsonObject();
                var holder = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(effect.get("id").getAsString()))
                        .orElseThrow(() -> new IllegalArgumentException("Неизвестный эффект"));
                int duration = SafeNumbers.scaled(effect.get("seconds").getAsInt(), context.tier()) * 20;
                int amplifier = Parameters.integer(effect, "level", 1) - 1;
                for (var target : targets) target.addEffect(new MobEffectInstance(holder, duration, amplifier));
            }
        }
        return true;
    }

    private List<LivingEntity> targets(ActionContext context, JsonObject params) {
        String target = Parameters.text(params, "target", "player");
        if ("player".equals(target)) return List.of(context.player());
        int radius = Parameters.integer(params, "radius", 16);
        var entities = context.level().getEntitiesOfClass(LivingEntity.class,
                context.player().getBoundingBox().inflate(radius),
                entity -> entity.isAlive() && entity.distanceToSqr(context.player()) <= radius * radius
                        && ("undead".equals(target) ? entity.getType().is(EntityTypeTags.UNDEAD) : entity instanceof Enemy));
        return List.copyOf(entities);
    }
}
