package team.civitatum.charactercreation.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import team.civitatum.charactercreation.power.Hooks;
import com.llamalad7.mixinextras.sugar.Local;

/** Potions of healing heal the mechanist 25% less: the drunk one (instant) and the one of a beacon-like tick. */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class HealOrHarmMobEffectMixin {
    @ModifyArg(method = "applyEffectTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V"))
    private float civitatum$tick(float amount, @Local(argsOnly = true) LivingEntity entity) {
        return Hooks.potionHeal(entity, amount);
    }

    @ModifyArg(method = "applyInstantenousEffect", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V"))
    private float civitatum$instant(float amount, @Local(argsOnly = true) LivingEntity entity) {
        return Hooks.potionHeal(entity, amount);
    }
}
