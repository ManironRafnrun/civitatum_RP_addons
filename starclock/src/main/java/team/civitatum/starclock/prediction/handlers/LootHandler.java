package team.civitatum.starclock.prediction.handlers;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import team.civitatum.starclock.prediction.ActionContext;
import team.civitatum.starclock.prediction.ItemFactory;
import team.civitatum.starclock.prediction.Parameters;
import team.civitatum.starclock.prediction.SafeNumbers;

public final class LootHandler implements ActionHandler {
    @Override
    public boolean run(ActionContext context, JsonObject params) {
        String special = Parameters.text(params, "special", "");
        switch (special) {
            case "village_map" -> {
                ServerLevel overworld = context.player().server.overworld();
                double scale = context.level().dimensionType().coordinateScale() / overworld.dimensionType().coordinateScale();
                BlockPos searchOrigin = BlockPos.containing(context.player().getX() * scale,
                        context.player().getY(), context.player().getZ() * scale);
                BlockPos village = overworld.findNearestMapStructure(StructureTags.VILLAGE,
                        searchOrigin, 64, false);
                if (village == null) return false;
                ItemStack map = MapItem.create(overworld, village.getX(), village.getZ(), (byte) 2, true, true);
                MapItem.renderBiomePreviewMap(overworld, map);
                MapItemSavedData.addTargetDecoration(map, village, "+", MapDecorationTypes.TARGET_X);
                map.set(DataComponents.CUSTOM_NAME, Component.translatable("item.civitatum_starclock.village_map"));
                ItemFactory.give(context.player(), map, 1);
                return true;
            }
            case "ancestor_book" -> {
                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                        Filterable.passThrough("Память об Иларе"), "Звёздный счёт", 0,
                        List.of(Filterable.passThrough(Component.literal(
                                "Имя Илара помнят у старой переправы. Пока его произносят, путь назад не закрыт."))), true));
                ItemFactory.give(context.player(), book, 1);
                return true;
            }
            case "hotbar_drop" -> {
                dropHotbarItem(context);
                return true;
            }
            case "take_emeralds" -> {
                int required = SafeNumbers.scaled(Parameters.integer(params, "count", 8), context.tier());
                if (context.player().getInventory().countItem(Items.EMERALD) < required) {
                    dropHotbarItem(context);
                } else {
                    for (int i = 0; i < context.player().getInventory().getContainerSize() && required > 0; i++) {
                        var stack = context.player().getInventory().getItem(i);
                        if (stack.is(Items.EMERALD)) {
                            int removed = Math.min(required, stack.getCount());
                            stack.shrink(removed);
                            required -= removed;
                        }
                    }
                    context.player().getInventory().setChanged();
                    context.player().containerMenu.broadcastChanges();
                }
                return true;
            }
            case "" -> {
                if (params.has("table")) {
                    var key = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(params.get("table").getAsString()));
                    var table = context.player().server.reloadableRegistries().getLootTable(key);
                    var lootParams = new LootParams.Builder(context.level())
                            .withParameter(LootContextParams.ORIGIN, context.player().position())
                            .withOptionalParameter(LootContextParams.THIS_ENTITY, context.player())
                            .withLuck(context.player().getLuck()).create(LootContextParamSets.CHEST);
                    var items = table.getRandomItems(lootParams);
                    if (items.isEmpty()) return false;
                    for (ItemStack stack : items) {
                        ItemFactory.give(context.player(), stack, SafeNumbers.scaled(stack.getCount(), context.tier()));
                    }
                }
                if (params.has("items")) {
                    for (var value : params.getAsJsonArray("items")) {
                        var item = value.getAsJsonObject();
                        int count = Parameters.integer(item, "count", 1);
                        if (Parameters.flag(params, "scale", true)) count = SafeNumbers.scaled(count, context.tier());
                        ItemFactory.give(context.player(), ItemFactory.create(item, context.player()), count);
                    }
                }
                return true;
            }
            default -> throw new IllegalArgumentException("Неизвестная особая добыча: " + special);
        }
    }

    private void dropHotbarItem(ActionContext context) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (!context.player().getInventory().getItem(i).isEmpty()) slots.add(i);
        if (!slots.isEmpty()) {
            int slot = slots.get(context.random().nextInt(slots.size()));
            var stack = context.player().getInventory().removeItem(slot, 1);
            context.player().drop(stack, false);
            context.player().containerMenu.broadcastChanges();
        } else {
            context.player().addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.HUNGER, 400));
        }
    }
}
