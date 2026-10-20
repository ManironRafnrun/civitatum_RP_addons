package team.civitatum.starclock.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import team.civitatum.starclock.ritual.RitualRules;

public final class StarClockData extends SavedData {
    private long lastRitual = -1L;

    public static StarClockData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StarClockData::new, StarClockData::load), "civitatum_starclock");
    }

    private static StarClockData load(CompoundTag tag, HolderLookup.Provider registries) {
        StarClockData data = new StarClockData();
        data.lastRitual = tag.contains("LastRitual") ? tag.getLong("LastRitual") : -1L;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 1);
        tag.putLong("LastRitual", lastRitual);
        return tag;
    }

    public long remainingCooldown(long gameTime) {
        return RitualRules.remainingCooldown(lastRitual, gameTime);
    }

    public void recordRitual(long gameTime) {
        lastRitual = gameTime;
        setDirty();
    }
}
