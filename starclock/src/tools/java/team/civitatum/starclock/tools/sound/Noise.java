package team.civitatum.starclock.tools.sound;

/** Seeded xorshift32 white noise with a one-pole low-pass state, as the Python synthesizer has it. */
final class Noise {
    private long state;
    /** Low-passed noise, updated by the synthesizer itself. */
    double low;

    Noise(long seed) {
        this.state = seed;
    }

    /** The next sample in [-1, 1). */
    double sample() {
        long value = state;
        value ^= (value << 13) & 0xFFFFFFFFL;
        value ^= value >> 17;
        value ^= (value << 5) & 0xFFFFFFFFL;
        state = value & 0xFFFFFFFFL;
        return state / 2147483648.0 - 1.0;
    }
}
