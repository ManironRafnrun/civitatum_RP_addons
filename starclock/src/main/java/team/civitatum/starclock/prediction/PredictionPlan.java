package team.civitatum.starclock.prediction;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PredictionPlan(String eventId, String variantId, Constellation sign,
                             Orientation orientation, boolean positive, String category,
                             String target, String omenKey, List<ActionSpec> actions) {
    private static final Set<String> TARGETS = Set.of("player", "held_item", "nearby_hostiles",
            "nearby_undead", "nearby_wolf", "world");

    public PredictionPlan {
        Objects.requireNonNull(sign);
        Objects.requireNonNull(orientation);
        if ((sign == Constellation.SERPENT) != (orientation == Orientation.NONE)) {
            throw new IllegalArgumentException("Несовместимые знак и положение");
        }
        if (sign != Constellation.SERPENT && positive != (orientation == Orientation.UPRIGHT)) {
            throw new IllegalArgumentException("Положение не соответствует характеру пророчества");
        }
        if (eventId == null || !eventId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || variantId == null || !variantId.matches("[a-z0-9_]+") || omenKey == null
                || omenKey.isBlank() || !TARGETS.contains(target)) {
            throw new IllegalArgumentException("Неверные метаданные пророчества");
        }
        actions = List.copyOf(actions);
        if (actions.isEmpty() || actions.size() > 16) throw new IllegalArgumentException("Неверное число действий");
        if (actions.stream().noneMatch(action -> action.type().equals(category))) {
            throw new IllegalArgumentException("Категория должна соответствовать действию");
        }
    }
}
