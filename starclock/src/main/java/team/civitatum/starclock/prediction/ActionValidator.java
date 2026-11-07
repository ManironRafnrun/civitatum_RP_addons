package team.civitatum.starclock.prediction;

import com.google.gson.JsonObject;

public final class ActionValidator {
    public static void validate(ActionSpec action) {
        JsonObject p = action.params();
        if (p.toString().length() > 8000) throw new IllegalArgumentException("Слишком большое действие");
        switch (action.type()) {
            case "spawn" -> {
                resource(p.get("entity").getAsString());
                range(Parameters.integer(p, "count", 1), 1, 16);
                range(Parameters.integer(p, "distance", 8), 1, 32);
                range(Parameters.integer(p, "maxDistance", Parameters.integer(p, "distance", 8)), 1, 48);
                range(Parameters.integer(p, "lifetime", 0), 0, 86400);
            }
            case "effect" -> {
                boolean hasEffect = p.has("effects") || p.has("remove") || p.has("clear")
                        || p.has("food") || p.has("experienceLevels") || p.has("fireSeconds")
                        || p.has("peaceSeconds") || p.has("tameNearest") || p.has("ability");
                if (!hasEffect) throw new IllegalArgumentException("Пустое действие эффектов");
                if (p.has("effects")) {
                    if (p.getAsJsonArray("effects").size() > 8) throw new IllegalArgumentException("Слишком много эффектов");
                    for (var value : p.getAsJsonArray("effects")) {
                        var effect = value.getAsJsonObject();
                        resource(effect.get("id").getAsString());
                        range(effect.get("seconds").getAsInt(), 1, 12000);
                        range(Parameters.integer(effect, "level", 1), 1, 10);
                    }
                }
                range(Parameters.integer(p, "radius", 16), 1, 128);
                range(Parameters.integer(p, "peaceSeconds", 0), 0, 12000);
            }
            case "loot" -> {
                if (!p.has("items") && !p.has("table") && !p.has("special")) throw new IllegalArgumentException("Пустая добыча");
                if (p.has("table")) resource(p.get("table").getAsString());
                if (p.has("items")) {
                    if (p.getAsJsonArray("items").size() > 16) throw new IllegalArgumentException("Слишком много предметов");
                    for (var value : p.getAsJsonArray("items")) {
                        var item = value.getAsJsonObject();
                        resource(item.get("id").getAsString());
                        range(Parameters.integer(item, "count", 1), 1, 64);
                    }
                }
            }
            case "block" -> {
                resource(p.get("block").getAsString());
                range(Parameters.integer(p, "seconds", 10), 1, 600);
                for (String key : java.util.List.of("x", "y", "z")) range(Parameters.integer(p, key, 0), -8, 8);
            }
            case "teleport" -> {
                range(Parameters.integer(p, "distance", 16), 1, 64);
                range(Parameters.integer(p, "minimum", Parameters.integer(p, "distance", 16)), 1, 64);
            }
            case "damage" -> {
                double amount = p.get("amount").getAsDouble();
                if (!Double.isFinite(amount) || amount < 0 || amount > 100) throw new IllegalArgumentException("Неверный урон");
            }
            case "durability" -> {
                if (!java.util.Set.of("repair", "damage", "remove_enchantment").contains(p.get("mode").getAsString())) {
                    throw new IllegalArgumentException("Неизвестная операция прочности");
                }
                double fraction = Parameters.decimal(p, "fraction", 0.5);
                if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1) throw new IllegalArgumentException("Неверный износ");
            }
            case "weather" -> range(Parameters.integer(p, "seconds", 60), 1, 3600);
            case "trader" -> {
                if (p.has("sign")) Constellation.valueOf(p.get("sign").getAsString().toUpperCase(java.util.Locale.ROOT));
            }
            default -> throw new IllegalArgumentException("Неизвестное действие");
        }
    }

    private static void resource(String value) {
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Неверный идентификатор: " + value);
    }

    private static void range(int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException("Параметр за допустимыми границами");
    }

    private ActionValidator() {}
}
