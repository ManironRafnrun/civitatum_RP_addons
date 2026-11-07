package team.civitatum.starclock.tools.sound;

/**
 * Makes an encoded Ogg stream reproducible: the encoder picks a random stream serial number, so it is replaced by one
 * derived from the sound's name and every page CRC is recomputed.
 */
final class OggStreams {
    /** Generator polynomial of the Ogg page checksum (CRC-32, not reflected, zero initial value). */
    private static final long POLYNOMIAL = 0x04C11DB7L;

    static byte[] canonicalize(byte[] ogg, String name) {
        byte[] payload = ogg.clone();
        byte[] serial = Synth.sha256(name);
        int offset = 0;
        while (offset < payload.length) {
            if (!new String(payload, offset, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("OggS"))
                throw new IllegalStateException("Повреждён контейнер Ogg: " + name);
            int segments = Byte.toUnsignedInt(payload[offset + 26]);
            int bodyStart = offset + 27 + segments;
            int end = bodyStart;
            for (int i = offset + 27; i < bodyStart; i++) end += Byte.toUnsignedInt(payload[i]);
            if (end > payload.length) throw new IllegalStateException("Неполная страница Ogg: " + name);
            System.arraycopy(serial, 0, payload, offset + 14, 4);
            for (int i = 22; i < 26; i++) payload[offset + i] = 0;
            long crc = crc(payload, offset, end);
            for (int i = 0; i < 4; i++) payload[offset + 22 + i] = (byte) (crc >> (8 * i));
            offset = end;
        }
        return payload;
    }

    private static long crc(byte[] data, int from, int to) {
        long crc = 0;
        for (int i = from; i < to; i++) {
            crc ^= (long) Byte.toUnsignedInt(data[i]) << 24;
            for (int bit = 0; bit < 8; bit++)
                crc = ((crc << 1) ^ ((crc & 0x80000000L) != 0 ? POLYNOMIAL : 0)) & 0xFFFFFFFFL;
        }
        return crc;
    }

    private OggStreams() {}
}
