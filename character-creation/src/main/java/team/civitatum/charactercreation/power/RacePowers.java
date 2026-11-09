package team.civitatum.charactercreation.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.RaceDefinition;
import team.civitatum.charactercreation.RaceNetwork;
import team.civitatum.charactercreation.RaceService;
import team.civitatum.charactercreation.power.PlayerPowers.ChannelKind;

/** The racial powers of "10b", part I: one key, once per game day, paid by the effect that follows. */
public final class RacePowers {
    /** Presses closer than this are one press: a held key repeats, and the HUD button may send one more. */
    private static final int DEBOUNCE_TICKS = 5;

    private static final int NABAT_RADIUS = 16;
    private static final int PHANTOM_RADIUS = 32;
    private static final int SEAL_REACH = 5;
    private static final int SEAL_TICKS = 10 * 60 * 20;
    private static final int ROOTS_REACH = 8;
    /** Half of the 7×7 zone of the roots, from the centre block. */
    private static final int ROOTS_HALF = 3;
    private static final int ROOTS_BLOCK_TICKS = 10 * 20;
    private static final int BELL_TICKS = 60 * 20;
    private static final int CONSTELLATION_RADIUS = 48;
    /** How far mobs that hunt the night elf are made to lose it at once. */
    private static final int SHADOW_RADIUS = 64;
    private static final int KNOT_RANGE = 12;
    private static final int KNOT_TARGETS = 3;
    /** The cobweb stays for both halves of the knot: 4 s rooted and 4 s slowed. */
    private static final int KNOT_WEB_TICKS = 8 * 20;
    private static final int RIME_REACH = 8;
    /** Half of the 5×5 zone of the rime. */
    private static final int RIME_HALF = 2;
    private static final int RIME_TICKS = 5 * 20;
    private static final int LIGHTNING_RANGE = 12;
    private static final int STEP_RANGE = 16;
    private static final int ANCESTOR_RADIUS = 32;
    private static final int RAM_RANGE = 8;
    /** The knockback of the ram: as strong as a Knockback II blow. */
    private static final double RAM_KNOCKBACK = 1.2;

    public static void activate(ServerPlayer p) {
        RaceDefinition r = RaceService.race(p);
        if (r == null || !r.hasAbility() || !p.isAlive() || p.isSpectator()) return;
        long now = RaceService.time(p);
        var powers = PlayerPowers.of(p);
        if (now - powers.lastRequest < DEBOUNCE_TICKS) return;
        powers.lastRequest = now;
        if (Channels.isRunning(p)) return;
        var state = p.getData(Civitatum.STATE);
        if (state.cooldownUntil() > now) {
            message(p, Civitatum.message("ability_cooldown", (state.cooldownUntil() - now + 19) / 20));
            return;
        }
        if (!use(p, r.id)) return;
        p.setData(Civitatum.STATE, state.withCooldown(now + r.cooldown * 20L));
        RaceNetwork.sync(p);
        message(p, Component.translatable(r.key("ability_name")));
    }

    /** The key was let go: the held powers end. */
    public static void release(ServerPlayer p) {
        Channels.release(p);
    }

    private static boolean use(ServerPlayer p, String race) {
        return switch (race) {
            case "human" -> nabat(p);
            case "dwarf" -> seal(p);
            case "orc" -> self(p, ModEffects.RAGE, 30);
            case "firbolg" -> roots(p);
            case "goblin" -> bell(p);
            case "forged" -> self(p, ModEffects.REPLACEMENT, 30);
            case "hobbit" -> breakfast(p);
            case "astral_elf" -> constellation(p);
            case "night_elf" -> shadow(p);
            case "dark_elf" -> knot(p);
            case "wood_elf" -> channel(p, ChannelKind.TRANCE, Channels.TRANCE_TICKS);
            case "fire_dragonborn" -> channel(p, ChannelKind.EXHALE, Channels.EXHALE_TICKS);
            case "frost_dragonborn" -> rime(p);
            case "storm_dragonborn" -> lightning(p);
            case "end_dragonborn" -> step(p);
            case "orthosian" -> ancestorName(p);
            case "jahar" -> mask(p);
            case "forest_algnor", "city_algnor" -> ram(p);
            default -> false;
        };
    }

    private static boolean self(ServerPlayer p, Holder<MobEffect> effect, int seconds) {
        Effects.give(p, effect, seconds * 20, 0);
        return true;
    }

    private static boolean channel(ServerPlayer p, ChannelKind kind, int ticks) {
        Channels.start(p, kind, ticks);
        return true;
    }

    private static boolean nabat(ServerPlayer p) {
        var level = p.serverLevel();
        for (var e : Targets.around(p, p.position(), NABAT_RADIUS, e -> true)) {
            if (e instanceof ServerPlayer q && Targets.isFriendly(p, q)) Effects.give(q, ModEffects.NABAT, 60 * 20, 0);
            if (e.isSleeping()) {
                if (e instanceof ServerPlayer q) q.stopSleepInBed(true, true);
                else e.stopSleeping();
            }
        }
        for (var phantom : level.getEntitiesOfClass(Phantom.class, p.getBoundingBox().inflate(PHANTOM_RADIUS)))
            phantom.discard();
        level.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2, 1);
        return true;
    }

    private static boolean seal(ServerPlayer p) {
        var hit = Targets.block(p, SEAL_REACH);
        if (hit == null) return fail(p, "power.no_block");
        BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
        if (!TemporaryBlocks.place(p.serverLevel(), pos, Blocks.CHAIN.defaultBlockState(), SEAL_TICKS))
            return fail(p, "no_room");
        Zones.add(Zones.Kind.SEAL, p, Vec3.atCenterOf(pos), SEAL_TICKS);
        p.serverLevel().playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1, 1);
        return true;
    }

    private static boolean roots(ServerPlayer p) {
        var level = p.serverLevel();
        BlockPos center = Targets.ground(p, ROOTS_REACH);
        for (int dx = -ROOTS_HALF; dx <= ROOTS_HALF; dx++)
            for (int dz = -ROOTS_HALF; dz <= ROOTS_HALF; dz++) {
                // A wall around the zone: the roots close the way out, the rooted stand inside.
                if (Math.abs(dx) != ROOTS_HALF && Math.abs(dz) != ROOTS_HALF) continue;
                for (int dy = 0; dy < 2; dy++)
                    TemporaryBlocks.place(level, center.offset(dx, dy, dz), Blocks.MANGROVE_ROOTS.defaultBlockState(),
                        ROOTS_BLOCK_TICKS);
            }
        var zone = new AABB(center).inflate(ROOTS_HALF + .5, 2, ROOTS_HALF + .5);
        for (var e : level.getEntitiesOfClass(LivingEntity.class, zone, e -> Targets.isHostile(p, e)))
            Effects.give(e, ModEffects.ROOTS, 5 * 20, 0);
        level.playSound(null, center, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.PLAYERS, 1.5f, .8f);
        return true;
    }

    private static boolean bell(ServerPlayer p) {
        var level = p.serverLevel();
        BlockPos pos = p.blockPosition();
        if (!TemporaryBlocks.place(level, pos, Blocks.BELL.defaultBlockState(), BELL_TICKS)
            && !TemporaryBlocks.place(level, pos = pos.above(), Blocks.BELL.defaultBlockState(), BELL_TICKS))
            return fail(p, "no_room");
        Zones.add(Zones.Kind.BELL, p, Vec3.atCenterOf(pos), BELL_TICKS);
        return true;
    }

    private static boolean breakfast(ServerPlayer p) {
        p.getFoodData().setFoodLevel(20);
        Effects.give(p, ModEffects.BREAKFAST, 60 * 20, 0);
        return true;
    }

    private static boolean constellation(ServerPlayer p) {
        for (var e : Targets.around(p, p.position(), CONSTELLATION_RADIUS, e -> e != p))
            Effects.give(e, MobEffects.GLOWING, 30 * 20, 0);
        return true;
    }

    private static boolean shadow(ServerPlayer p) {
        Effects.give(p, ModEffects.SHADOW, 60 * 20, 0);
        for (var mob : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(SHADOW_RADIUS),
                m -> m.getTarget() == p)) mob.setTarget(null);
        return true;
    }

    private static boolean knot(ServerPlayer p) {
        var targets = Targets.around(p, p.position(), KNOT_RANGE,
            e -> Targets.isHostile(p, e) && Targets.isVisible(p, e.getBoundingBox().getCenter()));
        if (targets.isEmpty()) return fail(p, "power.no_target");
        for (var e : targets.subList(0, Math.min(KNOT_TARGETS, targets.size()))) {
            Effects.give(e, ModEffects.KNOT, 4 * 20, 0);
            TemporaryBlocks.place(p.serverLevel(), e.blockPosition(), Blocks.COBWEB.defaultBlockState(),
                KNOT_WEB_TICKS);
        }
        return true;
    }

    private static boolean rime(ServerPlayer p) {
        var level = p.serverLevel();
        BlockPos center = Targets.ground(p, RIME_REACH);
        for (int dx = -RIME_HALF; dx <= RIME_HALF; dx++)
            for (int dz = -RIME_HALF; dz <= RIME_HALF; dz++)
                for (int dy = -1; dy <= 0; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (level.getFluidState(pos).isSource() && level.getBlockState(pos.above()).isAir())
                        TemporaryBlocks.place(level, pos, Blocks.FROSTED_ICE.defaultBlockState(), RIME_TICKS);
                }
        var zone = new AABB(center).inflate(RIME_HALF + .5, 2, RIME_HALF + .5);
        for (var e : level.getEntitiesOfClass(LivingEntity.class, zone, e -> Targets.isHostile(p, e)))
            Effects.give(e, ModEffects.RIME, RIME_TICKS, 0);
        level.playSound(null, center, SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.5f, .6f);
        return true;
    }

    private static boolean lightning(ServerPlayer p) {
        var level = p.serverLevel();
        Vec3 at;
        var target = Targets.looked(p, LIGHTNING_RANGE, e -> Targets.isHostile(p, e));
        var hit = Targets.block(p, LIGHTNING_RANGE);
        if (target != null) at = target.position();
        else if (hit != null) at = Vec3.atBottomCenterOf(hit.getBlockPos().above());
        else return fail(p, "power.no_target");
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return false;
        bolt.moveTo(at);
        bolt.setCause(p);
        level.addFreshEntity(bolt);
        return true;
    }

    private static boolean step(ServerPlayer p) {
        if (p.isInWaterOrBubble()) return fail(p, "power.step_water");
        Vec3 to = Targets.reach(p, p.getLookAngle().scale(STEP_RANGE));
        if (to.distanceToSqr(p.position()) < .09) return fail(p, "no_room");
        p.teleportTo(to.x, to.y, to.z);
        p.resetFallDistance();
        Effects.give(p, ModEffects.STEP, 5 * 20, 0);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1);
        return true;
    }

    private static boolean ancestorName(ServerPlayer p) {
        Effects.give(p, ModEffects.ANCESTOR_NAME, 30 * 20, 0);
        for (var mob : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(ANCESTOR_RADIUS),
                m -> m.isAlive() && !Targets.isFriendly(p, m)
                    && m.distanceToSqr(p) <= ANCESTOR_RADIUS * ANCESTOR_RADIUS))
            mob.setTarget(p);
        return true;
    }

    private static boolean mask(ServerPlayer p) {
        Effects.give(p, ModEffects.MASK, 15 * 20, 0);
        p.clearFire();
        return true;
    }

    private static boolean ram(ServerPlayer p) {
        Vec3 dir = Targets.facing(p), from = p.position();
        Vec3 to = Targets.reach(p, dir.scale(RAM_RANGE));
        if (to.distanceToSqr(from) < .09) return fail(p, "no_room");
        var swept = p.getBoundingBox().minmax(p.getBoundingBox().move(to.subtract(from))).inflate(.5);
        for (var e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, swept, e -> Targets.isHostile(p, e))) {
            e.knockback(RAM_KNOCKBACK, -dir.x, -dir.z);
            e.hurtMarked = true;
            Effects.give(e, ModEffects.RAM, 2 * 20, 0);
        }
        p.teleportTo(to.x, to.y, to.z);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.GOAT_RAM_IMPACT, SoundSource.PLAYERS, 1, 1);
        return true;
    }

    private static boolean fail(ServerPlayer p, String key) {
        message(p, Civitatum.message(key));
        return false;
    }

    private static void message(ServerPlayer p, Component text) { p.displayClientMessage(text, true); }

    private RacePowers() {}
}
