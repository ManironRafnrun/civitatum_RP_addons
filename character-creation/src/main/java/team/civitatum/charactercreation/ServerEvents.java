package team.civitatum.charactercreation;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.minecraft.world.item.ArmorItem;
import team.civitatum.charactercreation.power.Channels;
import team.civitatum.charactercreation.power.PassiveAttributes;
import team.civitatum.charactercreation.power.PassiveTicks;
import team.civitatum.charactercreation.power.Powers;
import team.civitatum.charactercreation.power.Traits;

@EventBusSubscriber(modid = Civitatum.ID)
public final class ServerEvents {
    /** Passives that depend on the moment (a storm, water, a forest, an empty hand) are recomputed this often. */
    private static final int PASSIVE_EVERY = 5;

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
        e.getDispatcher().register(Commands.literal("civitatum").then(race).then(SignCommands.build()).then(config));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        RaceService.apply(p); RaceNetwork.sync(p);
        for (ServerPlayer other : p.server.getPlayerList().getPlayers())
            if (other != p) RaceNetwork.send(p,new RaceNetwork.Clientbound(0,other.getUUID(),0,other.getData(Civitatum.STATE).json()));
        if (!p.getData(Civitatum.STATE).character().selected)
            p.sendSystemMessage(Civitatum.message("welcome"));
        else if (p.getData(Civitatum.STATE).character().birthsign.equals(BirthsignCatalog.NONE))
            p.sendSystemMessage(Civitatum.message("sign_missing"));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if(e.getEntity() instanceof ServerPlayer p) RaceService.logout(p); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { if(e.getEntity() instanceof ServerPlayer p) { RaceService.apply(p); RaceNetwork.sync(p); } }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if(e.getEntity() instanceof ServerPlayer p) { RaceService.apply(p); RaceNetwork.sync(p); } }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        var original = e.getOriginal();
        p.setData(Civitatum.STATE, original.getData(Civitatum.STATE));
        Powers.clear(p);
        // «Тетрадь» of the mechanist: half of the levels stay. With keepInventory vanilla keeps them all anyway.
        if (e.isWasDeath() && Traits.isRace(p, "forged")
            && !p.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))
            p.giveExperienceLevels(original.experienceLevel / 2);
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
        Channels.tick(p);
        PassiveTicks.tick(p);
        if(p.tickCount % PASSIVE_EVERY == 0) PassiveAttributes.apply(p);
        var race = RaceService.race(p);
        if (race != null) CompatPatches.tick(p, race);
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
        } else if(RaceService.forbidsBoots(e.getEntity()) && e.getItemStack().getItem() instanceof ArmorItem a && a.getEquipmentSlot() == EquipmentSlot.FEET) {
            e.setCanceled(true); e.setCancellationResult(InteractionResult.FAIL);
            if(e.getEntity() instanceof ServerPlayer p) p.displayClientMessage(RaceService.bootsMessage(p),true);
        }
    }
    /** Last: the Ancestor's reprieve may still cancel the death. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void death(LivingDeathEvent e) {
        if(!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) Powers.clear(p);
    }
    @SubscribeEvent public static void target(LivingChangeTargetEvent e) { CompatPatches.target(e); }
}
