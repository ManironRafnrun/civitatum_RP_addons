package team.civitatum.starclock.data;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import team.civitatum.starclock.CivitatumStarclock;
import team.civitatum.starclock.config.StarClockConfig;

public final class StarClockData extends SavedData {
    private static final Gson JSON = new Gson();
    private final Map<UUID, RitualCooldown> cooldowns = new LinkedHashMap<>();
    private final Map<UUID, ReadingSeed> readingSeeds = new LinkedHashMap<>();
    private final Map<UUID, PendingPrediction> pending = new LinkedHashMap<>();
    private final Map<UUID, TimedSpawn> spawns = new LinkedHashMap<>();
    private final Map<String, TimedBlock> blocks = new LinkedHashMap<>();
    private final List<PeaceZone> zones = new ArrayList<>();
    private final Map<UUID, TowerKeyState> towerKeys = new LinkedHashMap<>();
    private final List<String> quarantine = new ArrayList<>();

    public static StarClockData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StarClockData::new, StarClockData::load), "civitatum_starclock");
    }

    public static StarClockData load(CompoundTag tag, HolderLookup.Provider registries) {
        StarClockData data = new StarClockData();
        // Прежние общие кулдаун и выбор не принадлежат никакому игроку и не переносятся: они просто заканчиваются.
        data.read(tag, "Cooldowns", RitualCooldown.class).forEach(value -> data.cooldowns.put(value.player(), value));
        data.read(tag, "ReadingSeeds", ReadingSeed.class)
                .forEach(value -> data.readingSeeds.put(value.player(), value));
        data.read(tag, "Pending", PendingPrediction.class).forEach(value -> data.pending.put(value.id(), value));
        data.read(tag, "Spawns", TimedSpawn.class).forEach(value -> data.spawns.put(value.entity(), value));
        data.read(tag, "Blocks", TimedBlock.class).forEach(value -> data.blocks.put(value.key(), value));
        data.zones.addAll(data.read(tag, "Zones", PeaceZone.class));
        data.read(tag, "TowerKeys", TowerKeyState.class).forEach(value -> data.towerKeys.put(value.player(), value));
        var quarantined = tag.getList("Quarantine", Tag.TAG_STRING);
        for (int i = 0; i < quarantined.size(); i++) data.quarantine.add(quarantined.getString(i));
        return data;
    }

    private <T> List<T> read(CompoundTag tag, String key, Class<T> type) {
        List<T> values = new ArrayList<>();
        var list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String raw = list.getString(i);
            try {
                T value = JSON.fromJson(raw, type);
                if (value == null) throw new IllegalArgumentException("Пустая запись");
                values.add(value);
            } catch (RuntimeException error) {
                quarantine.add(key + ": " + raw);
                CivitatumStarclock.LOG.error("Повреждённая запись Starclock {}[{}] сохранена отдельно", key, i, error);
            }
        }
        return values;
    }

    private static ListTag write(Iterable<?> values) {
        var list = new ListTag();
        for (Object value : values) list.add(StringTag.valueOf(JSON.toJson(value)));
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 5);
        tag.put("Cooldowns", write(cooldowns.values()));
        tag.put("ReadingSeeds", write(readingSeeds.values()));
        tag.put("Pending", write(pending.values()));
        tag.put("Spawns", write(spawns.values()));
        tag.put("Blocks", write(blocks.values()));
        tag.put("Zones", write(zones));
        tag.put("TowerKeys", write(towerKeys.values()));
        var damaged = new ListTag();
        quarantine.forEach(value -> damaged.add(StringTag.valueOf(value)));
        tag.put("Quarantine", damaged);
        return tag;
    }

    // До завершения обряда у игрока сохраняется один выбор, включая перезапуски.
    public long readingSeed(UUID player) {
        var seed = readingSeeds.get(player);
        if (seed == null) {
            seed = new ReadingSeed(player, UUID.randomUUID().getMostSignificantBits());
            readingSeeds.put(player, seed);
            setDirty();
        }
        return seed.seed();
    }

    public void advanceCooldowns(long dayTime) {
        var iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var advanced = entry.getValue().advance(dayTime);
            if (advanced.equals(entry.getValue())) continue;
            if (advanced.remaining(cooldownTicks()) > 0) entry.setValue(advanced);
            else iterator.remove();
            setDirty();
        }
    }

    public long remainingCooldown(UUID player, long dayTime) {
        advanceCooldowns(dayTime);
        var cooldown = cooldowns.get(player);
        return cooldown == null ? 0 : cooldown.remaining(cooldownTicks());
    }

    private static long cooldownTicks() {
        return StarClockConfig.COOLDOWN_DAYS.get() * RitualCooldown.DAY_TICKS;
    }

    public void schedule(long dayTime, PendingPrediction prediction) {
        if (pending.containsKey(prediction.id())) throw new IllegalArgumentException("Повтор предсказания");
        pending.put(prediction.id(), prediction);
        cooldowns.put(prediction.player(), RitualCooldown.start(prediction.player(), dayTime));
        readingSeeds.remove(prediction.player());
        setDirty();
    }

    public List<PendingPrediction> pending() {
        return List.copyOf(pending.values());
    }

    public void update(PendingPrediction prediction) {
        pending.put(prediction.id(), prediction);
        setDirty();
    }

    public void removePrediction(UUID id) {
        if (pending.remove(id) != null) setDirty();
    }

    public boolean hasPrediction(UUID id) {
        return pending.containsKey(id);
    }

    public int cancelPredictions(Predicate<UUID> players) {
        int before = pending.size();
        if (pending.values().removeIf(prediction -> players.test(prediction.player()))) setDirty();
        return before - pending.size();
    }

    public void addSpawn(TimedSpawn spawn) {
        spawns.put(spawn.entity(), spawn);
        setDirty();
    }

    public List<TimedSpawn> spawns() {
        return List.copyOf(spawns.values());
    }

    public void removeSpawn(UUID id) {
        if (spawns.remove(id) != null) setDirty();
    }

    public void addBlock(TimedBlock block) {
        blocks.put(block.key(), block);
        setDirty();
    }

    public List<TimedBlock> blocks() {
        return List.copyOf(blocks.values());
    }

    public void removeBlock(String key) {
        if (blocks.remove(key) != null) setDirty();
    }

    public void addZone(PeaceZone zone) {
        zones.add(zone);
        setDirty();
    }

    public void expireZones(long now) {
        if (zones.removeIf(zone -> now >= zone.expiresAt())) setDirty();
    }

    public boolean isPeaceful(String dimension, double x, double y, double z, long now) {
        return zones.stream().anyMatch(zone -> zone.contains(dimension, x, y, z, now));
    }


    public int towerKeyCharges(UUID player) {
        var state = towerKeys.get(player);
        return state == null ? 0 : state.charges();
    }

    public void grantTowerKey(UUID player) {
        towerKeys.put(player, new TowerKeyState(player, TowerKeyState.MAX_CHARGES));
        setDirty();
    }

    public void restoreLegacyTowerKey(UUID player, int remaining) {
        int charges = Math.clamp(remaining, 0, TowerKeyState.MAX_CHARGES);
        if (charges > towerKeyCharges(player)) {
            towerKeys.put(player, new TowerKeyState(player, charges));
            setDirty();
        }
    }

    public boolean consumeTowerKey(UUID player) {
        var state = towerKeys.get(player);
        if (state == null || state.charges() == 0) return false;
        state = state.consume();
        if (state.charges() == 0) towerKeys.remove(player);
        else towerKeys.put(player, state);
        setDirty();
        return true;
    }

    public int resetCooldowns(long dayTime, Predicate<UUID> players) {
        advanceCooldowns(dayTime);
        int before = cooldowns.size();
        if (cooldowns.keySet().removeIf(players)) setDirty();
        return before - cooldowns.size();
    }
}
