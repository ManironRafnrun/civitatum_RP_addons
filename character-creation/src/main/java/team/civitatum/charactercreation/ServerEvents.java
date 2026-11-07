package team.civitatum.charactercreation;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.minecraft.world.item.ArmorItem;

@EventBusSubscriber(modid = Civitatum.ID)
public final class ServerEvents {
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        var choose = Commands.literal("choose")
            .executes(c -> { RaceService.open(c.getSource().getPlayerOrException(), true, false); return 1; })
            .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                .executes(c -> { RaceService.open(EntityArgument.getPlayer(c,"player"), true, false); return 1; }));
        var race = Commands.literal("race").then(choose);
        race.then(Commands.literal("naked").then(Commands.argument("value",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->{
            RaceService.naked(c.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"value"));return 1;
        })));
        race.then(Commands.literal("looks").executes(c -> { RaceService.open(c.getSource().getPlayerOrException(),false,false); return 1; })
            .then(Commands.argument("player",EntityArgument.player()).requires(s -> s.hasPermission(2))
                .executes(c -> { RaceService.open(EntityArgument.getPlayer(c,"player"),false,false); return 1; })));
        race.then(Commands.literal("reload").executes(c -> { RaceService.reset(c.getSource().getPlayerOrException()); return 1; })
            .then(Commands.argument("player",EntityArgument.player()).requires(s -> s.hasPermission(2))
                .executes(c -> { RaceService.reset(EntityArgument.getPlayer(c,"player")); return 1; })));
        var config=Commands.literal("configreload").requires(s -> s.hasPermission(2)).executes(c -> {
            ModConfig.load();for(var p:c.getSource().getServer().getPlayerList().getPlayers()) {RaceService.apply(p);RaceNetwork.sync(p);}return 1;
        });
        e.getDispatcher().register(Commands.literal("civitatum").then(race).then(config));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        RaceService.apply(p); RaceNetwork.sync(p);
        for (ServerPlayer other : p.server.getPlayerList().getPlayers())
            if (other != p) RaceNetwork.send(p,new RaceNetwork.Clientbound(0,other.getUUID(),0,other.getData(Civitatum.STATE).json()));
        if (!p.getData(Civitatum.STATE).character().selected)
            p.sendSystemMessage(Civitatum.message("welcome"));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if(e.getEntity() instanceof ServerPlayer p) RaceService.logout(p); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { if(e.getEntity() instanceof ServerPlayer p) { RaceService.apply(p); RaceNetwork.sync(p); } }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if(e.getEntity() instanceof ServerPlayer p) { RaceService.apply(p); RaceNetwork.sync(p); } }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            p.setData(Civitatum.STATE,e.getOriginal().getData(Civitatum.STATE));
            RaceAbilities.clearTransient(p);
        }
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e) {
        if(e.getEntity() instanceof ServerPlayer p && e.getTarget() instanceof ServerPlayer target)
            RaceNetwork.send(p,new RaceNetwork.Clientbound(0,target.getUUID(),0,target.getData(Civitatum.STATE).json()));
    }
    @SubscribeEvent public static void name(PlayerEvent.NameFormat e) {
        var c=e.getEntity().getData(Civitatum.STATE).character();
        if(c.selected && !c.name.isBlank()) e.setDisplayname(Component.literal(c.name));
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post e) {
        if(!(e.getEntity() instanceof ServerPlayer p)) return;
        RaceAbilities.tick(p); MetalEating.tick(p);
        var character=p.getData(Civitatum.STATE).character();
        if(character.selected && character.birthsign.equals("lord") && p.tickCount%160==0 && p.getFoodData().getFoodLevel()>=6 && p.isAlive())p.heal(1);
        if(p.tickCount % 5 == 0) RaceService.updateSpeed(p);
        RaceService.enforceBoots(p);
    }
    @SubscribeEvent public static void size(EntityEvent.Size e) {
        if (!(e.getEntity() instanceof Player p) || !p.isAddedToLevel() || e.getPose() != Pose.CROUCHING) return;
        var c=p.getData(Civitatum.STATE).character();
        if(c.selected && c.height >= 2.2 && e.getNewSize().height() > 1.8f)
            e.setNewSize(EntityDimensions.scalable(e.getNewSize().width(),1.8f).withEyeHeight(1.62f));
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickItem e) {
        if(e.getItemStack().is(Civitatum.SHARD.get()) || e.getItemStack().is(Civitatum.MIRROR.get())) {
            e.setCanceled(true); e.setCancellationResult(InteractionResult.SUCCESS);
            if(e.getEntity() instanceof ServerPlayer p) RaceService.openCosmetic(p,e.getItemStack().getItem());
        } else if(e.getEntity() instanceof ServerPlayer p && MetalEating.start(p,e.getHand())) {
            e.setCanceled(true); e.setCancellationResult(InteractionResult.CONSUME);
        } else if(RaceService.forbidsBoots(e.getEntity()) && e.getItemStack().getItem() instanceof ArmorItem a && a.getEquipmentSlot() == EquipmentSlot.FEET) {
            e.setCanceled(true); e.setCancellationResult(InteractionResult.FAIL);
            if(e.getEntity() instanceof ServerPlayer p) p.displayClientMessage(RaceService.bootsMessage(p),true);
        }
    }
    @SubscribeEvent public static void interactBlock(PlayerInteractEvent.RightClickBlock e) {
        if(e.getLevel().isClientSide && !e.getEntity().isShiftKeyDown() && e.getEntity().canEat(false)
            && "forged".equals(e.getEntity().getData(Civitatum.STATE).character().race)
            && e.getEntity().getData(Civitatum.STATE).character().selected && MetalEating.food(e.getItemStack())!=null) {
            e.setCanceled(true);e.setCancellationResult(InteractionResult.CONSUME);
        }
        if(e.getEntity() instanceof ServerPlayer p && !p.isShiftKeyDown() && MetalEating.start(p,e.getHand())) {
            e.setCanceled(true); e.setCancellationResult(InteractionResult.CONSUME);
        }
    }
    @SubscribeEvent public static void useFood(LivingEntityUseItemEvent.Start e) {
        if(e.getEntity() instanceof Player p && "forged".equals(p.getData(Civitatum.STATE).character().race)
            && p.getData(Civitatum.STATE).character().selected && e.getItem().get(DataComponents.FOOD) != null) {
            e.setCanceled(true);
            if(p instanceof ServerPlayer sp) sp.displayClientMessage(Civitatum.message("forged_food"),true);
        }
    }
    @SubscribeEvent public static void ate(LivingEntityUseItemEvent.Finish e) {
        if(e.getEntity() instanceof ServerPlayer p && "hobbit".equals(p.getData(Civitatum.STATE).character().race)) {
            var food=e.getItem().get(DataComponents.FOOD);
            if(food!=null) p.getFoodData().setSaturation(Math.min(p.getFoodData().getFoodLevel(),p.getFoodData().getSaturationLevel()+food.saturation()*.2f));
        }
    }
    @SubscribeEvent public static void attack(AttackEntityEvent e) { if(e.getEntity() instanceof ServerPlayer p) RaceAbilities.attacked(p); }
    @SubscribeEvent public static void incoming(LivingIncomingDamageEvent e) {
        float factor=1;
        var source=e.getSource();
        if(e.getEntity() instanceof ServerPlayer p) {
            var r=RaceService.race(p);
            if(r!=null) {
                String sign=p.getData(Civitatum.STATE).character().birthsign;
                if(source.is(DamageTypeTags.IS_FIRE) && sign.equals("lord"))factor*=1.25f;
                if(source.is(DamageTypeTags.IS_FALL) && sign.equals("thief"))factor*=.9f;
                if(source.is(DamageTypeTags.IS_FALL) && java.util.List.of("astral_elf","goblin","hobbit","forest_algnor","city_algnor").contains(r.id)) factor*=.8f;
                boolean fire=source.is(DamageTypeTags.IS_FIRE);
                if(fire) factor*=switch(r.id) { case "fire_dragonborn" -> .75f; case "frost_dragonborn","jahar" -> 1.25f; case "end_dragonborn" -> 1.15f; default -> 1; };
                if(source.is(DamageTypes.FREEZE)) factor*=r.id.equals("fire_dragonborn") ? 1.25f : r.id.equals("frost_dragonborn") ? .5f : 1;
                if(source.is(DamageTypes.LIGHTNING_BOLT) && r.id.equals("storm_dragonborn")) factor*=.5f;
                if(source.is(DamageTypes.DROWN) && r.id.equals("storm_dragonborn")) factor*=1.2f;
                if(source.is(DamageTypeTags.IS_PROJECTILE) && r.id.equals("end_dragonborn")) factor*=.85f;
            }
        }
        if(source.getEntity() instanceof ServerPlayer attacker) {
            var r=RaceService.race(attacker);
            if(r!=null) {
                boolean melee=source.is(DamageTypes.PLAYER_ATTACK);
                if(melee && attacker.getData(Civitatum.STATE).character().birthsign.equals("warrior"))factor*=1.1f;
                if(melee && r.id.equals("orc")) factor*=1.1f;
                if(melee && r.id.equals("firbolg")) factor*=.9f;
                if(r.id.equals("orc") && (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow)) factor*=.9f;
            }
        }
        e.setAmount(e.getAmount()*factor);
    }
    @SubscribeEvent public static void damaged(LivingDamageEvent.Post e) { if(e.getEntity() instanceof ServerPlayer p && e.getNewDamage()>0) RaceAbilities.hurt(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) {
        if(e.getEntity() instanceof ServerPlayer p) {
            var state=p.getData(Civitatum.STATE);
            if(state.character().selected && state.character().race.equals("jahar") && e.getSource().is(DamageTypeTags.IS_FIRE)) {
                p.setData(Civitatum.STATE,new RaceState(state.character(),state.curseDeaths()+1,state.cooldownUntil()));
                p.sendSystemMessage(Civitatum.message("jahar_curse"));
            }
            RaceAbilities.clearTransient(p); MetalEating.logout(p);
        }
    }
    @SubscribeEvent public static void target(LivingChangeTargetEvent e) { CompatPatches.target(e); }
    @SubscribeEvent public static void vibration(VanillaGameEvent e) {
        if(e.getVanillaEvent() == net.minecraft.world.level.gameevent.GameEvent.STEP
            && e.getCause() instanceof Player p && RaceAbilities.silentSteps(p)) e.setCanceled(true);
    }
}
