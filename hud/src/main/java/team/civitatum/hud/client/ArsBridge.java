package team.civitatum.hud.client;

import java.lang.reflect.Method;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.mojang.logging.LogUtils;

/** Ars Nouveau 5.x: reads the client-synchronised attachment through the published mana API. */
public final class ArsBridge {
    public record Mana(double current, double max, double reserved) {}
    private static Method getMana, current, max, reserve;
    private static boolean failed;

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
