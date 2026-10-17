package team.civitatum.charactercreation.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An ordered list of rules from metadata that sorts pixels: the first rule that matches decides. A rule matches by
 * {@code "when"} (a {@link PixelExpression} condition), by {@code "colors"} (a list of exact {@code #RRGGBB} colours),
 * or by both. Everything else in the rule ({@code "zone"}, {@code "piece"}, {@code "slot"} ...) is the result.
 */
final class PixelRules {
    private record Rule(PixelExpression when, Set<Integer> colors, JsonObject result) {
    }

    private final List<Rule> rules = new ArrayList<>();
    private final JsonObject fallback;

    /** {@code fallback} is the result when no rule matches; null means every pixel must match a rule. */
    PixelRules(JsonArray rules, JsonObject fallback) {
        if (rules != null) {
            for (JsonElement element : rules) {
                JsonObject rule = element.getAsJsonObject();
                PixelExpression when = rule.has("when") ? PixelExpression.parse(rule.get("when").getAsString()) : null;
                Set<Integer> colors = null;
                if (rule.has("colors")) {
                    colors = new HashSet<>();
                    for (JsonElement color : rule.getAsJsonArray("colors")) {
                        colors.add(Integer.parseInt(color.getAsString().substring(1), 16));
                    }
                }
                if (when == null && colors == null) {
                    throw new IllegalArgumentException("A rule needs \"when\" or \"colors\": " + rule);
                }
                this.rules.add(new Rule(when, colors, rule));
            }
        }
        this.fallback = fallback;
    }

    JsonObject match(PixelExpression.Pixel pixel) {
        int rgb = pixel.r() << 16 | pixel.g() << 8 | pixel.b();
        for (Rule rule : rules) {
            if ((rule.colors() == null || rule.colors().contains(rgb)) && (rule.when() == null || rule.when().test(pixel))) {
                return rule.result();
            }
        }
        if (fallback == null) {
            throw new IllegalStateException("No rule matches the pixel at " + pixel.x() + "," + pixel.y());
        }
        return fallback;
    }
}
