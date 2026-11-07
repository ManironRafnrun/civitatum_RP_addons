package team.civitatum.hud.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * AppleSkin 3.x: its estimate of the held food and its settings for showing it. Without AppleSkin the item's own
 * food value is used.
 */
public final class AppleSkinBridge {
    private static final double DEFAULT_FLASH_ALPHA = 0.65;
    private static Class<?> config;
    private static Method isFood, canConsume, query;
    /** The settings and the food field are read every frame; their lookup is done once. Render thread only. */
    private static final Map<String, Field> SETTINGS = new HashMap<>();
    private static Field modifiedFood;
    private static boolean failed;

    /**
     * Hunger the held food would restore: the main hand, or the off hand if AppleSkin is set to show that.
     * Zero when nothing held can be eaten now, or when AppleSkin's food overlay is switched off.
     */
    public static int heldHunger(Player player) {
        if (!active()) {
            var food = player.getMainHandItem().getFoodProperties(player);
            return food != null && player.canEat(food.canAlwaysEat()) ? food.nutrition() : 0;
        }
        if (!option("SHOW_FOOD_VALUES_OVERLAY")) return 0;
        int hunger = hunger(player, player.getMainHandItem());
        if (hunger == 0 && option("SHOW_FOOD_VALUES_OVERLAY_WHEN_OFFHAND")) {
            hunger = hunger(player, player.getOffhandItem());
        }
        return hunger;
    }

    public static double flashAlpha() {
        if (!active()) return DEFAULT_FLASH_ALPHA;
        try {
            return ((Number) setting("MAX_HUD_OVERLAY_FLASH_ALPHA")).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return DEFAULT_FLASH_ALPHA;
        }
    }

    private static boolean option(String name) {
        try {
            return (Boolean) setting(name);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private static Object setting(String name) throws ReflectiveOperationException {
        Field field = SETTINGS.get(name);
        if (field == null) {
            field = config.getField(name);
            SETTINGS.put(name, field);
        }
        return ((Supplier<?>) field.get(null)).get();
    }

    private static int hunger(Player player, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        try {
            if (!(Boolean) isFood.invoke(null, stack, player)) return 0;
            Object result = query.invoke(null, stack, player);
            if (modifiedFood == null) modifiedFood = result.getClass().getField("modifiedFoodProperties");
            var food = (FoodProperties) modifiedFood.get(result);
            return food != null && (Boolean) canConsume.invoke(null, player, food) ? food.nutrition() : 0;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return 0;
        }
    }

    private static boolean active() {
        if (failed || !CompatBridge.loaded("appleskin")) return false;
        if (config != null) return true;
        try {
            Class<?> food = Class.forName("squeek.appleskin.helpers.FoodHelper");
            isFood = food.getMethod("isFood", ItemStack.class, Player.class);
            canConsume = food.getMethod("canConsume", Player.class, FoodProperties.class);
            query = food.getMethod("query", ItemStack.class, Player.class);
            config = Class.forName("squeek.appleskin.ModConfig");
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            failed = true;
            LogUtils.getLogger().warn("Civitatum HUD: AppleSkin adapter unavailable", ex);
            return false;
        }
    }
}
