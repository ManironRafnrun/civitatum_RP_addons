package team.civitatum.charactercreation;

import java.nio.file.*;
import java.util.*;
import net.neoforged.fml.loading.FMLPaths;

public final class ModConfig {
    public boolean femaleGenderAdapter = true;
    public boolean algnorBootsAllowed = false;
    public boolean forgedMetalPatches = true;
    public boolean ftbTeamsPatch = true;
    public boolean arsNouveauPatch = true;
    public boolean koboldsPatch = true;
    public boolean automatonPatch = true;
    public int jaharHealthFloor = 4;
    public int netheriteEffectSeconds = 20;
    public int metalUseTicks = 32;
    public int koboldPairCooldownSeconds = 1200;
    public List<String> dragonHunterEntityIds = new ArrayList<>();
    public List<String> automatonNamespaces = new ArrayList<>(List.of("mebahelcreaturesdwarven"));
    public List<String> metalBlacklist = new ArrayList<>();
    public Map<String, MetalFood> metalOverrides = new LinkedHashMap<>();
    public record MetalFood(int food, float saturation) {}
    public static ModConfig INSTANCE = new ModConfig();
    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("civitatum-races.json");
        try {
            if (Files.exists(path)) {
                ModConfig parsed = Civitatum.GSON.fromJson(Files.readString(path), ModConfig.class);
                if (parsed != null) INSTANCE = parsed;
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, Civitatum.GSON.toJson(INSTANCE));
            }
            INSTANCE.jaharHealthFloor = Math.max(2, Math.min(20, INSTANCE.jaharHealthFloor));
            INSTANCE.metalUseTicks = Math.max(16, Math.min(100, INSTANCE.metalUseTicks));
            INSTANCE.netheriteEffectSeconds = Math.max(1, Math.min(300, INSTANCE.netheriteEffectSeconds));
        } catch (Exception e) { Civitatum.LOGGER.error("Cannot read civitatum-races.json; using defaults", e); }
    }
}
