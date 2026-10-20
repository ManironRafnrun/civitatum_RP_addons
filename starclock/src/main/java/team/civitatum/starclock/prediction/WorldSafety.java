package team.civitatum.starclock.prediction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class WorldSafety {
    public static boolean free(ServerLevel level, Entity entity, Vec3 feet, boolean requireFloor) {
        BlockPos pos = BlockPos.containing(feet);
        if (pos.getY() < level.getMinBuildHeight() + 1 || pos.getY() + entity.getBbHeight() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)) return false;
        var offset = feet.subtract(entity.position());
        if (!level.noCollision(entity, entity.getBoundingBox().move(offset))) return false;
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) return false;
        if (!requireFloor) return true;
        var floor = level.getBlockState(pos.below());
        return floor.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                && !floor.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                && !floor.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                && !floor.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE);
    }

    public static Vec3 nearby(ServerLevel level, Entity entity, Vec3 wanted, int radius) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                        Vec3 feet = new Vec3(Math.floor(wanted.x) + dx + 0.5,
                                Math.floor(wanted.y) + dy, Math.floor(wanted.z) + dz + 0.5);
                        if (free(level, entity, feet, true)) return feet;
                    }
                }
            }
        }
        return null;
    }

    private WorldSafety() {}
}
