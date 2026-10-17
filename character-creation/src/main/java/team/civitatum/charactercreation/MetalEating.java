package team.civitatum.charactercreation;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.*;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class MetalEating {
    private record Meal(InteractionHand hand, ItemStack identity, long finish, ModConfig.MetalFood food, boolean ore, boolean netherite) {}
    private static final Map<UUID,Meal> MEALS=new HashMap<>();
    private static final Set<String> ORES=Set.of("iron_ore","deepslate_iron_ore","gold_ore","deepslate_gold_ore","nether_gold_ore","copper_ore","deepslate_copper_ore","ancient_debris","raw_iron","raw_gold","raw_copper");
    public static ModConfig.MetalFood food(ItemStack stack) {
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if(ModConfig.INSTANCE.metalBlacklist.contains(id)) return null;
        if(ModConfig.INSTANCE.metalOverrides.containsKey(id)) return ModConfig.INSTANCE.metalOverrides.get(id);
        if(id.startsWith("minecraft:")) {
            String path=id.substring(10);
            if(ORES.contains(path)) return new ModConfig.MetalFood(2,1);
            return switch(path) {
                case "iron_nugget","gold_nugget" -> new ModConfig.MetalFood(1,1);
                case "copper_ingot" -> new ModConfig.MetalFood(3,4);
                case "iron_ingot" -> new ModConfig.MetalFood(4,5);
                case "gold_ingot" -> new ModConfig.MetalFood(3,7);
                case "netherite_ingot" -> new ModConfig.MetalFood(8,10);
                default -> null;
            };
        }
        if(!ModConfig.INSTANCE.forgedMetalPatches) return null;
        if(stack.is(TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("c","ingots/steel")))) return new ModConfig.MetalFood(6,6);
        if(stack.is(TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("c","ingots")))) return new ModConfig.MetalFood(3,3);
        if(stack.is(TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("c","nuggets")))) return new ModConfig.MetalFood(1,1);
        if(stack.is(TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("c","ores")))) return new ModConfig.MetalFood(2,1);
        return null;
    }
    public static boolean start(ServerPlayer p, InteractionHand hand) {
        var r=RaceService.race(p);
        if(r==null || !r.id.equals("forged")) return false;
        ItemStack stack=p.getItemInHand(hand);
        var food=food(stack);
        if(food==null) return false;
        if(MEALS.containsKey(p.getUUID())) return true;
        if(!p.canEat(false)) return false;
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        boolean nugget=id.endsWith("_nugget") || stack.is(TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("c","nuggets")));
        boolean ore=ORES.contains(id) || stack.is(TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("c","ores")));
        int ticks=nugget ? Math.max(8,ModConfig.INSTANCE.metalUseTicks/2) : ModConfig.INSTANCE.metalUseTicks;
        MEALS.put(p.getUUID(),new Meal(hand,stack.copy(),RaceService.time(p)+ticks,food,ore,stack.is(Items.NETHERITE_INGOT)));
        p.displayClientMessage(Component.literal("Питание металлом… удерживайте предмет в руке"),true);
        return true;
    }
    public static void tick(ServerPlayer p) {
        Meal meal=MEALS.get(p.getUUID());
        if(meal==null) return;
        var r=RaceService.race(p);
        ItemStack held=p.getItemInHand(meal.hand);
        if(!p.isAlive() || r==null || !r.id.equals("forged") || !ItemStack.isSameItemSameComponents(held,meal.identity) || held.isEmpty()) { MEALS.remove(p.getUUID()); return; }
        if(p.tickCount%8==0) p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.GENERIC_EAT,SoundSource.PLAYERS,.5f,.8f);
        if(RaceService.time(p)<meal.finish) return;
        MEALS.remove(p.getUUID());
        if(!p.isCreative()) held.shrink(1);
        var data=p.getFoodData();
        data.setFoodLevel(Math.min(20,data.getFoodLevel()+Math.max(0,meal.food.food())));
        data.setSaturation(Math.min(data.getFoodLevel(),data.getSaturationLevel()+Math.max(0,meal.food.saturation())));
        if(meal.ore) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,0));
        if(meal.netherite) for(var effect : List.of(MobEffects.DAMAGE_RESISTANCE,MobEffects.REGENERATION,MobEffects.DAMAGE_BOOST))
            p.addEffect(new MobEffectInstance(effect,ModConfig.INSTANCE.netheriteEffectSeconds*20,1));
        p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.PLAYER_BURP,SoundSource.PLAYERS,.5f,.9f);
    }
    public static void logout(ServerPlayer p) { MEALS.remove(p.getUUID()); }
}
