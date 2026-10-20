package team.civitatum.starclock.prediction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;

public final class PredictionBonuses {
    public static PredictionPlan append(PredictionPlan plan) {
        var actions = new ArrayList<>(plan.actions());
        JsonObject params = new JsonObject();
        String type;
        if (plan.positive()) {
            type = "loot";
            params.addProperty("scale", false);
            var items = new JsonArray();
            String item;
            String enchantment = null;
            int level = 2;
            switch (plan.sign()) {
                case SPEAR -> { item = "iron_sword"; enchantment = "sharpness"; }
                case SMITH, SPINNER -> { item = "enchanted_book"; enchantment = "unbreaking"; }
                case MESSENGER -> item = "saddle";
                case SHEPHERD -> item = "bell";
                case ANCESTOR -> { item = "enchanted_book"; enchantment = "mending"; level = 1; }
                case MOTHER, SERPENT -> item = "golden_apple";
                case WANDERER -> item = "recovery_compass";
                case HEARTH -> { item = "enchanted_book"; enchantment = "fire_protection"; }
                case TOWER -> { item = "shield"; enchantment = "unbreaking"; }
                case MERCHANT -> { item = "enchanted_book"; enchantment = "fortune"; }
                case BEAST -> item = "diamond_horse_armor";
                default -> throw new IllegalArgumentException("Неизвестный знак");
            }
            var stack = item(item, enchantment, level);
            stack.addProperty("count", 1);
            items.add(stack);
            params.add("items", items);
        } else {
            type = "spawn";
            String entity = switch (plan.sign()) {
                case MESSENGER, WANDERER, MERCHANT -> "pillager";
                case ANCESTOR, SPINNER -> "skeleton";
                case BEAST -> "wolf";
                case SERPENT -> "cave_spider";
                default -> "zombie";
            };
            params.addProperty("entity", "minecraft:" + entity);
            params.addProperty("count", 1);
            params.addProperty("scale", false);
            params.addProperty("distance", 10);
            params.addProperty("maxDistance", 16);
            params.addProperty("lifetime", 300);
            params.addProperty("mode", "hostile");
            params.addProperty("noDrops", true);
            var equipment = new JsonObject();
            switch (plan.sign()) {
                case SPEAR -> {
                    equipment.add("mainhand", item("iron_sword", null, 0));
                    equipment.add("head", item("iron_helmet", null, 0));
                }
                case SMITH -> {
                    equipment.add("mainhand", item("iron_axe", null, 0));
                    equipment.add("chest", item("iron_chestplate", null, 0));
                }
                case MESSENGER, WANDERER -> equipment.add("mainhand", item("crossbow", "quick_charge", 1));
                case ANCESTOR -> equipment.add("mainhand", item("bow", "power", 2));
                case MOTHER -> {
                    equipment.add("mainhand", item("iron_sword", "knockback", 1));
                    equipment.add("head", item("iron_helmet", null, 0));
                }
                case SPINNER -> equipment.add("mainhand", item("bow", "punch", 1));
                case HEARTH -> equipment.add("chest", item("iron_chestplate", "fire_protection", 2));
                case TOWER -> {
                    equipment.add("mainhand", item("iron_sword", null, 0));
                    equipment.add("chest", item("iron_chestplate", null, 0));
                }
                case MERCHANT -> equipment.add("mainhand", item("crossbow", "multishot", 1));
                case SHEPHERD -> {
                    equipment.add("head", item("iron_helmet", null, 0));
                    var effects = new JsonArray();
                    var speed = new JsonObject();
                    speed.addProperty("id", "minecraft:speed");
                    speed.addProperty("seconds", 60);
                    speed.addProperty("level", 1);
                    effects.add(speed);
                    params.add("effects", effects);
                }
                case BEAST -> params.addProperty("health", 30);
                case SERPENT -> params.addProperty("health", 20);
            }
            params.add("equipment", equipment);
        }
        actions.add(new ActionSpec(type, params));
        return new PredictionPlan(plan.eventId(), plan.variantId(), plan.sign(), plan.orientation(),
                plan.positive(), plan.category(), plan.target(), plan.omenKey(), actions);
    }

    private static JsonObject item(String id, String enchantment, int level) {
        var item = new JsonObject();
        item.addProperty("id", "minecraft:" + id);
        if (enchantment != null) {
            var enchantments = new JsonObject();
            enchantments.addProperty("minecraft:" + enchantment, level);
            item.add("enchantments", enchantments);
        }
        return item;
    }

    private PredictionBonuses() {}
}
