package team.civitatum.charactercreation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import team.civitatum.charactercreation.power.Hooks;

/** The Smith's wear: after Unbreaking, on the points that would be lost. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @ModifyExpressionValue(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;"
        + "Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processDurabilityChange("
            + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;I)I"))
    private int civitatum$wear(int amount, @Local(argsOnly = true) LivingEntity user) {
        return user == null ? amount : Hooks.durability(user, amount);
    }
}
