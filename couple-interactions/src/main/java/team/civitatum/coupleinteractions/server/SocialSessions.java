package team.civitatum.coupleinteractions.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import team.civitatum.coupleinteractions.*;
import team.civitatum.coupleinteractions.core.Interaction;
import team.civitatum.coupleinteractions.network.CoupleNetwork;

/** One server-owned session per player. Sessions never survive a disconnect or teleport. */
public final class SocialSessions {
    /** Kiss and hug end by themselves (3.5 and 6 seconds); resting lasts until a partner leaves. */
    private static final int KISS_TICKS = 70, HUG_TICKS = 120;
    /** The faces meet 1.5 seconds into the kiss: hearts and sound play then. */
    private static final int KISS_CONTACT_TICK = 30;
    /** Partners stand this far apart at normal scale: facing for a kiss or hug, side by side to rest. */
    private static final double FACING_GAP = 0.66, SIDE_GAP = 0.64;
    /** A partner that drifts 0.6 blocks from the placed position ends the session. */
    private static final double MAX_DRIFT_SQR = 0.6 * 0.6;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static long clock;
    private static boolean positioning;

    private static final class Session {
        final ServerPlayer a, b;
        final Interaction action;
        final long start;
        final int duration;
        final float yaw;
        final boolean mounted;
        final Vec3 first, second;
        boolean effect;

        Session(ServerPlayer a, ServerPlayer b, Interaction action, float yaw, boolean mounted, Vec3 first,
                Vec3 second) {
            this.a = a;
            this.b = b;
            this.action = action;
            this.yaw = yaw;
            this.mounted = mounted;
            this.first = first;
            this.second = second;
            start = clock;
            duration = action == Interaction.KISS ? KISS_TICKS : action == Interaction.HUG ? HUG_TICKS : 0;
        }

        CoupleNetwork.SocialState packet(boolean active) {
            return new CoupleNetwork.SocialState(a.getUUID(), b.getUUID(), action.ordinal(), active,
                (int) (clock - start), duration, yaw, mounted);
        }

        boolean broken() {
            if (a.isRemoved() || b.isRemoved() || !a.isAlive() || !b.isAlive() || a.level() != b.level()) return true;
            if (a.isSpectator() || b.isSpectator() || a.isSleeping() || b.isSleeping()) return true;
            if (a.isInWaterOrBubble() || b.isInWaterOrBubble() || a.isInLava() || b.isInLava()) return true;
            if (a.isFallFlying() || b.isFallFlying() || a.isShiftKeyDown() || b.isShiftKeyDown()) return true;
            if (duration > 0 && clock - start >= duration) return true;
            if (mounted) return !CarryHooks.pair(a, b) && !CarryHooks.pair(b, a);
            return a.isPassenger() || b.isPassenger() || a.position().distanceToSqr(first) > MAX_DRIFT_SQR
                || b.position().distanceToSqr(second) > MAX_DRIFT_SQR;
        }
    }

    public static boolean positioning() { return positioning; }
    public static boolean active(UUID id) { return ACTIVE.containsKey(id); }

    public static CoupleNetwork.SocialState state(UUID id) {
        var s = ACTIVE.get(id);
        return s == null ? null : s.packet(true);
    }

    public static boolean begin(ServerPlayer a, ServerPlayer b, Interaction action) {
        if (action != Interaction.KISS && action != Interaction.HUG && action != Interaction.REST) return false;
        if (CoupleServer.validate(a, b, action) != null) return false;
        if (active(a.getUUID()) || active(b.getUUID())) return false;
        // A kiss is the one interaction allowed while one partner carries the other.
        boolean mounted = CarryHooks.pair(a, b) || CarryHooks.pair(b, a);
        if (mounted && action != Interaction.KISS) return false;
        float facing = (float) Math.toDegrees(Math.atan2(b.getZ() - a.getZ(), b.getX() - a.getX())) - 90;
        float yaw = mounted ? a.yBodyRot : facing;
        double scale = (a.getScale() + b.getScale()) * .5;
        Vec3 first = a.position();
        Vec3 second = action == Interaction.REST
            ? first.add(CarryGeometry.local(yaw, -SIDE_GAP * scale, 0, 0))
            : first.add(CarryGeometry.local(yaw, 0, 0, FACING_GAP * scale));
        if (!mounted && (!safe(b, second) || !a.onGround() || !b.onGround())) return false;
        var s = new Session(a, b, action, yaw, mounted, first, second);
        ACTIVE.put(a.getUUID(), s);
        ACTIVE.put(b.getUUID(), s);
        a.stopUsingItem();
        b.stopUsingItem();
        a.setSprinting(false);
        b.setSprinting(false);
        if (!mounted) {
            place(a, first, yaw);
            place(b, second, action == Interaction.REST ? yaw : yaw + 180);
        }
        freeze(a, true);
        freeze(b, true);
        a.refreshDimensions();
        b.refreshDimensions();
        broadcast(s.packet(true));
        return true;
    }

    private static boolean safe(ServerPlayer p, Vec3 at) {
        var box = p.getDimensions(Pose.STANDING).makeBoundingBox(at);
        var level = p.serverLevel();
        return level.getWorldBorder().isWithinBounds(box) && level.noCollision(p, box)
            && level.getFluidState(BlockPos.containing(at)).isEmpty();
    }

    /** Our own move: {@link #positioning} tells the teleport hook it must not end the session. */
    private static void place(ServerPlayer p, Vec3 pos, float yaw) {
        positioning = true;
        try {
            p.connection.teleport(pos.x, pos.y, pos.z, yaw, 0);
            p.yBodyRot = p.yBodyRotO = yaw;
            p.yHeadRot = p.yHeadRotO = yaw;
        } finally {
            positioning = false;
        }
    }

    private static void freeze(ServerPlayer p, boolean enable) {
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(CoupleInteractions.id("social_pose"));
        if (enable) {
            speed.addTransientModifier(new AttributeModifier(CoupleInteractions.id("social_pose"), -1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    public static void end(UUID member) {
        var s = ACTIVE.remove(member);
        if (s == null) return;
        ACTIVE.remove(s.a.getUUID(), s);
        ACTIVE.remove(s.b.getUUID(), s);
        freeze(s.a, false);
        freeze(s.b, false);
        s.a.refreshDimensions();
        s.b.refreshDimensions();
        broadcast(s.packet(false));
    }

    public static void tick() {
        clock++;
        for (var s : new HashSet<>(ACTIVE.values())) {
            if (s.broken()) {
                end(s.a.getUUID());
                continue;
            }
            s.a.stopUsingItem();
            s.b.stopUsingItem();
            s.a.setSprinting(false);
            s.b.setSprinting(false);
            if (!s.mounted) {
                s.a.setDeltaMovement(Vec3.ZERO);
                s.b.setDeltaMovement(Vec3.ZERO);
            }
            if (s.action == Interaction.KISS && !s.effect && clock - s.start >= KISS_CONTACT_TICK) {
                s.effect = true;
                broadcast(new CoupleNetwork.KissEffect(s.a.getUUID(), s.b.getUUID()));
            }
        }
    }

    public static void tracking(ServerPlayer watcher, UUID member) {
        var state = state(member);
        if (state != null) CoupleServer.send(watcher, state);
    }

    /** To every player: ending the last session still needs to reach every observer. */
    private static void broadcast(CustomPacketPayload packet) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) for (var p : server.getPlayerList().getPlayers()) CoupleServer.send(p, packet);
    }

    public static void clear() {
        for (var s : new HashSet<>(ACTIVE.values())) end(s.a.getUUID());
        ACTIVE.clear();
        clock = 0;
    }

    private SocialSessions() {}
}
