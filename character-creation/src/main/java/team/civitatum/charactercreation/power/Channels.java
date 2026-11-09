package team.civitatum.charactercreation.power;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.RaceNetwork;
import team.civitatum.charactercreation.RaceService;
import team.civitatum.charactercreation.power.PlayerPowers.Channel;
import team.civitatum.charactercreation.power.PlayerPowers.ChannelKind;

/**
 * Powers that take time ("10b", rule 5): the wood elf's trance and the fire dragonborn's breath last while the key is
 * held, the Wanderer's path needs ten seconds of standing still. Moving, attacking or being hurt breaks a focus.
 */
public final class Channels {
    public static final int TRANCE_TICKS = 10 * 20;
    public static final int EXHALE_TICKS = 3 * 20;
    public static final int PATH_TICKS = 10 * 20;
    /** The breath burns every half second ("каждые 0,5 с наносит урон и поджигает"). */
    private static final int EXHALE_PULSE = 10;
    /**
     * Damage of one pulse. The old one-shot breath did 6; the held breath pulses six times, so 2 a pulse gives 12 for
     * a power that now works once a day.
     */
    private static final float EXHALE_DAMAGE = 2;
    private static final int EXHALE_FIRE_SECONDS = 3;
    private static final double EXHALE_RANGE = 6;
    /** cos of the cone's half angle: about 37°, the cone of the old breath. */
    private static final double EXHALE_CONE = .80;
    /** The Wanderer pays a heart ("цена — 1 сердце"). */
    private static final float PATH_PRICE = 2;
    /** How far the Wanderer may shift while focusing: a twentieth of a block, standing still. */
    private static final double STILL = .05;

    public static boolean isRunning(ServerPlayer p) {
        var s = PlayerPowers.peek(p);
        return s != null && s.channel != null;
    }

    public static boolean isRunning(ServerPlayer p, ChannelKind kind) {
        var s = PlayerPowers.peek(p);
        return s != null && s.channel != null && s.channel.kind() == kind;
    }

    public static void start(ServerPlayer p, ChannelKind kind, int ticks) {
        long now = RaceService.time(p);
        PlayerPowers.of(p).channel = new Channel(kind, now, now + ticks, p.position());
        switch (kind) {
            case TRANCE -> {
                modifier(p, Attributes.MOVEMENT_SPEED, -1);
                modifier(p, Attributes.JUMP_STRENGTH, -1);
            }
            case EXHALE -> modifier(p, Attributes.MOVEMENT_SPEED, -.5);
            case PATH -> {}
        }
    }

    public static void tick(ServerPlayer p) {
        var s = PlayerPowers.peek(p);
        if (s == null || s.channel == null) return;
        var c = s.channel;
        long now = RaceService.time(p);
        if (!p.isAlive()) {
            stop(p);
            return;
        }
        // The breath pulses at 0.5, 1.0 ... 3.0 s: not on the tick it starts (the key press and the player's tick
        // share it), and the last pulse on the tick it ends.
        if (c.kind() == ChannelKind.EXHALE) exhale(p, now > c.started() && (now - c.started()) % EXHALE_PULSE == 0);
        if (now >= c.until()) {
            finish(p, true);
            return;
        }
        switch (c.kind()) {
            case EXHALE -> {}
            case PATH -> {
                if (c.at().distanceToSqr(p.position()) > STILL * STILL || p.isUsingItem()) interrupt(p);
                else if ((c.until() - now) % 20 == 0)
                    message(p, Civitatum.message("power.path_progress", (c.until() - now) / 20));
            }
            case TRANCE -> {}
        }
    }

    /** The key was let go: a held power ends now. */
    public static void release(ServerPlayer p) {
        var s = PlayerPowers.peek(p);
        if (s != null && s.channel != null && s.channel.kind() != ChannelKind.PATH) finish(p, false);
    }

    /** The player attacked or was hurt. */
    public static void disturbed(ServerPlayer p, boolean attacked) {
        var s = PlayerPowers.peek(p);
        if (s == null || s.channel == null) return;
        switch (s.channel.kind()) {
            case PATH -> interrupt(p);
            // The trance takes the blows (+50% damage), but a blow given wakes it
            case TRANCE -> { if (attacked) finish(p, false); }
            case EXHALE -> {}
        }
    }

    /** Ends without a result and without a price: death, logout, a new character. */
    public static void stop(ServerPlayer p) {
        var s = PlayerPowers.peek(p);
        if (s != null) s.channel = null;
        modifier(p, Attributes.MOVEMENT_SPEED, 0);
        modifier(p, Attributes.JUMP_STRENGTH, 0);
    }

    private static void interrupt(ServerPlayer p) {
        stop(p);
        message(p, Civitatum.message("power.interrupted"));
    }

    private static void finish(ServerPlayer p, boolean complete) {
        var kind = PlayerPowers.of(p).channel.kind();
        stop(p);
        switch (kind) {
            case TRANCE -> {
                if (complete) {
                    Effects.give(p, ModEffects.TRANCE, 60 * 20, 0);
                    p.setRespawnPosition(p.level().dimension(), p.blockPosition(), p.getYRot(), true, false);
                    message(p, Civitatum.message("power.trance_done"));
                } else {
                    Effects.give(p, MobEffects.REGENERATION, 5 * 20, 0);
                }
            }
            case EXHALE -> Effects.give(p, ModEffects.HOARSE, 30 * 20, 0);
            case PATH -> path(p);
        }
    }

    private static void exhale(ServerPlayer p, boolean pulse) {
        Vec3 start = p.getEyePosition(), look = p.getLookAngle();
        for (double d = .4; d < EXHALE_RANGE; d += .5) {
            Vec3 at = start.add(look.scale(d));
            if (!Targets.isVisible(p, at)) break;
            p.serverLevel().sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 2, .15, .15, .15, .01);
        }
        if (!pulse) return;
        for (LivingEntity e : Targets.around(p, start, EXHALE_RANGE, e -> Targets.isHostile(p, e))) {
            Vec3 center = e.getBoundingBox().getCenter(), relative = center.subtract(start);
            if (relative.normalize().dot(look) < EXHALE_CONE || !Targets.isVisible(p, center)) continue;
            // A pulse every 10 ticks is exactly the cooldown a hit leaves; without this every other pulse was lost,
            // depending on whether the target ticked before or after the player.
            e.invulnerableTime = 0;
            if (e.hurt(p.damageSources().playerAttack(p), EXHALE_DAMAGE)) e.igniteForSeconds(EXHALE_FIRE_SECONDS);
        }
    }

    private static void path(ServerPlayer p) {
        var state = p.getData(Civitatum.STATE);
        var mark = state.mark();
        if (mark == null) return;
        if (!mark.dimension().equals(p.level().dimension().location().toString())) {
            message(p, Civitatum.message("power.path_other_world"));
            return;
        }
        p.teleportTo(mark.x(), mark.y(), mark.z());
        p.resetFallDistance();
        p.hurt(p.damageSources().magic(), PATH_PRICE);
        long now = RaceService.time(p);
        p.setData(Civitatum.STATE, state.withSignCooldown(now + SignPowers.cooldownTicks("wanderer")));
        RaceNetwork.sync(p);
    }

    private static void modifier(ServerPlayer p, Holder<Attribute> attribute, double amount) {
        RaceService.modifier(p, attribute, "channel_" + attribute.unwrapKey().orElseThrow().location().getPath()
            .replace('.', '_'), amount, Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void message(ServerPlayer p, Component text) {
        p.displayClientMessage(text, true);
    }

    private Channels() {}
}
