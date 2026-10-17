package team.civitatum.charactercreation.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** What every generator shares: where the module's input and resources are, reading metadata, replacing output. */
final class Generation {
    /** Generator input: source PNGs and metadata. */
    static final Path INPUT = Path.of("input");
    /** Character textures the mod loads. */
    static final Path TEXTURES = Path.of("src/main/resources/assets/civitatum/textures/character");
    /** Catalogs the mod reads: which items exist, their colour fields and texture folders. */
    static final Path CATALOGS = Path.of("src/main/resources/appearance");

    private Generation() {
    }

    /**
     * Fails early when started from the wrong folder: generators read and write relative to the
     * {@code character-creation} module folder (the IDE's default working directory, set by the Gradle tasks too).
     */
    static void checkWorkingDirectory() {
        if (!Files.isDirectory(INPUT) || !Files.isDirectory(Path.of("src/main/resources"))) {
            throw new IllegalStateException("Run from the character-creation module folder (working directory is "
                    + Path.of("").toAbsolutePath() + ")");
        }
    }

    static JsonObject readJson(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static void writeJson(Path file, JsonElement json) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, gson.toJson(json) + "\n", StandardCharsets.UTF_8);
    }

    /** Deletes a generator's output folder, so files of removed items do not stay behind. */
    static void clean(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
