package team.civitatum.starclock.tools.sound;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Java port of tools/generate_sounds.py: synthesizes the ritual sounds and encodes them to Ogg Vorbis with ffmpeg,
 * which Java has no encoder for. The Python generator used ffmpeg 7.0.2 of imageio-ffmpeg 0.6.0; another ffmpeg
 * build may encode the same PCM to different bytes.
 *
 * <p>usage: {@code SoundGenerator --output <resources dir> [--report <file>] [--ffmpeg <path>]}
 */
public final class SoundGenerator {
    public static void main(String[] args) throws IOException, InterruptedException {
        Path output = null, report = null;
        String ffmpeg = "ffmpeg";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--output" -> output = Path.of(args[++i]);
                case "--report" -> report = Path.of(args[++i]);
                case "--ffmpeg" -> ffmpeg = args[++i];
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        if (output == null) throw new IllegalArgumentException("--output is required");
        generate(output, report, ffmpeg);
    }

    public static void generate(Path output, Path report, String ffmpeg) throws IOException, InterruptedException {
        var target = output.resolve("assets/starclock/sounds/ritual");
        Files.createDirectories(target);
        var json = new StringBuilder("{\n  \"sample_rate\": " + Synth.RATE + ",\n");
        json.append("  \"channels\": 1,\n  \"sounds\": {\n");
        var temporary = Files.createTempDirectory("starclock-audio-");
        try {
            int i = 0;
            for (var entry : Synth.DURATIONS.entrySet()) {
                String name = entry.getKey();
                var source = temporary.resolve(name + ".wav");
                Files.write(source, wav(Synth.synthesize(name)));
                var destination = target.resolve(name + ".ogg");
                encode(ffmpeg, source, destination);
                byte[] ogg = OggStreams.canonicalize(Files.readAllBytes(destination), name);
                Files.write(destination, ogg);
                json.append("    \"").append(name).append("\": {\n")
                    .append("      \"duration\": ").append(entry.getValue()).append(",\n")
                    .append("      \"sha256\": \"").append(sha256(ogg)).append("\",\n")
                    .append("      \"bytes\": ").append(ogg.length).append('\n')
                    .append("    }").append(++i < Synth.DURATIONS.size() ? ",\n" : "\n");
            }
        } finally {
            try (var files = Files.walk(temporary)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        json.append("  }\n}\n");
        if (report != null) {
            if (report.getParent() != null) Files.createDirectories(report.getParent());
            Files.writeString(report, json, StandardCharsets.UTF_8);
        }
        System.out.println(String.format(Locale.ROOT, "Создано %d оригинальных звуков Ogg Vorbis в %s",
            Synth.DURATIONS.size(), target));
    }

    /** The same ffmpeg call as the Python generator: libvorbis quality 4, no metadata, bit-exact flags. */
    private static void encode(String ffmpeg, Path source, Path destination) throws IOException, InterruptedException {
        var command = List.of(ffmpeg, "-nostdin", "-v", "error", "-y", "-i", source.toString(), "-map_metadata", "-1",
            "-c:a", "libvorbis", "-q:a", "4", "-fflags", "+bitexact", "-flags:a", "+bitexact", destination.toString());
        var process = new ProcessBuilder(command).inheritIO().start();
        if (process.waitFor() != 0) throw new IOException("ffmpeg failed on " + source.getFileName());
    }

    /** A 16-bit mono PCM WAV file with the 44-byte header Python's {@code wave} module writes. */
    static byte[] wav(short[] pcm) {
        int dataSize = pcm.length * 2;
        var buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataSize);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
            .putInt(Synth.RATE).putInt(Synth.RATE * 2).putShort((short) 2).putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize);
        for (short sample : pcm) buffer.putShort(sample);
        return buffer.array();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private SoundGenerator() {}
}
