package team.civitatum.charactercreation;

import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.block.Blocks;

public final class RaceAbilities {
    public static final class Runtime {
        public long activeUntil, lastHurt = -10000, lastRequest = -10000, repairUntil, fatigueUntil;
        public String mode = "";
        public Vec3 repairAt;
        public long repairHurt;
        public int xpTenths;
    }
    private static final Map<UUID, Runtime> RUNTIME = new HashMap<>();
    public static Runtime runtime(Player p) { return RUNTIME.computeIfAbsent(p.getUUID(), ignored -> new Runtime()); }
    public static boolean friendly(Player p, LivingEntity target) {
        if (p == target || p.isAlliedTo(target)) return true;
        if (target instanceof TamableAnimal pet && pet.isOwnedBy(p)) return true;
        if (target instanceof Player q && CompatPatches.sameTeam(p, q)) return true;
        if (target instanceof TamableAnimal pet && pet.getOwner() instanceof Player owner)
            return p.isAlliedTo(owner) || CompatPatches.sameTeam(p, owner);
        return false;
    }
    public static boolean hostile(ServerPlayer p, LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target.isInvulnerable() || friendly(p, target)) return false;
        if (target instanceof Player q) return p.server.isPvpAllowed() && p.canHarmPlayer(q) && !q.isCreative();
        return target instanceof Enemy || (target instanceof Mob mob && mob.getTarget() == p);
    }
    public static int experience(Player p, int value) {
        var c=p.getData(Civitatum.STATE).character();
        if(!c.selected || value<=0)return value;
        int bonus=(c.race.equals("human")?1:0)+(c.birthsign.equals("mage")?1:0);
        if(bonus==0)return value;
        Runtime rt = runtime(p);
        rt.xpTenths += value*bonus;
        int extra = rt.xpTenths / 10;
        rt.xpTenths %= 10;
        return value + extra;
    }
    public static void activate(ServerPlayer p) {
        RaceDefinition r = RaceService.race(p);
        if (r == null || !p.isAlive() || p.isSpectator()) return;
        long now = RaceService.time(p);
        Runtime rt = runtime(p);
        if (now - rt.lastRequest < 5) return;
        rt.lastRequest = now;
        var state = p.getData(Civitatum.STATE);
        if (state.cooldownUntil() > now) {
            p.displayClientMessage(Component.literal("Способность восстановится через " + ((state.cooldownUntil() - now + 19) / 20) + " сек."), true); return;
        }
        Vec3 destination = null;
        LivingEntity single = null;
        if (r.id.equals("astral_elf") || r.id.equals("goblin")) {
            Vec3 vector = r.id.equals("goblin") ? new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize().scale(-3) : p.getLookAngle().scale(6);
            destination = safeDestination(p, vector);
            if (destination.distanceToSqr(p.position()) < .09) { message(p, "Недостаточно места для перемещения"); return; }
        }
        if (r.id.equals("forest_algnor") && !p.onGround()) { message(p, "Для скачка нужно стоять на земле"); return; }
        if (r.id.equals("forged") && p.getHealth() >= p.getMaxHealth()) { message(p, "Ремонт сейчас не требуется"); return; }
        if (r.id.equals("jahar")) {
            single = beamTarget(p, 6);
            if (single == null) { message(p, "В пределах шести блоков нет враждебной цели"); return; }
        }
        if (!pay(p, r)) return;
        p.setData(Civitatum.STATE, new RaceState(state.character(), state.curseDeaths(), now + r.cooldown * 20L));
        rt.mode = r.id; rt.activeUntil = now;
        switch (r.id) {
            case "human" -> { rt.activeUntil = now + 200; timed(p, Attributes.BLOCK_BREAK_SPEED, "ability_mining", .15); timed(p, Attributes.ATTACK_SPEED, "ability_attack", .10); }
            case "astral_elf", "goblin" -> p.teleportTo(destination.x, destination.y, destination.z);
            case "dark_elf" -> { rt.activeUntil = now + 160; effect(p, MobEffects.INVISIBILITY, 160, 0); }
            case "night_elf" -> {
                for (var entity : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(12), e -> hostile(p, e)))
                    if (entity.distanceToSqr(p) <= 144) effect(entity, MobEffects.GLOWING, 120, 0);
            }
            case "wood_elf" -> { rt.activeUntil = now + 160; }
            case "dwarf" -> effect(p, MobEffects.DIG_SPEED, 300, 0);
            case "orc" -> { rt.activeUntil = now + 120; rt.fatigueUntil = now + 180; effect(p, MobEffects.DAMAGE_BOOST, 120, 0); }
            case "fire_dragonborn", "frost_dragonborn", "storm_dragonborn", "end_dragonborn" -> breath(p, r.id);
            case "firbolg" -> {
                effect(p, MobEffects.REGENERATION, 200, 0);
                for (ServerPlayer q : p.serverLevel().players())
                    if (q != p && q.distanceToSqr(p) <= 25 && friendly(p,q)) effect(q, MobEffects.REGENERATION, 200, 0);
            }
            case "forged" -> { rt.repairUntil = now + 80; rt.repairAt = p.position(); rt.repairHurt = rt.lastHurt; }
            case "hobbit" -> rt.activeUntil = now + 160;
            case "orthosian" -> {
                rt.activeUntil = now + 160;
                effect(p, MobEffects.DAMAGE_RESISTANCE, 160, 0);
                RaceService.modifier(p, Attributes.KNOCKBACK_RESISTANCE, "ability_knockback", .50, AttributeModifier.Operation.ADD_VALUE);
            }
            case "forest_algnor" -> {
                Vec3 forward = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize().scale(.62);
                p.setDeltaMovement(forward.x, .42, forward.z); p.hurtMarked = true;
            }
            case "city_algnor" -> {
                p.removeEffect(MobEffects.WEAKNESS); p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                rt.activeUntil = now + 120;
            }
            case "jahar" -> {
                if (single.hurt(p.damageSources().playerAttack(p), 6)) p.heal(2);
                p.serverLevel().sendParticles(ParticleTypes.END_ROD, single.getX(), single.getY() + single.getBbHeight()/2, single.getZ(), 12, .2, .3, .2, .01);
            }
            default -> {}
        }
        tick(p);
        RaceNetwork.sync(p);
        message(p, r.active.split("•")[0].strip());
    }
    private static boolean pay(ServerPlayer p, RaceDefinition r) {
        if (p.isCreative()) return true;
        if (r.magic && ModConfig.INSTANCE.arsNouveauPatch && CompatPatches.arsLoaded()) {
            int mana = switch (r.id) {
                case "astral_elf" -> 30;
                case "dark_elf","night_elf" -> 25;
                case "wood_elf" -> 20;
                case "firbolg" -> 35;
                case "jahar" -> 30;
                default -> 40;
            };
            if (!CompatPatches.payMana(p, mana)) { message(p, "Недостаточно маны"); return false; }
            return true;
        }
        int food = p.getFoodData().getFoodLevel();
        if (food - r.food < 6) { message(p, "После применения должно остаться хотя бы 6 единиц питания"); return false; }
        p.getFoodData().setFoodLevel(food - r.food);
        p.getFoodData().setSaturation(Math.min(p.getFoodData().getSaturationLevel(), food - r.food));
        return true;
    }
    private static void timed(ServerPlayer p, net.minecraft.core.Holder<Attribute> a, String id, double amount) {
        RaceService.modifier(p, a, id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }
    private static void effect(LivingEntity e, net.minecraft.core.Holder<MobEffect> effect, int ticks, int amplifier) {
        e.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, true, true));
    }
    public static void tick(ServerPlayer p) {
        Runtime rt = runtime(p);
        long now = RaceService.time(p);
        RaceDefinition r = RaceService.race(p);
        if (r == null) return;
        boolean active = rt.activeUntil > now;
        double speed = active ? switch(rt.mode) {
            case "wood_elf" -> .2;
            case "hobbit" -> p.onGround() ? .3 : 0;
            case "city_algnor" -> .1;
            case "orthosian" -> -.2;
            default -> 0;
        } : rt.mode.equals("orc") && rt.fatigueUntil > now ? -.1 : 0;
        timed(p, Attributes.MOVEMENT_SPEED, "ability_speed", speed);
        if (!active) {
            timed(p, Attributes.BLOCK_BREAK_SPEED, "ability_mining", 0);
            timed(p, Attributes.ATTACK_SPEED, "ability_attack", 0);
            RaceService.modifier(p, Attributes.KNOCKBACK_RESISTANCE, "ability_knockback", 0, AttributeModifier.Operation.ADD_VALUE);
        }
        if (rt.repairUntil > 0) {
            if (rt.repairAt.distanceToSqr(p.position()) > .0025 || rt.repairHurt != rt.lastHurt || p.isUsingItem() || !p.isAlive()) {
                rt.repairUntil = 0; message(p, "Ремонт прерван");
            } else if (now >= rt.repairUntil) { p.heal(6); rt.repairUntil = 0; message(p, "Ремонт завершён"); }
            else if (p.tickCount % 20 == 0) message(p, "Ремонт: " + ((rt.repairUntil - now + 19) / 20) + " сек.");
        }
        if (r.elf() && p.tickCount % 200 == 0) effect(p, MobEffects.NIGHT_VISION, 600, 0);
        if (r.id.equals("firbolg") && p.tickCount % 200 == 0 && now - rt.lastHurt >= 100 && p.getFoodData().getFoodLevel() >= 10) {
            var under = p.level().getBlockState(p.blockPosition().below());
            if (under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.MOSS_BLOCK)) p.heal(1);
        }
        if (r.id.equals("jahar")) {
            if (p.tickCount % 80 == 0 && now - rt.lastHurt >= 100 && !p.isOnFire() && p.getFoodData().getFoodLevel() >= 6 && p.getHealth() < p.getMaxHealth()) {
                p.heal(1); p.causeFoodExhaustion(2);
            }
            if (p.isOnFire() && p.getRemainingFireTicks() > 0 && p.getRemainingFireTicks() % 20 == 10)
                p.hurt(p.damageSources().onFire(), 1);
        }
        CompatPatches.tick(p, r);
    }
    public static void attacked(ServerPlayer p) {
        Runtime rt = runtime(p);
        if (rt.mode.equals("dark_elf") && rt.activeUntil > RaceService.time(p)) {
            p.removeEffect(MobEffects.INVISIBILITY); rt.activeUntil = 0;
        }
        rt.repairUntil = 0;
    }
    public static void hurt(ServerPlayer p) { attacked(p); runtime(p).lastHurt = RaceService.time(p); }
    public static boolean silentSteps(Player p) {
        Runtime rt = RUNTIME.get(p.getUUID());
        return rt != null && rt.mode.equals("wood_elf") && p.level().getGameTime() < rt.activeUntil;
    }
    public static Vec3 safeDestination(ServerPlayer p, Vec3 move) {
        Vec3 start = p.position(), best = start;
        int steps = Math.max(1, (int)Math.ceil(move.length() / .10));
        for (int i = 1; i <= steps; i++) {
            Vec3 offset = move.scale(i/(double)steps), point = start.add(offset);
            AABB box = p.getBoundingBox().move(offset).deflate(.001);
            if (!p.level().hasChunkAt(net.minecraft.core.BlockPos.containing(point))
                || !p.level().getWorldBorder().isWithinBounds(box) || !p.level().noCollision(p, box)) break;
            best = point;
        }
        return best;
    }
    private static boolean sight(ServerPlayer p, Vec3 target) {
        return p.level().clip(new ClipContext(p.getEyePosition(), target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }
    private static LivingEntity beamTarget(ServerPlayer p, double range) {
        Vec3 start = p.getEyePosition(), end = start.add(p.getLookAngle().scale(range));
        LivingEntity best = null; double distance = range * range;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), x -> hostile(p,x))) {
            var hit = e.getBoundingBox().inflate(.12).clip(start,end);
            if (hit.isPresent() && hit.get().distanceToSqr(start) < distance && sight(p, hit.get())) {
                best = e; distance = hit.get().distanceToSqr(start);
            }
        }
        return best;
    }
    private static void breath(ServerPlayer p, String type) {
        boolean storm = type.equals("storm_dragonborn");
        double range = storm ? 8 : 6;
        Vec3 start = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity beam = storm ? beamTarget(p, range) : null;
        var targets = storm ? (beam == null ? List.<LivingEntity>of() : List.of(beam))
            : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), e -> hostile(p,e));
        for (LivingEntity e : targets) {
            Vec3 center = e.getBoundingBox().getCenter(), relative = center.subtract(start);
            if (relative.length() > range || relative.normalize().dot(look) < .80 || !sight(p, center)) continue;
            if (!e.hurt(p.damageSources().playerAttack(p), storm ? 8 : 6)) continue;
            switch(type) {
                case "fire_dragonborn" -> e.igniteForSeconds(3);
                case "frost_dragonborn" -> effect(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
                case "storm_dragonborn" -> e.knockback(.6, -look.x, -look.z);
                case "end_dragonborn" -> effect(e, MobEffects.WEAKNESS, 80, 0);
            }
        }
        var particle = switch(type) {
            case "fire_dragonborn" -> ParticleTypes.FLAME;
            case "frost_dragonborn" -> ParticleTypes.SNOWFLAKE;
            case "storm_dragonborn" -> ParticleTypes.ELECTRIC_SPARK;
            default -> ParticleTypes.DRAGON_BREATH;
        };
        for (double d=.4; d<range; d+=.35) {
            Vec3 at = start.add(look.scale(d));
            if (!sight(p, at)) break;
            p.serverLevel().sendParticles(particle, at.x, at.y, at.z, 3, .1, .1, .1, .01);
        }
    }
    public static void clearTransient(ServerPlayer p) {
        Runtime rt = RUNTIME.get(p.getUUID());
        if (rt != null && rt.mode.equals("dark_elf") && rt.activeUntil > RaceService.time(p)) p.removeEffect(MobEffects.INVISIBILITY);
        RUNTIME.remove(p.getUUID());
        for (String id : List.of("ability_speed","ability_mining","ability_attack","ability_knockback")) {
            for (var attribute : List.of(Attributes.MOVEMENT_SPEED,Attributes.BLOCK_BREAK_SPEED,Attributes.ATTACK_SPEED,Attributes.KNOCKBACK_RESISTANCE))
                RaceService.modifier(p, attribute, id, 0, AttributeModifier.Operation.ADD_VALUE);
        }
    }
    public static void logout(ServerPlayer p) { RUNTIME.remove(p.getUUID()); }
    private static void message(ServerPlayer p, String text) { p.displayClientMessage(Component.literal(text), true); }
}
