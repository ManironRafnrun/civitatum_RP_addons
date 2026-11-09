package team.civitatum.charactercreation.power;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.RaceService;

/** Passives that touch the world: drops, trade, spawns, crafting, potions, mounts; and the ticks of the powers. */
@EventBusSubscriber(modid = Civitatum.ID)
public final class WorldEvents {
    /** The wood elf: "с шансом 25% удвоить один предмет" of a plant. */
    private static final float HERBALIST_CHANCE = .25f;
    /** The goblin: "шанс 25% вернуть один ингредиент при крафте". */
    private static final float WORKSHOP_CHANCE = .25f;
    /** The kairi is paid 15% more emeralds. */
    private static final double TRADE_BONUS = .15;
    /** Potions brewed by a frost dragonborn last 25% longer. */
    private static final double ALCHEMIST_BONUS = 1.25;
    /** The mark on a brewed potion: who drinks it gets the longer effect. */
    private static final String ALCHEMIST_TAG = "civitatum_alchemist";

    private static final Set<Entity> SPIRITS = new HashSet<>();

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        var server = e.getServer();
        Aftermaths.tick();
        Zones.tick(server);
        TemporaryBlocks.tick(server);
        if (server.getTickCount() % 20 == 0) SPIRITS.removeIf(w -> {
            if (w.isRemoved()) return true;
            if (!SignPowers.isSpiritGone(w, server)) return false;
            w.discard();
            return true;
        });
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !SignPowers.isSpirit(e.getEntity())) return;
        if (SignPowers.isSpiritGone(e.getEntity(), e.getLevel().getServer())) e.setCanceled(true);
        else SPIRITS.add(e.getEntity());
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent e) {
        if (SignPowers.isSpirit(e.getEntity())) {
            e.setCanceled(true);
            return;
        }
        if (!(e.getSource().getEntity() instanceof ServerPlayer killer) || !Traits.isRace(killer, "wood_elf")
            || !(e.getEntity() instanceof Animal)) return;
        // "Животные дают +1 мясо и +1 шкуру": one more of the meat and of the hide the animal already drops.
        boolean meat = false, hide = false;
        for (var drop : e.getDrops()) {
            var stack = drop.getItem();
            if (!meat && stack.is(Tags.Items.FOODS_RAW_MEAT)) {
                stack.grow(1);
                meat = true;
            } else if (!hide && (stack.is(Tags.Items.LEATHERS) || stack.is(Items.RABBIT_HIDE))) {
                stack.grow(1);
                hide = true;
            }
        }
    }

    @SubscribeEvent
    public static void experience(LivingExperienceDropEvent e) {
        if (SignPowers.isSpirit(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void blockDrops(BlockDropsEvent e) {
        if (TemporaryBlocks.isTemporary(e.getLevel(), e.getPos())) {
            e.setCanceled(true);
            TemporaryBlocks.forget(e.getLevel(), e.getPos());
            return;
        }
        if (!(e.getBreaker() instanceof ServerPlayer p) || !Traits.isRace(p, "wood_elf") || e.getDrops().isEmpty()
            || !isPlant(e.getState()) || p.getRandom().nextFloat() >= HERBALIST_CHANCE) return;
        e.getDrops().getFirst().getItem().grow(1);
    }

    /** "Трава, листва, ягоды, грибы". */
    private static boolean isPlant(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.REPLACEABLE_BY_TREES)
            || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.CAVE_VINES) || s.is(Blocks.CAVE_VINES_PLANT)
            || s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM);
    }

    /** A power's block caught in an explosion stays: blown up, it would drop as an item. */
    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate e) {
        e.getAffectedBlocks().removeIf(pos -> TemporaryBlocks.isTemporary(e.getLevel(), pos));
    }

    @SubscribeEvent
    public static void spawn(FinalizeSpawnEvent e) {
        if (e.getEntity().getType().getCategory() == MobCategory.MONSTER
            && Zones.isSealed(e.getEntity().level(), e.getEntity().position())) e.setSpawnCancelled(true);
    }

    @SubscribeEvent
    public static void phantoms(PlayerSpawnPhantomsEvent e) {
        if (Traits.isRace(e.getEntity(), "night_elf") || Zones.isNearBell(e.getEntity()))
            e.setResult(PlayerSpawnPhantomsEvent.Result.DENY);
    }

    /** The Wanderer: "кровати не задают точку возрождения" — beds and anchors; a forced point (a command) stays. */
    @SubscribeEvent
    public static void setSpawn(PlayerSetSpawnEvent e) {
        if (Traits.isSign(e.getEntity(), "wanderer") && !e.isForced() && e.getNewSpawn() != null) e.setCanceled(true);
    }

    /** A rewritten sky costs a night without sleep. */
    @SubscribeEvent
    public static void sleep(CanPlayerSleepEvent e) {
        var p = e.getEntity();
        if (p.getData(Civitatum.STATE).sleeplessUntil() > RaceService.time(p))
            e.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
    }

    @SubscribeEvent
    public static void trade(TradeWithVillagerEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Traits.isRace(p, "jahar")) return;
        var result = e.getMerchantOffer().getResult();
        if (!result.is(Items.EMERALD)) return;
        // 15% more on average: a whole part, and the rest as a chance, so selling for one emerald is not +100%.
        double extra = result.getCount() * TRADE_BONUS;
        int count = (int) extra + (p.getRandom().nextDouble() < extra - (int) extra ? 1 : 0);
        if (count > 0) {
            var bonus = new ItemStack(Items.EMERALD, count);
            if (!p.getInventory().add(bonus)) p.drop(bonus, false);
        }
    }

    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Traits.isRace(p, "goblin")
            || p.getRandom().nextFloat() >= WORKSHOP_CHANCE) return;
        var grid = e.getInventory();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            var ingredient = grid.getItem(i);
            if (ingredient.isEmpty() || ingredient.hasCraftingRemainingItem()) continue;
            var back = ingredient.copyWithCount(1);
            if (!p.getInventory().add(back)) p.drop(back, false);
            return;
        }
    }

    /** A potion taken by a click; a shift-click hands the event a copy, so BrewingStandMenuMixin marks those. */
    @SubscribeEvent
    public static void brewed(PlayerBrewedPotionEvent e) { markBrewed(e.getEntity(), e.getStack()); }

    /** The frost dragonborn's potion: "Зелья, которые ты варишь, длятся на 25% дольше". */
    public static void markBrewed(Player p, ItemStack potion) {
        if (Traits.isRace(p, "frost_dragonborn") && potion.has(DataComponents.POTION_CONTENTS))
            CustomData.update(DataComponents.CUSTOM_DATA, potion, tag -> tag.putBoolean(ALCHEMIST_TAG, true));
    }

    @SubscribeEvent
    public static void drank(LivingEntityUseItemEvent.Finish e) {
        var stack = e.getItem();
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.copyTag().getBoolean(ALCHEMIST_TAG)) return;
        var contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        var entity = e.getEntity();
        for (var effect : contents.getAllEffects()) {
            var active = entity.getEffect(effect.getEffect());
            if (active == null || effect.getEffect().value().isInstantenous()) continue;
            Effects.give(entity, new MobEffectInstance(active.getEffect(),
                (int) Math.round(effect.getDuration() * ALCHEMIST_BONUS), active.getAmplifier(), active.isAmbient(),
                active.isVisible(), active.showIcon()));
        }
    }

    /** The Beast: "приручение всегда удаётся"; a horse is tamed on the first ride. */
    @SubscribeEvent
    public static void mount(EntityMountEvent e) {
        if (e.isMounting() && e.getEntityMounting() instanceof ServerPlayer p && Traits.isSign(p, "beast")
            && e.getEntityBeingMounted() instanceof AbstractHorse horse && !horse.isTamed())
            horse.tameWithName(p);
    }

    private WorldEvents() {}
}
