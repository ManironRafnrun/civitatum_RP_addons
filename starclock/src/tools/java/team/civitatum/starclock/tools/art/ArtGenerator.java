package team.civitatum.starclock.tools.art;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * Java port of tools/generate_art.py: the constellations from the supplied originals and the original cards,
 * star field, particle and icons. Pixels match the Python generator (Pillow 11.3.0); the PNG encoding does not.
 *
 * <p>usage: {@code ArtGenerator --archive starsigns.zip --output <dir>}
 */
public final class ArtGenerator {
    /** Sign ids of the mod and the names of their originals in the archive. */
    static final Map<String, String> SIGNS = new LinkedHashMap<>();

    static {
        SIGNS.put("tower", "Башня");
        SIGNS.put("spear", "Копье");
        SIGNS.put("hearth", "Очаг");
        SIGNS.put("beast", "Зверь");
        SIGNS.put("mother", "Матерь");
        SIGNS.put("smith", "Кузнец");
        SIGNS.put("merchant", "Купец");
        SIGNS.put("spinner", "Пряха");
        SIGNS.put("messenger", "Гонец");
        SIGNS.put("ancestor", "Пращур");
        SIGNS.put("shepherd", "Пастырь");
        SIGNS.put("wanderer", "Странник");
        SIGNS.put("serpent", "Змей");
    }

    public static final String[] ICONS = {"upright", "reversed", "sky", "timer"};

    public static void main(String[] args) throws IOException {
        Path archive = null, output = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--archive" -> archive = Path.of(args[++i]);
                case "--output" -> output = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        if (archive == null || output == null)
            throw new IllegalArgumentException("--archive and --output are required");
        generate(output, archive);
    }

    /** Writes the textures under {@code output/assets/starclock/textures} and the report next to them. */
    public static Map<String, Raster> generate(Path output, Path archive) throws IOException {
        var target = output.resolve("assets/starclock/textures");
        var report = new ArtReport(archive.getFileName().toString(), sha256(Files.readAllBytes(archive)));
        var figures = new LinkedHashMap<String, Raster>();
        try (var zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            var entries = new LinkedHashMap<String, String>();
            zip.stream().map(e -> e.getName()).filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png"))
                .forEach(n -> entries.put(stem(n), n));
            for (var sign : SIGNS.entrySet()) {
                String name = entries.get(sign.getValue());
                if (name == null) throw new IllegalStateException("В архиве отсутствует " + sign.getValue() + ".png");
                byte[] raw;
                try (var in = zip.getInputStream(zip.getEntry(name))) {
                    raw = in.readAllBytes();
                }
                var figure = ConstellationFigure.of(PngFiles.readRgb(raw));
                figures.put(sign.getKey(), figure.image());
                PngFiles.writeRgba(figure.image(), target.resolve("constellation/" + sign.getKey() + ".png"));
                report.add(sign.getKey(), new ArtReport.Figure(name, sha256(raw), figure.bounds()));
            }
        }
        PngFiles.writeRgba(Ornaments.starfield(), target.resolve("gui/starfield.png"));
        var front = Ornaments.card(false);
        PngFiles.writeRgba(front, target.resolve("gui/card_front.png"));
        PngFiles.writeRgba(Ornaments.card(true), target.resolve("gui/card_back.png"));
        PngFiles.writeRgba(Ornaments.serpentCard(front, figures.get("serpent")), target.resolve("gui/card_zmei.png"));
        PngFiles.writeRgba(Ornaments.particle(), target.resolve("particle/star.png"));
        for (String icon : ICONS) PngFiles.writeRgba(Ornaments.icon(icon), target.resolve("gui/" + icon + ".png"));
        Files.writeString(output.resolve("starclock-art.json"), report.toJson(), StandardCharsets.UTF_8);
        System.out.println("Созданы 13 фигур, три карты, звёздное поле, частица и четыре значка");
        return figures;
    }

    /** {@code pathlib.PurePath.stem}: the last path part without its last suffix. */
    static String stem(String name) {
        String last = name.substring(name.lastIndexOf('/') + 1);
        int dot = last.lastIndexOf('.');
        return dot > 0 ? last.substring(0, dot) : last;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private ArtGenerator() {}
}
