package team.civitatum.starclock.data;

public record PeaceZone(String dimension, int x, int y, int z, int radius, long expiresAt) {
    public PeaceZone {
        if (dimension == null || radius < 1 || radius > 128 || expiresAt < 0) {
            throw new IllegalArgumentException("Неверная зона покоя");
        }
    }

    public boolean contains(String level, double px, double py, double pz, long now) {
        if (now >= expiresAt || !dimension.equals(level)) return false;
        double dx = px - (x + 0.5), dy = py - (y + 0.5), dz = pz - (z + 0.5);
        return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }
}
