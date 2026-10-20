package team.civitatum.starclock.prediction.handlers;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import team.civitatum.starclock.data.TimedSpawn;
import team.civitatum.starclock.prediction.ActionContext;
import team.civitatum.starclock.prediction.ItemFactory;
import team.civitatum.starclock.prediction.Parameters;
import team.civitatum.starclock.prediction.SafeNumbers;
import team.civitatum.starclock.prediction.WorldSafety;

public final class SpawnHandler implements ActionHandler {
    @Override
    public boolean run(ActionContext context, JsonObject params) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(params.get("entity").getAsString()))
                .orElseThrow(() -> new IllegalArgumentException("Неизвестная сущность"));
        int count = Parameters.integer(params, "count", 1);
        if (Parameters.flag(params, "scale", true)) count = SafeNumbers.scaled(count, context.tier());
        int minimum = Parameters.integer(params, "distance", 8);
        int maximum = Math.max(minimum, Parameters.integer(params, "maxDistance", minimum));
        var random = context.random();
        List<Entity> entities = new ArrayList<>();
        List<Vec3> positions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Entity entity = type.create(context.level());
            if (!(entity instanceof Mob)) throw new IllegalArgumentException("Призыв поддерживает только мобов");
            Vec3 position = null;
            for (int attempt = 0; attempt < 24; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = minimum + random.nextDouble() * (maximum - minimum);
                Vec3 wanted = context.player().position().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                Vec3 found = WorldSafety.nearby(context.level(), entity, wanted, 3);
                if (found == null || positions.stream().anyMatch(previous -> previous.distanceToSqr(found) < 2.25)) continue;
                if (entity instanceof Enemy && context.data().isPeaceful(context.level().dimension().location().toString(),
                        found.x, found.y, found.z, context.now())) continue;
                position = found;
                break;
            }
            if (position == null) return false;
            positions.add(position);
            entities.add(entity);
        }
        int spawned = 0;
        for (int i = 0; i < entities.size(); i++) {
            Mob mob = (Mob) entities.get(i);
            Vec3 pos = positions.get(i);
            mob.moveTo(pos.x, pos.y, pos.z, context.player().getYRot() + 180, 0);
            EventHooks.finalizeMobSpawn(mob, context.level(), context.level().getCurrentDifficultyAt(mob.blockPosition()),
                    MobSpawnType.EVENT, null);
            configure(context, params, mob);
            int lifetime = Parameters.integer(params, "lifetime", 0);
            long expires = context.now() + lifetime * 20L;
            if (lifetime > 0) mob.getPersistentData().putLong("StarclockExpires", expires);
            if (!context.level().addFreshEntity(mob)) continue;
            spawned++;
            if (lifetime > 0) {
                context.data().addSpawn(new TimedSpawn(mob.getUUID(),
                        context.level().dimension().location().toString(), expires));
            }
        }
        // Отмена части спавнов другим модом не должна дублировать уже появившихся мобов.
        return spawned > 0;
    }

    private void configure(ActionContext context, JsonObject params, Mob mob) {
        mob.getPersistentData().putUUID("StarclockOwner", context.player().getUUID());
        String mode = Parameters.text(params, "mode", "default");
        if (mob instanceof IronGolem golem && "ally".equals(mode)) {
            golem.setPlayerCreated(true);
            golem.getPersistentData().putBoolean("StarclockAlly", true);
            golem.getPersistentData().putInt("StarclockGolemIron", 2);
        }
        if ("hostile".equals(mode) || "bite_and_flee".equals(mode)) {
            mob.setTarget(context.player());
            if (mob instanceof Wolf wolf) {
                wolf.setPersistentAngerTarget(context.player().getUUID());
                wolf.setRemainingPersistentAngerTime(6000);
                wolf.getPersistentData().putBoolean("StarclockBiteAndFlee", "bite_and_flee".equals(mode));
            }
        }
        if ("tame".equals(mode)) {
            if (mob instanceof Wolf wolf) wolf.tame(context.player());
            if (mob instanceof AbstractHorse horse) {
                horse.setTamed(true);
                horse.setOwnerUUID(context.player().getUUID());
            }
        }
        if (params.has("health")) {
            var maximumHealth = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (maximumHealth == null) throw new IllegalArgumentException("У сущности нет здоровья");
            maximumHealth.setBaseValue(params.get("health").getAsDouble());
            mob.setHealth(mob.getMaxHealth());
        }
        if (params.has("equipment")) {
            for (var equipment : params.getAsJsonObject("equipment").entrySet()) {
                var slot = EquipmentSlot.valueOf(equipment.getKey().toUpperCase(java.util.Locale.ROOT));
                mob.setItemSlot(slot, ItemFactory.create(equipment.getValue().getAsJsonObject(), context.player()));
                mob.setDropChance(slot, 0);
            }
        }
        if (Parameters.flag(params, "noDrops", false)) mob.getPersistentData().putBoolean("StarclockNoDrops", true);
        if (mob instanceof Enemy || Parameters.flag(params, "noDrops", false)) mob.setPersistenceRequired();
        if (params.has("effects")) {
            for (var value : params.getAsJsonArray("effects")) {
                var effect = value.getAsJsonObject();
                var holder = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(effect.get("id").getAsString()))
                        .orElseThrow();
                mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(holder,
                        effect.get("seconds").getAsInt() * 20, Parameters.integer(effect, "level", 1) - 1));
            }
        }
    }
}
