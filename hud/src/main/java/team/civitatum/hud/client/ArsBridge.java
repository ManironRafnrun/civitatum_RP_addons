package team.civitatum.hud.client;

import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Ars Nouveau 5.x: reads the client-synchronised attachment through the published mana API. */
public final class ArsBridge {
    public record Mana(double current, double max, double reserved) {}
    /** {@code GuiSpellHUD}: the "slot - name" line of the spell selected in a held spell book. */
    public static final ResourceLocation SPELL_NAME = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "spell_hud");
    private static Method getMana, current, max, reserve;
    private static ModConfigSpec.IntValue spellNameX, spellNameY;
    private static boolean failed;

    /**
     * Finds the client config entries {@code xSpellName} and {@code ySpellName}: {@code GuiSpellHUD} draws its line
     * at {@code (xSpellName, guiHeight - ySpellName)}. Without them the line cannot be moved, so start-up fails.
     */
    static void findSpellNameOffsets() {
        try {
            Class<?> config = Class.forName("com.hollingsworth.arsnouveau.setup.config.Config");
            spellNameX = (ModConfigSpec.IntValue) config.getField("SPELLNAME_X_OFFSET").get(null);
            spellNameY = (ModConfigSpec.IntValue) config.getField("SPELLNAME_Y_OFFSET").get(null);
        } catch (Exception e) {
            throw new IllegalStateException("Civitatum HUD: Ars Nouveau spell name offsets not found", e);
        }
    }

    /** The left edge of Ars's spell name line. */
    static int spellNameLeft() {
        return spellNameX.get();
    }

    /** The top edge of Ars's spell name line. */
    static int spellNameTop(int guiHeight) {
        return guiHeight - spellNameY.get();
    }

    public static Mana mana(Player player) {
        if (failed || !CompatBridge.loaded("ars_nouveau")) return null;
        try {
            if (getMana == null) {
                getMana = Class.forName("com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry")
                    .getMethod("getMana", LivingEntity.class);
                Class<?> cap = Class.forName("com.hollingsworth.arsnouveau.common.capability.ManaCap");
                current = cap.getMethod("getCurrentMana");
                max = cap.getMethod("getMaxMana");
                reserve = cap.getMethod("getReserve");
            }
            Object cap = getMana.invoke(null, player);
            if (cap == null) return null;
            return new Mana(number(current, cap), number(max, cap), number(reserve, cap));
        } catch (ReflectiveOperationException | LinkageError ex) {
            failed = true;
            LogUtils.getLogger().warn("Civitatum HUD: Ars mana adapter unavailable", ex);
            return null;
        }
    }

    private static double number(Method getter, Object cap) throws ReflectiveOperationException {
        return ((Number) getter.invoke(cap)).doubleValue();
    }
}
