package team.civitatum.starclock.prediction;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public final class ItemFactory {
    public static ItemStack create(JsonObject json, ServerPlayer player) {
        ResourceLocation id = ResourceLocation.parse(json.get("id").getAsString());
        var item = BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Нет предмета: " + id));
        ItemStack stack = new ItemStack(item);
        if (Parameters.flag(json, "poison", false)) stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
        if (json.has("enchantments")) {
            var lookup = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            EnchantmentHelper.updateEnchantments(stack, mutable -> {
                for (var entry : json.getAsJsonObject("enchantments").entrySet()) {
                    var enchantment = lookup.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,
                            ResourceLocation.parse(entry.getKey())));
                    mutable.set(enchantment, entry.getValue().getAsInt());
                }
            });
        }
        return stack;
    }

    public static void give(ServerPlayer player, ItemStack template, int count) {
        while (count > 0) {
            int amount = Math.min(count, template.getMaxStackSize());
            ItemStack stack = template.copyWithCount(amount);
            if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
            count -= amount;
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private ItemFactory() {}
}
