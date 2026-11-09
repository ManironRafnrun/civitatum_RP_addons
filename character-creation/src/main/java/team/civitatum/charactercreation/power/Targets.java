package team.civitatum.charactercreation.power;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import team.civitatum.charactercreation.CompatPatches;

/** Who a power may touch, and where the player looks. */
public final class Targets {
    /** Steps of the collision check of a move: a tenth of a block, finer than any full block. */
    private static final double MOVE_STEP = .10;

    /** The player, an ally, an FTB Teams mate, or a pet of one of them. */
    public static boolean isFriendly(Player p, LivingEntity target) {
        if (p == target || p.isAlliedTo(target)) return true;
        if (target instanceof TamableAnimal pet && pet.isOwnedBy(p)) return true;
        if (target instanceof Player q && CompatPatches.sameTeam(p, q)) return true;
        if (target instanceof TamableAnimal pet && pet.getOwner() instanceof Player owner)
            return p.isAlliedTo(owner) || CompatPatches.sameTeam(p, owner);
        return false;
    }

    /** A monster, a mob that hunts the player, or a player that may be hurt under the server's PvP rules. */
    public static boolean isHostile(ServerPlayer p, LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target.isInvulnerable() || isFriendly(p, target)) return false;
        if (target instanceof Player q) return p.server.isPvpAllowed() && p.canHarmPlayer(q) && !q.isCreative();
        return target instanceof Enemy || (target instanceof Mob mob && mob.getTarget() == p);
    }

    /** Living things within {@code radius} of the player, nearest first, that pass {@code filter}. */
    public static List<LivingEntity> around(ServerPlayer p, Vec3 center, double radius,
                                            java.util.function.Predicate<LivingEntity> filter) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && e.distanceToSqr(center) <= radius * radius && filter.test(e))
            .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(center))).toList();
    }

    /** The block the player looks at within {@code range}, or {@code null}. */
    public static BlockHitResult block(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        var hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /**
     * The ground where a zone of a power stands: the block looked at within {@code range}, or the ground under the
     * player when the look reaches nothing. The position is the air block above the ground.
     */
    public static BlockPos ground(ServerPlayer p, double range) {
        var hit = block(p, range);
        BlockPos start = hit == null ? p.blockPosition() : hit.getBlockPos().relative(hit.getDirection());
        var level = p.level();
        BlockPos pos = start;
        for (int i = 0; i < 4 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); i++)
            pos = pos.below();
        return pos;
    }

    /** The living thing on the line of sight within {@code range} that passes {@code filter}, or {@code null}. */
    public static LivingEntity looked(ServerPlayer p, double range, java.util.function.Predicate<LivingEntity> filter) {
        Vec3 start = p.getEyePosition(), end = start.add(p.getLookAngle().scale(range));
        LivingEntity best = null;
        double distance = range * range;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(range), x -> x != p && x.isAlive() && filter.test(x))) {
            var hit = e.getBoundingBox().inflate(.3).clip(start, end);
            if (hit.isPresent() && hit.get().distanceToSqr(start) < distance && isVisible(p, hit.get())) {
                best = e;
                distance = hit.get().distanceToSqr(start);
            }
        }
        return best;
    }

    public static boolean isVisible(ServerPlayer p, Vec3 target) {
        return p.level().clip(new ClipContext(p.getEyePosition(), target, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }

    /** The furthest point of {@code move} the player's box reaches without entering a block or the world border. */
    public static Vec3 reach(ServerPlayer p, Vec3 move) {
        Vec3 start = p.position(), best = start;
        int steps = Math.max(1, (int) Math.ceil(move.length() / MOVE_STEP));
        for (int i = 1; i <= steps; i++) {
            Vec3 offset = move.scale(i / (double) steps), point = start.add(offset);
            AABB box = p.getBoundingBox().move(offset).deflate(.001);
            if (!p.level().hasChunkAt(BlockPos.containing(point))
                || !p.level().getWorldBorder().isWithinBounds(box) || !p.level().noCollision(p, box)) break;
            best = point;
        }
        return best;
    }

    /**
     * The horizontal unit vector the player faces. Taken from the yaw, not from the look vector: looking straight up
     * or down, the look vector has no horizontal part to normalize.
     */
    public static Vec3 facing(Player p) {
        return Vec3.directionFromRotation(0, p.getYRot());
    }

    private Targets() {}
}
