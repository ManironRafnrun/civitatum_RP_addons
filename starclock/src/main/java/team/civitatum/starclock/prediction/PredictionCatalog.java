package team.civitatum.starclock.prediction;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

public final class PredictionCatalog {
    private final Map<String, List<PredictionPlan>> definitions;

    private PredictionCatalog(Map<String, List<PredictionPlan>> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    public static PredictionCatalog parse(Map<String, JsonObject> resources) {
        Map<String, List<PredictionPlan>> definitions = new HashMap<>();
        for (var resource : resources.entrySet()) {
            JsonObject json = resource.getValue();
            String id = json.get("id").getAsString();
            if (!id.equals(resource.getKey())) throw new IllegalArgumentException("Идентификатор не совпадает с именем файла: " + id);
            Constellation sign = Constellation.valueOf(json.get("sign").getAsString().toUpperCase(java.util.Locale.ROOT));
            Orientation orientation = Orientation.valueOf(json.get("orientation").getAsString().toUpperCase(java.util.Locale.ROOT));
            boolean positive = json.get("positive").getAsBoolean();
            List<PredictionPlan> variants = new ArrayList<>();
            var variantIds = new java.util.HashSet<String>();
            for (var element : json.getAsJsonArray("variants")) {
                var variant = element.getAsJsonObject();
                String variantId = variant.get("id").getAsString();
                if (!variantIds.add(variantId)) throw new IllegalArgumentException("Повтор варианта: " + id);
                List<ActionSpec> actions = new ArrayList<>();
                for (var value : variant.getAsJsonArray("actions")) {
                    var action = value.getAsJsonObject();
                    actions.add(new ActionSpec(action.get("type").getAsString(), action.getAsJsonObject("params")));
                }
                variants.add(new PredictionPlan(id, variantId, sign, orientation, positive,
                        variant.get("category").getAsString(), variant.get("target").getAsString(),
                        json.get("omen").getAsString(), actions));
            }
            int expected = sign == Constellation.SERPENT ? 2 : 3;
            if (variants.size() != expected) throw new IllegalArgumentException("Неверное число вариантов: " + id);
            definitions.put(id, List.copyOf(variants));
        }
        PredictionCatalog catalog = new PredictionCatalog(definitions);
        catalog.validatePools();
        return catalog;
    }

    private void validatePools() {
        if (definitions.size() != 32) throw new IllegalArgumentException("Ожидается 32 базовых пророчества");
        for (Constellation sign : Constellation.values()) {
            if (sign == Constellation.SERPENT) continue;
            for (Orientation orientation : List.of(Orientation.UPRIGHT, Orientation.REVERSED)) {
                if (pool(sign, orientation).size() != 1) {
                    throw new IllegalArgumentException("Для обычного знака нужно одно пророчество на положение: " + sign);
                }
            }
        }
        var serpent = pool(Constellation.SERPENT, Orientation.NONE);
        if (serpent.size() != 8 || serpent.stream().filter(plans -> plans.get(0).positive()).count() != 4) {
            throw new IllegalArgumentException("Змею нужны четыре хороших и четыре плохих пророчества");
        }
    }

    private List<List<PredictionPlan>> pool(Constellation sign, Orientation orientation) {
        return definitions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue).filter(plans -> plans.get(0).sign() == sign
                        && plans.get(0).orientation() == orientation).toList();
    }

    public PredictionPlan draw(Constellation sign, Orientation orientation, RandomGenerator random) {
        var pool = pool(sign, orientation);
        if (pool.isEmpty()) throw new IllegalArgumentException("Нет пророчества для знака");
        var variants = pool.get(random.nextInt(pool.size()));
        return variants.get(random.nextInt(variants.size()));
    }

    public PredictionPlan get(String id, int variant) {
        var plans = definitions.get(id);
        if (plans == null || variant < 0 || variant >= plans.size()) throw new IllegalArgumentException("Неизвестное пророчество: " + id);
        return plans.get(variant);
    }

    public List<PredictionPlan> allPlans() {
        return definitions.values().stream().flatMap(List::stream)
                .sorted(Comparator.comparing(PredictionPlan::eventId).thenComparing(PredictionPlan::variantId)).toList();
    }

    public List<String> ids() {
        return definitions.keySet().stream().sorted().toList();
    }
}
