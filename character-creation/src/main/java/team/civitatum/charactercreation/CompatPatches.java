package team.civitatum.charactercreation;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.bus.api.*;

/** Optional, isolated integrations. No external API is required by race gameplay. */
public final class CompatPatches {
    private static boolean arsReady;
    private static Method manaGetter;
    /** Looked up on first use and kept: mana regen is computed every tick for every player. */
    private static Method currentMana, removeMana, syncMana;
    private static Method teamsApi, teamManager, sameTeamCheck;
    private static final Set<String> WARNED = new HashSet<>();
    @SuppressWarnings({"unchecked","rawtypes"})
    public static void init() {
        if(ModConfig.INSTANCE.arsNouveauPatch && ModList.get().isLoaded("ars_nouveau")) {
            try {
                Class<?> caps=Class.forName("com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry");
                manaGetter=caps.getMethod("getMana",LivingEntity.class); arsReady=true;
                Class event=Class.forName("com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent");
                Method getEntity=event.getMethod("getEntity"), getRegen=event.getMethod("getRegen");
                Method setRegen=event.getMethod("setRegen",double.class);
                NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL,false,event,(Consumer<Event>)e -> {
                    try {
                        Object entity=getEntity.invoke(e);
                        if(!(entity instanceof Player p)) return;
                        RaceDefinition r=RaceService.race(p);
                        if(r==null) return;
                        double factor=r.id.equals("dwarf") ? .70 : r.id.equals("astral_elf") ? 1.15 : r.elf() ? 1.10 : 1;
                        if(p.getData(Civitatum.STATE).character().birthsign.equals("mage"))factor*=1.15;
                        if(factor!=1) {
                            double regen=((Number)getRegen.invoke(e)).doubleValue();
                            setRegen.invoke(e,regen*factor);
                        }
                    } catch(ReflectiveOperationException ex) { warn("ars_regen",ex); }
                });
                Civitatum.LOGGER.info("Ars Nouveau mana and race regen patch enabled");
            } catch(ReflectiveOperationException ex) { warn("ars_api",ex); arsReady=false; }
        }
    }
    public static boolean arsLoaded() { return arsReady; }
    public static boolean payMana(ServerPlayer p,int cost) {
        try {
            Object mana=manaGetter.invoke(null,p);
            if(mana==null) return false;
            if(currentMana==null) {
                currentMana=mana.getClass().getMethod("getCurrentMana");
                removeMana=mana.getClass().getMethod("removeMana",double.class);
                syncMana=mana.getClass().getMethod("syncToClient",ServerPlayer.class);
            }
            double current=((Number)currentMana.invoke(mana)).doubleValue();
            if(current<cost) return false;
            removeMana.invoke(mana,(double)cost);
            syncMana.invoke(mana,p);
            return true;
        } catch(ReflectiveOperationException ex) { warn("ars_payment",ex); return false; }
    }
    public static boolean sameTeam(Player a,Player b) {
        if(!ModConfig.INSTANCE.ftbTeamsPatch || !ModList.get().isLoaded("ftbteams")) return false;
        try {
            if(teamsApi==null) teamsApi=Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI").getMethod("api");
            Object instance=teamsApi.invoke(null);
            if(teamManager==null) teamManager=instance.getClass().getMethod("getManager");
            Object manager=teamManager.invoke(instance);
            if(sameTeamCheck==null)
                sameTeamCheck=manager.getClass().getMethod("arePlayersInSameTeam",UUID.class,UUID.class);
            return (boolean)sameTeamCheck.invoke(manager,a.getUUID(),b.getUUID());
        } catch(ReflectiveOperationException ex) { warn("ftb_teams",ex); return false; }
    }
    public static void target(LivingChangeTargetEvent e) {
        if(!(e.getNewAboutToBeSetTarget() instanceof Player p)) return;
        var r=RaceService.race(p);
        if(r==null) return;
        var id=BuiltInRegistries.ENTITY_TYPE.getKey(e.getEntity().getType());
        if(ModConfig.INSTANCE.automatonPatch && r.id.equals("orthosian") && ModConfig.INSTANCE.automatonNamespaces.contains(id.getNamespace())) e.setNewAboutToBeSetTarget(null);
    }
    private static final String GIFT_KEY="civitatum_kobold_gifts";
    public static void tick(ServerPlayer p,RaceDefinition r) {
        if(!r.dragon() || p.isCreative() || p.isSpectator()) return;
        if(!ModConfig.INSTANCE.dragonHunterEntityIds.isEmpty() && p.tickCount%20==0) {
            for(Mob hunter:p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(24),m ->
                ModConfig.INSTANCE.dragonHunterEntityIds.contains(BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString())))
                if(hunter.isAlive() && !hunter.isAlliedTo(p)) hunter.setTarget(p);
        }
        if(!ModConfig.INSTANCE.koboldsPatch || p.tickCount%100!=0) return;
        long now=RaceService.time(p);
        var history=p.getPersistentData().getCompound(GIFT_KEY);
        long cooldown=ModConfig.INSTANCE.koboldPairCooldownSeconds*20L;
        // An expired entry behaves exactly like a missing one; dropping it keeps gone kobolds out of the player's NBT.
        for(String key:List.copyOf(history.getAllKeys())) if(now-history.getLong(key)>=cooldown) history.remove(key);
        for(Mob mob : p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(5),m ->
            BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getNamespace().equals("kobolds"))) {
            String key=mob.getUUID().toString();
            long last=history.getLong(key);
            if(last!=0 && now-last<cooldown) continue;
            history.putLong(key,now);
            if(p.getRandom().nextFloat()>.25f) continue;
            var item=switch(p.getRandom().nextInt(6)) {
                case 0 -> Items.IRON_NUGGET; case 1 -> Items.GOLD_NUGGET; case 2 -> Items.COPPER_INGOT;
                case 3 -> Items.APPLE; case 4 -> Items.BREAD; default -> Items.GLOW_BERRIES;
            };
            var gift=new ItemStack(item);
            if(!p.getInventory().add(gift)) p.drop(gift,false);
        }
        p.getPersistentData().put(GIFT_KEY,history);
    }
    private static void warn(String key,Exception e) {
        if(WARNED.add(key)) Civitatum.LOGGER.warn("Optional compatibility patch {} is unavailable: {}",key,e.toString());
    }
}
