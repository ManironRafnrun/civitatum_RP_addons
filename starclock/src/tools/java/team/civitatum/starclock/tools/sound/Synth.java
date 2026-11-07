package team.civitatum.starclock.tools.sound;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The ritual sounds as 16-bit mono PCM, synthesized as in tools/generate_sounds.py. Every expression keeps Python's
 * order of double operations; {@code x ** 2} is {@code x * x}. Transcendentals go through fdlibm, Python's through
 * the C library, so a sample may in principle differ by one step.
 */
final class Synth {
    static final int RATE = 44100;
    private static final double TAU = 2 * Math.PI;
    /** Length of every sound in seconds, in the order they are written. */
    static final Map<String, Double> DURATIONS = new LinkedHashMap<>();

    static {
        DURATIONS.put("shuffle", 0.28);
        DURATIONS.put("sextant_click", 0.18);
        DURATIONS.put("water_pour", 0.70);
        DURATIONS.put("ring_hum", 0.75);
        DURATIONS.put("stars_reveal", 1.20);
        DURATIONS.put("card_flip", 0.24);
        DURATIONS.put("omen_good", 1.35);
        DURATIONS.put("omen_bad", 1.35);
    }

    /** Peak level of every sound, leaving headroom for the Vorbis encoder. */
    private static final double PEAK = 0.62;

    static short[] synthesize(String name) {
        double duration = DURATIONS.get(name);
        long seed = littleEndian(sha256(name), 4);
        var noise = new Noise(seed == 0 ? 1 : seed);
        int count = (int) Math.rint(duration * RATE);
        double[] samples = new double[count];
        for (int index = 0; index < count; index++) {
            double t = index / (double) RATE;
            double raw = noise.sample();
            noise.low += 0.06 * (raw - noise.low);
            double high = raw - noise.low;
            double value = switch (name) {
                case "shuffle" -> {
                    double strokes = 0;
                    for (double start : new double[] {0.035, 0.11, 0.19}) strokes += exp(-square((t - start) / 0.022));
                    yield (high * 0.7 + noise.low * 0.6) * strokes;
                }
                case "sextant_click" -> 0.5 * bell(t, 1640, 42) + 0.3 * bell(t, 2900, 65) + high * exp(-130 * t) * 0.22;
                case "water_pour" -> {
                    double flowing = StrictMath.pow(sin(Math.PI * t / duration), 1.1);
                    double v = (noise.low * 1.5 + high * 0.07) * flowing;
                    double[][] drops = {{0.08, 780}, {0.24, 1120}, {0.41, 630}, {0.54, 930}};
                    for (double[] drop : drops) {
                        double dt = t - drop[0];
                        if (dt >= 0) v += 0.13 * exp(-38 * dt) * sin(TAU * (drop[1] * dt - 370 * dt * dt));
                    }
                    yield v;
                }
                case "ring_hum" -> {
                    double modulation = 0.7 + 0.3 * sin(TAU * 4.5 * t);
                    double phase = TAU * 220 * t + 0.035 * sin(TAU * 5 * t);
                    yield modulation * (0.65 * sin(phase) + 0.23 * sin(phase * 2) + 0.10 * sin(phase * 4));
                }
                case "stars_reveal" -> {
                    double v = chord(t, 0.28, 4.0,
                        new double[][] {{0.0, 440}, {0.12, 554.365}, {0.24, 659.255}, {0.40, 880}});
                    yield v + high * 0.035 * square(sin(Math.PI * t / duration));
                }
                case "card_flip" -> {
                    double swish = exp(-square((t - 0.09) / 0.043));
                    double flutter = 0.55 + 0.45 * sin(TAU * 52 * t);
                    yield high * swish * flutter * 0.7 + 0.12 * bell(t - 0.13, 1050, 90);
                }
                case "omen_good" -> chord(t, 0.25, 2.8,
                    new double[][] {{0.0, 261.6256}, {0.07, 329.6276}, {0.14, 391.9954}, {0.30, 523.2511}});
                case "omen_bad" -> {
                    double v = chord(t, 0.23, 2.6,
                        new double[][] {{0.0, 146.8324}, {0.08, 155.5635}, {0.16, 207.6523}});
                    yield v + noise.low * 0.12 * sin(Math.PI * t / duration);
                }
                default -> throw new IllegalArgumentException("Неизвестный звук: " + name);
            };
            double attack = name.equals("sextant_click") ? 0.001 : 0.008;
            double release = duration < 0.4 ? 0.045 : 0.16;
            double envelope = Math.min(Math.min(1.0, t / attack), (duration - t) / release);
            if (name.equals("ring_hum")) envelope *= Math.min(Math.min(1.0, t / 0.06), (duration - t) / 0.22);
            samples[index] = value * Math.max(0.0, envelope);
        }
        double peak = 0;
        for (double v : samples) peak = Math.max(peak, Math.abs(v));
        if (peak <= 0) throw new IllegalStateException("Синтезирована тишина: " + name);
        double gain = PEAK / peak;
        short[] pcm = new short[count];
        for (int i = 0; i < count; i++) pcm[i] = (short) Math.rint(Math.clamp(samples[i] * gain, -1.0, 1.0) * 32767);
        return pcm;
    }

    /** A struck bell: 4 ms onset, exponential decay, the fundamental with two slightly sharp partials. */
    private static double bell(double t, double frequency, double decay) {
        if (t < 0) return 0.0;
        double onset = Math.min(1.0, t / 0.004);
        return onset * exp(-decay * t) * (sin(TAU * frequency * t) + 0.28 * sin(TAU * frequency * 2.003 * t)
            + 0.09 * sin(TAU * frequency * 4.011 * t));
    }

    /** Sum of bells {start, frequency}, each at {@code level}, added in order as Python's {@code sum}. */
    private static double chord(double t, double level, double decay, double[][] notes) {
        double sum = 0;
        for (double[] note : notes) sum += level * bell(t - note[0], note[1], decay);
        return sum;
    }

    static byte[] sha256(String name) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long littleEndian(byte[] bytes, int count) {
        long value = 0;
        for (int i = count - 1; i >= 0; i--) value = value << 8 | Byte.toUnsignedLong(bytes[i]);
        return value;
    }

    private static double square(double x) {
        return x * x;
    }

    private static double sin(double x) {
        return StrictMath.sin(x);
    }

    private static double exp(double x) {
        return StrictMath.exp(x);
    }

    private Synth() {}
}
