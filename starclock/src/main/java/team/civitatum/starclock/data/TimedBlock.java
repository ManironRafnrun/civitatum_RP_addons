package team.civitatum.starclock.data;

public record TimedBlock(String dimension, int x, int y, int z, String placedBlock, long expiresAt) {
    public TimedBlock {
        if (dimension == null || placedBlock == null || expiresAt < 0) throw new IllegalArgumentException("Неверный временный блок");
    }

    public String key() {
        return dimension + "/" + x + "/" + y + "/" + z;
    }
}
