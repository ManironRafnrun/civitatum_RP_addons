package team.civitatum.charactercreation.power;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

/**
 * One icon for a power with several parts ("10b", part IV, rule 3): the attribute modifiers are added by the
 * registry, and the parts vanilla gives through its own effect classes are rebuilt here, with the vanilla numbers.
 */
public class PowerEffect extends MobEffect {
    /** Exhaustion every tick; vanilla Hunger adds 0.005 per level. */
    private float exhaustion;
    /** One point of healing every this many ticks; vanilla Regeneration heals every 50 >> amplifier ticks. */
    private int healInterval;
    /** Healing stops when no more than this many ticks are left: a part shorter than the effect. */
    private int healWhileAbove;
    /** Absorption given at the start, as vanilla Absorption gives 4 points per level. */
    private float absorption;

    public PowerEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    /** {@link #addAttributeModifier} that keeps the type, so the parts chain. */
    public PowerEffect modifier(Holder<Attribute> attribute, ResourceLocation id, double amount,
                                AttributeModifier.Operation operation) {
        addAttributeModifier(attribute, id, amount, operation);
        return this;
    }

    /** Hunger at the given vanilla level, 1 for Hunger I. */
    public PowerEffect hunger(int level) {
        if (healInterval > 0) throw new IllegalStateException("A power effect either feeds on hunger or heals");
        exhaustion = 0.005f * level;
        return this;
    }

    /** Regeneration at the given vanilla level for the first {@code seconds} of an effect lasting {@code total}. */
    public PowerEffect regeneration(int level, int seconds, int total) {
        if (exhaustion > 0) throw new IllegalStateException("A power effect either feeds on hunger or heals");
        healInterval = Math.max(1, 50 >> (level - 1));
        healWhileAbove = (total - seconds) * 20;
        return this;
    }

    /** Absorption in hearts, given once when the effect starts. */
    public PowerEffect absorption(int hearts) {
        absorption = hearts * 2;
        return this;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        if (exhaustion > 0) return true;
        return healInterval > 0 && duration > healWhileAbove && duration % healInterval == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (exhaustion > 0 && entity instanceof Player player) player.causeFoodExhaustion(exhaustion);
        if (healInterval > 0 && entity.getHealth() < entity.getMaxHealth()) entity.heal(1);
        return true;
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        if (absorption > 0) entity.setAbsorptionAmount(Math.max(entity.getAbsorptionAmount(), absorption));
    }
}
