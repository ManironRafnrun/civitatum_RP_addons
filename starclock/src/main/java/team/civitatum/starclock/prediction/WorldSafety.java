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
        var bounds = entity.getBoundingBox().move(offset);
        if (!level.hasChunkAt(BlockPos.containing(bounds.minX, feet.y, bounds.minZ))
                || !level.hasChunkAt(BlockPos.containing(Math.nextDown(bounds.maxX), feet.y, Math.nextDown(bounds.maxZ)))
                || !level.noCollision(entity, bounds)) return false;
        for (int dy = 0; dy < Math.ceil(entity.getBbHeight()); dy++) {
            var state = level.getBlockState(pos.above(dy));
            if (state.is(net.minecraft.tags.BlockTags.FIRE)
                    || state.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                    || state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                    || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                    || state.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                    || state.is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                    || state.is(net.minecraft.world.level.block.Blocks.END_GATEWAY)
                    || state.is(net.minecraft.world.level.block.Blocks.COBWEB)) return false;
        }
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
