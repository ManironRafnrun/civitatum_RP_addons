package team.civitatum.starclock.prediction.handlers;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import team.civitatum.starclock.prediction.ActionContext;
import team.civitatum.starclock.prediction.ItemFactory;
import team.civitatum.starclock.prediction.Parameters;
import team.civitatum.starclock.prediction.SafeNumbers;

public final class DurabilityHandler implements ActionHandler {
    private static final String TEMPORARY = "CivitatumStarclockUnbreaking";

    @Override
    public boolean run(ActionContext context, JsonObject params) {
        ItemStack stack = context.player().getMainHandItem();
        String mode = params.get("mode").getAsString();
        if ("remove_enchantment".equals(mode)) {
            var entries = new ArrayList<>(EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet());
            if (!entries.isEmpty()) {
                var chosen = entries.get(context.random().nextInt(entries.size())).getKey();
                EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(chosen, 0));
            } else {
                context.player().addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 1200 * context.tier()));
            }
        } else if ("repair".equals(mode)) {
            if (stack.isEmpty() || !stack.isDamageableItem()) {
                ItemFactory.give(context.player(), new ItemStack(Items.IRON_PICKAXE), context.tier());
            } else if (stack.getDamageValue() == 0) {
                var unbreaking = context.player().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse("minecraft:unbreaking")));
                int previous = EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(unbreaking);
                if (previous == 0) {
                    EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(unbreaking, 1));
                    CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    custom.putLong(TEMPORARY, context.now() + 300L * 20 * context.tier());
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
                }
            } else {
                stack.setDamageValue(SafeNumbers.durability(stack.getMaxDamage(), stack.getDamageValue(),
                        Parameters.decimal(params, "fraction", 0.5), true));
            }
        } else if ("damage".equals(mode) && stack.isDamageableItem()) {
            int damaged = SafeNumbers.durability(stack.getMaxDamage(), stack.getDamageValue(),
                    Parameters.decimal(params, "fraction", 0.5), false);
            if (damaged >= stack.getMaxDamage()) {
                stack.shrink(1);
                context.level().playSound(null, context.player().blockPosition(),
                        net.minecraft.sounds.SoundEvents.ITEM_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1, 1);
            } else stack.setDamageValue(damaged);
        } else if ("damage".equals(mode)) {
            context.player().addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.WEAKNESS, 600 * context.tier()));
        }
        context.player().getInventory().setChanged();
        context.player().containerMenu.broadcastChanges();
        return true;
    }

    public static void expireTemporaryEnchantment(net.minecraft.server.level.ServerPlayer player, ItemStack stack, long now) {
        var custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!custom.contains(TEMPORARY) || now < custom.getLong(TEMPORARY)) return;
        var unbreaking = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse("minecraft:unbreaking")));
        if (EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(unbreaking) == 1) {
            EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(unbreaking, 0));
        }
        custom.remove(TEMPORARY);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }
}
