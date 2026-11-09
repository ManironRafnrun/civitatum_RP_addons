package team.civitatum.charactercreation;

import java.nio.file.*;
import java.util.*;
import net.neoforged.fml.loading.FMLPaths;

public final class ModConfig {
    public boolean femaleGenderAdapter = true;
    public boolean algnorBootsAllowed = false;
    public boolean ftbTeamsPatch = true;
    public boolean koboldsPatch = true;
    public boolean automatonPatch = true;
    public int koboldPairCooldownSeconds = 1200;
    public List<String> dragonHunterEntityIds = new ArrayList<>();
    public List<String> automatonNamespaces = new ArrayList<>(List.of("mebahelcreaturesdwarven"));
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
            INSTANCE.koboldPairCooldownSeconds = Math.max(0, INSTANCE.koboldPairCooldownSeconds);
            // "key": null in the file would otherwise fail on every use of the setting
            if (INSTANCE.dragonHunterEntityIds == null) INSTANCE.dragonHunterEntityIds = new ArrayList<>();
            if (INSTANCE.automatonNamespaces == null) INSTANCE.automatonNamespaces = new ArrayList<>();
        } catch (Exception e) { Civitatum.LOGGER.error("Cannot read civitatum-races.json; using defaults", e); }
    }
}
