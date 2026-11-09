package team.civitatum.charactercreation.power;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.EnderManAngerEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import team.civitatum.charactercreation.BirthsignCatalog;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.RaceNetwork;
import team.civitatum.charactercreation.RaceService;
import team.civitatum.charactercreation.power.PlayerPowers.ChannelKind;

/** Passives and effects that change fights: damage, targets, effects that may or may not land, death. */
@EventBusSubscriber(modid = Civitatum.ID)
public final class CombatEvents {
    /**
     * Spiders of the dark elf's neutrality: vanilla has no spider tag, so the pack's own tag lists the vanilla spiders
     * and the eleven of Spider Overhaul (its {@code spider_overhaul:spiders}).
     */
    public static final TagKey<EntityType<?>> SPIDERS = TagKey.create(Registries.ENTITY_TYPE,
        Civitatum.res("spiders"));
    /** Spider Overhaul's cobweb effect; the dark elf is not held by webs. */
    private static final ResourceLocation WEBBED = ResourceLocation.fromNamespaceAndPath("spider_overhaul", "webbed");
    /** Resistance takes 20% per level (MobEffects.DAMAGE_RESISTANCE). */
    private static final float RESISTANCE_I = .8f, RESISTANCE_II = .6f;
    /** The Mask: "получаемый урон +2". */
    private static final float MASK_PRICE = 2;
    /** The trance: "получаемый урон +50%". */
    private static final float TRANCE_PRICE = 1.5f;
    /** The undead strike the Ancestor again a minute after the Ancestor last struck them ([ДОПУЩЕНИЕ] of "10b"). */
    private static final long ANCESTOR_MEMORY = 60 * 20;
    /** The night elf is seen 25% later. */
    private static final double NIGHT_ELF_VISIBILITY = .75;

    @SubscribeEvent
    public static void incoming(LivingIncomingDamageEvent e) {
        var source = e.getSource();
        if (source.getEntity() instanceof LivingEntity attacker && isStunned(attacker)) {
            e.setCanceled(true);
            return;
        }
        if (source.getEntity() instanceof ServerPlayer attacker) {
            if (attacker.hasEffect(ModEffects.SHADOW)) {
                e.setCanceled(true);
                return;
            }
            if (e.getEntity().getType().is(EntityTypeTags.UNDEAD))
                PlayerPowers.of(attacker).lastUndeadHit = RaceService.time(attacker);
        }
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        String race = Traits.race(p);
        if (source.is(DamageTypeTags.IS_FIRE) && p.hasEffect(ModEffects.MASK)
            || source.is(DamageTypeTags.IS_FREEZING) && race.equals("frost_dragonborn")
            || source.is(DamageTypeTags.IS_LIGHTNING) && race.equals("storm_dragonborn")
            || source.is(DamageTypeTags.IS_FALL) && p.hasEffect(ModEffects.STEP)) {
            e.setCanceled(true);
            return;
        }
        float amount = e.getAmount();
        if (source.is(DamageTypeTags.IS_FIRE)) amount *= switch (race) {
            case "fire_dragonborn" -> .25f;
            case "frost_dragonborn" -> 1.5f;
            case "jahar" -> 2;
            default -> 1;
        };
        if (!source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
            if (p.hasEffect(ModEffects.REPLACEMENT)) amount *= RESISTANCE_II;
            if (p.hasEffect(ModEffects.ANCESTOR_NAME)) amount *= RESISTANCE_I;
        }
        if (Channels.isRunning(p, ChannelKind.TRANCE)) amount *= TRANCE_PRICE;
        if (p.hasEffect(ModEffects.MASK)) amount += MASK_PRICE;
        e.setAmount(amount);
    }

    @SubscribeEvent
    public static void damaged(LivingDamageEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getNewDamage() > 0) Channels.disturbed(p, false);
    }

    @SubscribeEvent
    public static void attack(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (p.hasEffect(ModEffects.SHADOW) || isStunned(p)) {
            e.setCanceled(true);
            return;
        }
        Channels.disturbed(p, true);
    }

    /** The bound cannot use items ("не использует предметы"). */
    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem e) {
        if (isStunned(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock e) {
        if (isStunned(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void useEntity(PlayerInteractEvent.EntityInteract e) {
        if (isStunned(e.getEntity())) e.setCanceled(true);
    }

    private static boolean isStunned(LivingEntity e) {
        return e.hasEffect(ModEffects.BOUND) || e.hasEffect(ModEffects.RAM);
    }

    @SubscribeEvent
    public static void applicable(MobEffectEvent.Applicable e) {
        var instance = e.getEffectInstance();
        var entity = e.getEntity();
        String race = Traits.race(entity);
        if (race.equals("forged") && (instance.is(MobEffects.POISON) || instance.is(MobEffects.CONFUSION)
            || instance.is(MobEffects.BLINDNESS))
            || race.equals("dark_elf") && instance.getEffect().unwrapKey()
                .map(k -> k.location().equals(WEBBED)).orElse(false)) {
            e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (Traits.isSign(entity, "merchant") && isHalved(instance)) {
            e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            Effects.give(entity, new MobEffectInstance(instance.getEffect(), Math.max(1, instance.getDuration() / 2),
                instance.getAmplifier(), instance.isAmbient(), instance.isVisible(), instance.showIcon()));
        }
    }

    /**
     * "Положительные зелья действуют вдвое меньше": a beneficial effect with a duration, not one of the powers
     * ("расовые и знаковые составные эффекты из этого правила исключить"), not a beacon's or conduit's (ambient,
     * renewed every few seconds anyway).
     */
    private static boolean isHalved(MobEffectInstance instance) {
        return !Effects.isGiving() && instance.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL
            && !instance.getEffect().value().isInstantenous() && !instance.isInfiniteDuration()
            && !instance.isAmbient() && !ModEffects.isOwn(instance.getEffect());
    }

    @SubscribeEvent
    public static void expired(MobEffectEvent.Expired e) { Aftermaths.ended(e.getEntity(), e.getEffectInstance()); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void removed(MobEffectEvent.Remove e) {
        if (!e.isCanceled()) Aftermaths.ended(e.getEntity(), e.getEffectInstance());
    }

    @SubscribeEvent
    public static void target(LivingChangeTargetEvent e) {
        var mob = e.getEntity();
        if (isStunned(mob)) {
            e.setNewAboutToBeSetTarget(null);
            return;
        }
        if (!(e.getNewAboutToBeSetTarget() instanceof ServerPlayer p)) return;
        boolean provoked = mob.getLastHurtByMob() == p;
        String race = Traits.race(p);
        boolean undead = mob.getType().is(EntityTypeTags.UNDEAD);
        if (p.hasEffect(ModEffects.SHADOW)
            || race.equals("dark_elf") && mob.getType().is(SPIDERS) && !provoked
            || race.equals("end_dragonborn") && mob instanceof EnderMan && !provoked
            || undead && p.hasEffect(ModEffects.TRANCE)
            || undead && Traits.isSign(p, "ancestor") && mob.getType() != EntityType.WITHER
                && RaceService.time(p) - PlayerPowers.of(p).lastUndeadHit > ANCESTOR_MEMORY)
            e.setNewAboutToBeSetTarget(null);
    }

    @SubscribeEvent
    public static void enderman(EnderManAngerEvent e) {
        if (Traits.isRace(e.getPlayer(), "end_dragonborn")) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void lightning(EntityStruckByLightningEvent e) {
        if (Traits.isRace(e.getEntity(), "storm_dragonborn")) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void visibility(LivingEvent.LivingVisibilityEvent e) {
        if (Traits.isRace(e.getEntity(), "night_elf")) e.modifyVisibility(NIGHT_ELF_VISIBILITY);
    }

    @SubscribeEvent
    public static void heal(LivingHealEvent e) {
        if (Traits.isSign(e.getEntity(), BirthsignCatalog.SERPENT)) e.setAmount(e.getAmount() / 2);
    }

    /** «Отсрочка» of the Ancestor: once a day a mortal blow leaves 1 HP and Resistance II for 3 s. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void death(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) return;
        var state = p.getData(Civitatum.STATE);
        long now = RaceService.time(p);
        if (Traits.isSign(p, "ancestor") && state.reprieveUntil() <= now
            && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            e.setCanceled(true);
            p.setHealth(1);
            Effects.give(p, MobEffects.DAMAGE_RESISTANCE, 3 * 20, 1);
            p.setData(Civitatum.STATE, state.withReprieve(now + BirthsignCatalog.REPRIEVE_TICKS));
            RaceNetwork.sync(p);
            p.displayClientMessage(Civitatum.message("power.reprieve"), true);
        }
    }

    private CombatEvents() {}
}
