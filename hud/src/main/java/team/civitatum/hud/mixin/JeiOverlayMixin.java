package team.civitatum.hud.mixin;

import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * JEI's panels, the ingredient list, the bookmarks and the config button, only on its own recipes screen (opened by
 * our browser key or R/U on an item): not over chests, crafting tables, our inventory or any other menu. JEI tells
 * its panels the screen's place through here; any other screen reads as none, so they stay away. What does not
 * need the panels stays, as R/U on an item in a menu and the recipe areas of a furnace's arrow.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.GuiPropertiesCache$Updater", remap = false)
public abstract class JeiOverlayMixin {
    @ModifyVariable(method = "updateGuiProperties", at = @At("HEAD"), argsOnly = true, require = 0)
    private IGuiProperties civitatumHud$recipesOnly(IGuiProperties properties) {
        return properties == null || RecipesGui.class.isAssignableFrom(properties.screenClass()) ? properties : null;
    }
}
