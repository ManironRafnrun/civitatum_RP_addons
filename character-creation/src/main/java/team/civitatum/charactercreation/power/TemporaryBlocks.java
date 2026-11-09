package team.civitatum.charactercreation.power;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import team.civitatum.charactercreation.Civitatum;

/**
 * Blocks a power puts into the world for a while: the dwarf's chain, roots, cobwebs, ice, the goblin's bell, the
 * Hearth's campfire. They are saved with the world, so a restart in the middle still takes them away, and they drop
 * nothing when broken: otherwise a daily power would be a free bell or campfire.
 */
public final class TemporaryBlocks extends SavedData {
    private static final String NAME = Civitatum.ID + "_temporary_blocks";

    /** A placed block and what to put back; {@code until} is overworld game time. */
    private record Entry(ResourceKey<Level> dimension, BlockPos pos, BlockState placed, BlockState original,
                         long until) {}

    private final List<Entry> entries = new ArrayList<>();

    private static TemporaryBlocks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(TemporaryBlocks::new, TemporaryBlocks::load, null), NAME);
    }

    /**
     * Puts {@code state} at {@code pos} for {@code ticks} if the place is free (air or a replaceable plant), and says
     * whether it was put.
     */
    public static boolean place(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        var original = level.getBlockState(pos);
        boolean water = !original.getFluidState().isEmpty();
        // Only ice goes onto water: a chain or roots there would leave the water flowing around them.
        if (!original.canBeReplaced() || water != state.is(Blocks.FROSTED_ICE) || isTemporary(level, pos)) return false;
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) return false;
        var data = get(level.getServer());
        data.entries.add(new Entry(level.dimension(), pos.immutable(), level.getBlockState(pos), original,
            level.getServer().overworld().getGameTime() + ticks));
        data.setDirty();
        return true;
    }

    /**
     * Whether the power's own block still stands here. An entry whose block is gone without its drops being asked
     * (a command, a piston, another mod) is stale: it no longer keeps the place from a new block of a power.
     */
    public static boolean isTemporary(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return false;
        var data = get(server.getServer());
        for (var e : data.entries) {
            if (!e.pos.equals(pos) || !e.dimension.equals(level.dimension())) continue;
            if (level.getBlockState(pos).getBlock() == e.placed.getBlock()) return true;
            data.entries.remove(e);
            data.setDirty();
            return false;
        }
        return false;
    }

    /** Takes back the blocks whose time is over, in loaded chunks; the rest wait until their chunk loads. */
    public static void tick(MinecraftServer server) {
        var data = get(server);
        if (data.entries.isEmpty()) return;
        long now = server.overworld().getGameTime();
        boolean changed = data.entries.removeIf(e -> {
            if (e.until > now) return false;
            ServerLevel level = server.getLevel(e.dimension);
            if (level == null) return true;
            if (!level.isLoaded(e.pos)) return false;
            restore(level, e);
            return true;
        });
        if (changed) data.setDirty();
    }

    /** The block was broken or blown up: it is gone without drops, and its entry with it. */
    public static void forget(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        var data = get(server.getServer());
        if (data.entries.removeIf(e -> e.pos.equals(pos) && e.dimension.equals(level.dimension()))) data.setDirty();
    }

    /** Puts the original block back only over the power's own block: a player may have built there since. */
    private static void restore(ServerLevel level, Entry e) {
        var now = level.getBlockState(e.pos);
        if (now.getBlock() == e.placed.getBlock()) level.setBlock(e.pos, e.original, Block.UPDATE_ALL);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var e : entries) {
            var entry = new CompoundTag();
            entry.putString("dimension", e.dimension.location().toString());
            entry.putLong("pos", e.pos.asLong());
            entry.put("placed", NbtUtils.writeBlockState(e.placed));
            entry.put("original", NbtUtils.writeBlockState(e.original));
            entry.putLong("until", e.until);
            list.add(entry);
        }
        tag.put("entries", list);
        return tag;
    }

    private static TemporaryBlocks load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new TemporaryBlocks();
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) t;
            var dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(entry.getString("dimension")));
            data.entries.add(new Entry(dimension, BlockPos.of(entry.getLong("pos")),
                NbtUtils.readBlockState(blocks, entry.getCompound("placed")),
                NbtUtils.readBlockState(blocks, entry.getCompound("original")), entry.getLong("until")));
        }
        return data;
    }
}
