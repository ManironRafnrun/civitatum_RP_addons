package team.civitatum.charactercreation.power;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** How the powers give effects. */
public final class Effects {
    /**
     * Set while a power gives an effect: the Merchant halves potions, not the effects of the powers ("Купец срежет сам
     * себе «Набат»" in "10b").
     */
    private static final ThreadLocal<Boolean> GIVING = ThreadLocal.withInitial(() -> false);

    /** An effect of a power: with particles, the power is something others should see. */
    public static void give(LivingEntity entity, Holder<MobEffect> effect, int ticks, int amplifier) {
        give(entity, new MobEffectInstance(effect, ticks, amplifier, false, true, true));
    }

    /**
     * An effect of a passive: no particles ("пассивки от рас должны быть без частиц"), and no icon when
     * {@code icon} is false, for a passive that is always there such as night vision.
     */
    public static void passive(LivingEntity entity, Holder<MobEffect> effect, int ticks, boolean icon) {
        give(entity, new MobEffectInstance(effect, ticks, 0, true, false, icon));
    }

    public static void give(LivingEntity entity, MobEffectInstance instance) {
        GIVING.set(true);
        try {
            entity.addEffect(instance);
        } finally {
            GIVING.set(false);
        }
    }

    public static boolean isGiving() { return GIVING.get(); }

    private Effects() {}
}
