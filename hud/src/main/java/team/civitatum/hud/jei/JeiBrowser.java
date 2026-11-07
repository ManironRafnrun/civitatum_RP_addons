package team.civitatum.hud.jei;

import java.util.List;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * JEI on its own, out of any menu: its recipes screen over all its categories, the first (crafting) shown. JEI lays
 * the screen out as it always does, the recipes in the middle, every ingredient on the right, the bookmarks on the
 * left; a click on any of them goes on as in JEI. Only touched when JEI is installed.
 */
public final class JeiBrowser {
    /** Set by {@link CivitatumJeiPlugin} while a world is open; null before JEI is ready or after it leaves. */
    static volatile IJeiRuntime runtime;

    private JeiBrowser() {}

    /** Opens the browser; false when JEI is not ready yet. */
    public static boolean open() {
        var jei = runtime;
        if (jei == null) return false;
        List<RecipeType<?>> types = jei.getRecipeManager().createRecipeCategoryLookup().get()
            .<RecipeType<?>>map(category -> category.getRecipeType()).toList();
        if (types.isEmpty()) return false;
        jei.getRecipesGui().showTypes(types);
        return true;
    }
}
