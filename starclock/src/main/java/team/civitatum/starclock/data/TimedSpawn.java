package team.civitatum.starclock.data;

import java.util.UUID;

public record TimedSpawn(UUID entity, String dimension, long expiresAt) {
    public TimedSpawn {
        if (entity == null || dimension == null || expiresAt < 0) throw new IllegalArgumentException("Неверный срок сущности");
    }
}
