package team.civitatum.starclock.data;

import com.google.gson.Gson;
import java.util.Objects;
import java.util.UUID;
import team.civitatum.starclock.prediction.PredictionPlan;

public record PendingPrediction(UUID id, UUID player, String dimension, int x, int y, int z,
                                long dueGameTime, int tier, PredictionPlan plan, boolean waited,
                                int nextAction, int attempts, long retryAfter) {
    private static final Gson JSON = new Gson();

    public PendingPrediction {
        Objects.requireNonNull(id);
        Objects.requireNonNull(player);
        Objects.requireNonNull(plan);
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || dueGameTime < 0 || tier < 1 || tier > 3 || nextAction < 0
                || nextAction > plan.actions().size() + 1 || attempts < 0 || retryAfter < 0) {
            throw new IllegalArgumentException("Неверная запись очереди предсказаний");
        }
    }

    public boolean isDue(long now) {
        return now >= dueGameTime && now >= retryAfter;
    }

    public PendingPrediction waiting() {
        return new PendingPrediction(id, player, dimension, x, y, z, dueGameTime, tier, plan,
                true, nextAction, attempts, retryAfter);
    }

    public PendingPrediction advance() {
        return new PendingPrediction(id, player, dimension, x, y, z, dueGameTime, tier, plan,
                waited, nextAction + 1, 0, 0);
    }

    public PendingPrediction retry(long now) {
        int attempt = Math.min(30, attempts + 1);
        long delay = Math.min(1200L, 20L << Math.min(attempt, 6));
        return new PendingPrediction(id, player, dimension, x, y, z, dueGameTime, tier, plan,
                waited, nextAction, attempt, now + delay);
    }

    public String toJson() {
        return JSON.toJson(this);
    }

    public static PendingPrediction fromJson(String value) {
        PendingPrediction result = JSON.fromJson(value, PendingPrediction.class);
        if (result == null) throw new IllegalArgumentException("Пустая запись очереди");
        return result;
    }
}
