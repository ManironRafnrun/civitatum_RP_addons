package team.civitatum.charactercreation.gametest;

import static team.civitatum.charactercreation.gametest.Case.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.Foods;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.EnderManAngerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.RaceService;
import team.civitatum.charactercreation.ServerEvents;
import team.civitatum.charactercreation.power.Channels;
import team.civitatum.charactercreation.power.ModEffects;
import team.civitatum.charactercreation.power.RacePowers;
import team.civitatum.charactercreation.power.TemporaryBlocks;
import team.civitatum.charactercreation.power.WorldEvents;
import team.civitatum.charactercreation.power.Zones;

/** The passives and powers of the eighteen races ("10b", part I). */
@GameTestHolder(Civitatum.ID)
@PrefixGameTestTemplate(false)
public final class RaceTests {
    /** The price a villager asks for a trade of 10 emeralds after its special prices are set for {@code p}. */
    static int price(Case c, Player p) {
        Villager v = c.mob(EntityType.VILLAGER, 20, 20, true);
        var offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 10), new ItemStack(Items.BREAD), 12, 1, .05f));
        v.setOffers(offers);
        call(v, Villager.class, "updateSpecialPrices", new Class<?>[]{Player.class}, p);
        int cost = v.getOffers().getFirst().getCostA().getCount();
        v.discard();
        return cost;
    }

    /** The anvil's price to repair a worn diamond pickaxe with a diamond. */
    static int anvil(Player p) {
        var menu = new AnvilMenu(0, p.getInventory());
        var pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.setDamageValue(1000);
        pick.set(DataComponents.REPAIR_COST, 7);
        menu.getSlot(0).set(pick);
        menu.getSlot(1).set(new ItemStack(Items.DIAMOND));
        menu.createResult();
        return menu.getCost();
    }

    /** What a block does to the player's movement; ZERO when it does not hold. */
    static Vec3 stuck(Player p, Block block) {
        setField(p, Entity.class, "stuckSpeedMultiplier", Vec3.ZERO);
        p.makeStuckInBlock(block.defaultBlockState(), new Vec3(.25, .05, .25));
        Vec3 r = field(p, Entity.class, "stuckSpeedMultiplier");
        setField(p, Entity.class, "stuckSpeedMultiplier", Vec3.ZERO);
        return r;
    }

    static int blocks(Case c, BlockPos center, int r, Block block) {
        int n = 0;
        for (var pos : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 4, r)))
            if (c.level.getBlockState(pos).is(block)) n++;
        return n;
    }

    static int items(Case c, BlockPos at, Item item) {
        int n = 0;
        for (var e : c.level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4)))
            if (e.getItem().is(item)) n += e.getItem().getCount();
        return n;
    }

    // ------------------------------------------------------------------ human

    @GameTest(template = TEMPLATE, batch = "human")
    public static void humanPassives(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("human", "human", "none", 16, 16);
        check(near(c.exhaustion(p), .85), "hunger x0.85", c.exhaustion(p));
        check(price(c, p) == 9, "villager price 10 -> 9", price(c, p));
        c.end(1);
    }

    @GameTest(template = TEMPLATE, batch = "human_nabat")
    public static void humanNabat(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("nabat", "human", "none", 16, 16);
        var ally = c.player("nabat_ally", "human", "none", 19, 16);
        var stranger = c.player("nabat_str", "human", "none", 13, 16);
        c.team("gt_nabat", p, ally);
        var phantom = c.mob(EntityType.PHANTOM, 16, 26, true);
        RacePowers.activate(p);
        check(p.hasEffect(ModEffects.NABAT) && ally.hasEffect(ModEffects.NABAT)
            && !stranger.hasEffect(ModEffects.NABAT),
            "nabat on self and teammate, not on a stranger");
        check(ally.getEffect(ModEffects.NABAT).getDuration() > 1150
            && near(ally.getAttributeValue(Attributes.MOVEMENT_SPEED), .12),
            "nabat = Speed I for 60 s", ally.getAttributeValue(Attributes.MOVEMENT_SPEED));
        check(phantom.isRemoved(), "phantoms in 32 gone");
        check(Math.abs(c.cooldown(p) - 24000) <= 2, "once a day (1200 s)", c.cooldown(p));
        c.at(10, () -> {
            RacePowers.activate(p);
            check(p.hasSaid("ability_cooldown"), "second press says cooldown", p.messages);
        });
        c.end(12);
    }

    // ------------------------------------------------------------------ dwarf

    @GameTest(template = TEMPLATE, batch = "dwarf", timeoutTicks = 300)
    public static void dwarfPassives(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("dwarf", "dwarf", "none", 16, 16);
        var human = c.player("dwarf_ctl", "human", "none", 10, 16);
        int dwarf = anvil(p), base = anvil(human);
        check(dwarf == Math.max(1, Math.round(base * .75f)), "anvil x0.75", dwarf + " vs " + base);
        var pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.onCraftedBy(c.level, p, 1);
        pick.onCraftedBy(c.level, p, 1);
        var plain = new ItemStack(Items.DIAMOND_PICKAXE);
        plain.onCraftedBy(c.level, human, 1);
        check(pick.getMaxDamage() == 1717 && plain.getMaxDamage() == 1561, "own crafts +10% durability, once",
            pick.getMaxDamage() + " / " + plain.getMaxDamage());
        c.at(210, () -> {
            var nv = p.getEffect(MobEffects.NIGHT_VISION);
            check(nv != null && !nv.isVisible() && !nv.showIcon(), "night vision without particles or icon", nv);
        });
        c.end(212);
    }

    @GameTest(template = TEMPLATE, batch = "dwarf_seal")
    public static void dwarfSeal(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("seal", "dwarf", "none", 16, 16);
        c.block(16, 1, 19, Blocks.STONE);
        c.look(p, Vec3.atCenterOf(c.pos(16, 1, 19)));
        RacePowers.activate(p);
        BlockPos chain = c.pos(16, 1, 18);
        check(c.level.getBlockState(chain).is(Blocks.CHAIN), "chain on the block", c.level.getBlockState(chain));
        check(Zones.isSealed(c.level, Vec3.atCenterOf(chain).add(3, 2, -3)) && !Zones.isSealed(c.level,
            Vec3.atCenterOf(chain).add(6, 0, 0)),
            "a 9x9x9 zone");
        var inside = EntityType.ZOMBIE.create(c.level);
        inside.moveTo(Vec3.atCenterOf(chain).add(2, 0, 0));
        EventHooks.finalizeMobSpawn(inside, c.level, c.level.getCurrentDifficultyAt(chain), MobSpawnType.NATURAL, null);
        var outside = EntityType.ZOMBIE.create(c.level);
        outside.moveTo(Vec3.atCenterOf(chain).add(10, 0, 0));
        EventHooks.finalizeMobSpawn(outside, c.level, c.level.getCurrentDifficultyAt(chain), MobSpawnType.NATURAL,
            null);
        var cow = EntityType.COW.create(c.level);
        cow.moveTo(Vec3.atCenterOf(chain).add(2, 0, 0));
        EventHooks.finalizeMobSpawn(cow, c.level, c.level.getCurrentDifficultyAt(chain), MobSpawnType.NATURAL, null);
        check(inside.isSpawnCancelled() && !outside.isSpawnCancelled() && !cow.isSpawnCancelled(),
            "no monster appears in the seal; outside and animals do");
        c.level.destroyBlock(chain, true, p);
        c.at(2, () -> check(items(c, chain, Items.CHAIN) == 0 && !TemporaryBlocks.isTemporary(c.level, chain),
            "the broken chain drops nothing", items(c, chain, Items.CHAIN)));
        c.end(3);
    }

    // ------------------------------------------------------------------ orc

    @GameTest(template = TEMPLATE, batch = "orc")
    public static void orcPassives(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("orc", "orc", "none", 16, 16);
        var human = c.player("orc_ctl", "human", "none", 10, 16);
        check(p.getMaxHealth() == 24, "24 HP", p.getMaxHealth());
        check(PiglinAi.isWearingGold(p) && !PiglinAi.isWearingGold(human), "piglins wait for an unarmed orc");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        check(!PiglinAi.isWearingGold(p), "a sword in hand ends it");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        check(!PiglinAi.isWearingGold(p), "an axe in hand ends it");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        c.end(1);
    }

    @GameTest(template = TEMPLATE, batch = "orc", timeoutTicks = 60)
    public static void orcBerserk(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("berserk", "orc", "none", 16, 16);
        RacePowers.activate(p);
        check(p.hasEffect(ModEffects.RAGE) && near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 1.5)
            && near(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 1)
                && near(p.getAttributeValue(Attributes.BLOCK_BREAK_SPEED), 1.4),
            "rage: +50% damage, no knockback, Haste II", p.getAttributeValue(Attributes.ATTACK_DAMAGE));
        p.removeEffect(ModEffects.RAGE);
        c.at(3, () -> {
            check(p.hasEffect(ModEffects.COMEDOWN) && p.getEffect(ModEffects.COMEDOWN).getDuration() > 590,
                "comedown follows even when milk took the rage", p.getActiveEffects());
            p.getFoodData().setExhaustion(0);
        });
        c.at(23, () -> check(Math.abs(p.getFoodData().getExhaustionLevel() - .3f) < .03, "comedown = Hunger III",
            p.getFoodData().getExhaustionLevel()));
        c.end(24);
    }

    // ------------------------------------------------------------------ firbolg

    @GameTest(template = TEMPLATE, batch = "firbolg")
    public static void firbolgPassives(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("firbolg", "firbolg", "none", 16, 16);
        check(p.getMaxHealth() == 24 && near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), .09), "24 HP, speed x0.9");
        check(stuck(p, Blocks.OAK_LEAVES).equals(Vec3.ZERO) && stuck(p, Blocks.VINE).equals(Vec3.ZERO)
            && !stuck(p, Blocks.COBWEB).equals(Vec3.ZERO), "leaves and vines do not hold, webs do");
        c.end(1);
    }

    /** Random ticks are off in a game test world, so any growth is the firbolg's. */
    @GameTest(template = TEMPLATE, batch = "firbolg_saplings", timeoutTicks = 1300)
    public static void firbolgSaplings(GameTestHelper h) {
        var c = new Case(h);
        c.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(0,
            c.level.getServer());
        c.player("saplings", "firbolg", "none", 16, 16);
        List<BlockPos> near = new ArrayList<>(), far = new ArrayList<>();
        for (int dx = 0; dx < 7; dx++)
            for (int dz = 0; dz < 7; dz++) {
                near.add(c.pos(20 + dx, 1, 20 + dz));
                far.add(c.pos(20 + dx + 30, 1, 20 + dz));
            }
        for (var list : List.of(near, far))
            for (var s : list) {
                c.level.setBlock(s.below(), Blocks.DIRT.defaultBlockState(), 2);
                c.level.setBlock(s, Blocks.OAK_SAPLING.defaultBlockState(), 2);
                c.level.setBlock(s.above(4), Blocks.SEA_LANTERN.defaultBlockState(), 2);
            }
        c.at(1200, () -> {
            int grown = 0, farGrown = 0;
            for (var s : near) if (isGrown(c, s)) grown++;
            for (var s : far) if (isGrown(c, s)) farGrown++;
            check(grown > 0 && farGrown == 0, "saplings in 16 grow, far ones do not", grown + " / " + farGrown);
        });
        c.end(1201);
    }

    static boolean isGrown(Case c, BlockPos s) {
        var state = c.level.getBlockState(s);
        return !state.is(Blocks.OAK_SAPLING) || state.getValue(SaplingBlock.STAGE) > 0;
    }

    @GameTest(template = TEMPLATE, batch = "firbolg_roots", timeoutTicks = 300)
    public static void firbolgRoots(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("roots", "firbolg", "none", 16, 8);
        var z = c.mob(EntityType.ZOMBIE, 16, 13, true);
        c.look(p, c.vec(16.5, .5, 13.5));
        RacePowers.activate(p);
        check(z.hasEffect(ModEffects.ROOTS) && near(z.getAttributeValue(Attributes.MOVEMENT_SPEED), 0), "enemy rooted",
            z.getActiveEffects());
        BlockPos center = z.blockPosition();
        check(blocks(c, center, 5, Blocks.MANGROVE_ROOTS) == 48, "a wall of roots around 7x7, two high", blocks(c,
            center, 5, Blocks.MANGROVE_ROOTS));
        c.at(105, () -> check(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
            && z.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 1,
            "then Slowness II", z.getActiveEffects()));
        c.at(215, () -> check(blocks(c, center, 5, Blocks.MANGROVE_ROOTS) == 0, "roots gone after 10 s"));
        c.end(216);
    }

    // ------------------------------------------------------------------ goblin

    @GameTest(template = TEMPLATE, batch = "goblin")
    public static void goblinPassives(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("goblin", "goblin", "none", 16, 16);
        var human = c.player("goblin_ctl", "human", "none", 10, 16);
        check(near(p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), .5), "falls x0.5");
        check(price(c, p) == 12, "villagers +15%: 10 -> 12", price(c, p));
        int g = anvil(p), base = anvil(human);
        check(g == Math.max(1, Math.round(base * .75f)), "anvil x0.75", g + " vs " + base);
        p.getInventory().clearContent();
        var grid = new SimpleContainer(9);
        grid.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        for (int i = 0; i < 400; i++)
            NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(p, new ItemStack(Items.BUCKET), grid));
        int back = p.getInventory().countItem(Items.IRON_INGOT);
        check(back > 70 && back < 130, "a 25% chance to get an ingredient back", back + "/400");
        c.end(1);
    }

    @GameTest(template = TEMPLATE, batch = "goblin_bell", timeoutTicks = 60)
    public static void goblinBell(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("bell", "goblin", "none", 16, 16);
        var near = c.player("bell_near", "human", "none", 20, 20);
        var z = c.mob(EntityType.ZOMBIE, 21, 16, true);
        z.setTarget(p);
        RacePowers.activate(p);
        BlockPos bell = p.blockPosition();
        check(c.level.getBlockState(bell).is(Blocks.BELL), "a bell", c.level.getBlockState(bell));
        c.at(25, () -> {
            check(z.hasEffect(ModEffects.RING) && z.getTarget() == null, "enemies ring and lose the goblin",
                z.getActiveEffects());
            var event = NeoForge.EVENT_BUS.post(new PlayerSpawnPhantomsEvent(near, 3));
            check(event.getResult() == PlayerSpawnPhantomsEvent.Result.DENY, "no phantoms near the bell");
            c.level.destroyBlock(bell, true, p);
        });
        c.at(28, () -> check(items(c, bell, Items.BELL) == 0, "the broken bell drops nothing"));
        c.end(29);
    }

    // ------------------------------------------------------------------ mechanist

    @GameTest(template = TEMPLATE, batch = "forged", timeoutTicks = 60)
    public static void forged(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("forged", "forged", "none", 16, 16);
        var human = c.player("forged_ctl", "human", "none", 10, 16);
        check(!p.addEffect(new MobEffectInstance(MobEffects.POISON, 100))
            && !p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100))
            && !p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100))
                && p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100)),
            "immune to poison, blindness and nausea only");
        p.removeEffect(MobEffects.REGENERATION);
        p.setHealth(5);
        human.setHealth(5);
        MobEffects.HEAL.value().applyInstantenousEffect(null, null, p, 0, 1);
        MobEffects.HEAL.value().applyInstantenousEffect(null, null, human, 0, 1);
        check(near(p.getHealth(), 8) && near(human.getHealth(), 9), "healing potions x0.75", p.getHealth() + " / "
            + human.getHealth());
        var reborn = c.player("forged_new", "forged", "none", 20, 16);
        p.experienceLevel = 10;
        ServerEvents.clone(new PlayerEvent.Clone(reborn, p, true));
        check(reborn.experienceLevel == 5, "half of the levels stay after death", reborn.experienceLevel);
        RacePowers.activate(p);
        check(p.hasEffect(ModEffects.REPLACEMENT) && near(c.loss(p, c.level.damageSources().generic(), 10), 6),
            "replacement = Resistance II", c.loss(p, c.level.damageSources().generic(), 10));
        p.removeEffect(ModEffects.REPLACEMENT);
        c.at(3, () -> check(p.hasEffect(MobEffects.WEAKNESS) && p.getEffect(MobEffects.WEAKNESS).getDuration() > 1190,
            "then Weakness I for 60 s", p.getActiveEffects()));
        c.end(4);
    }

    // ------------------------------------------------------------------ hobbit

    @GameTest(template = TEMPLATE, batch = "hobbit", timeoutTicks = 100)
    public static void hobbit(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("hobbit", "hobbit", "none", 16, 16);
        var human = c.player("hobbit_ctl", "human", "none", 10, 16);
        check(p.getMaxHealth() == 16, "16 HP", p.getMaxHealth());
        check(near(c.exhaustion(p), .8), "hunger x0.8", c.exhaustion(p));
        for (var q : List.of(p, human)) {
            q.getFoodData().setFoodLevel(14);
            q.getFoodData().setSaturation(0);
            q.eat(c.level, new ItemStack(Items.BREAD), Foods.BREAD);
        }
        check(near(p.getFoodData().getSaturationLevel(), human.getFoodData().getSaturationLevel() + 1),
            "food +1 saturation");
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        c.at(2, () -> check(p.getItemBySlot(EquipmentSlot.FEET).isEmpty()
            && p.getInventory().countItem(Items.LEATHER_BOOTS) == 1,
            "boots come off into the inventory"));
        c.at(3, () -> {
            p.getFoodData().setFoodLevel(5);
            RacePowers.activate(p);
            check(p.getFoodData().getFoodLevel() == 20 && p.hasEffect(ModEffects.BREAKFAST)
                && near(p.getAbsorptionAmount(), 4),
                "second breakfast: full, absorption 2 hearts", p.getAbsorptionAmount());
            p.setHealth(10);
        });
        c.at(63, () -> check(p.getHealth() > 10, "and Regeneration I", p.getHealth()));
        c.end(64);
    }

    // ------------------------------------------------------------------ elves

    @GameTest(template = TEMPLATE, batch = "astral", timeoutTicks = 300)
    public static void astral(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("astral", "astral_elf", "none", 16, 16);
        var z = c.mob(EntityType.ZOMBIE, 46, 16, true);
        var cow = c.mob(EntityType.COW, 16, 26, true);
        RacePowers.activate(p);
        check(z.hasEffect(MobEffects.GLOWING) && cow.hasEffect(MobEffects.GLOWING) && !p.hasEffect(MobEffects.GLOWING),
            "constellation: everything in 48 glows");
        c.at(210, () -> check(p.hasEffect(MobEffects.NIGHT_VISION), "night vision"));
        c.end(211);
    }

    @GameTest(template = TEMPLATE, batch = "night_elf", timeoutTicks = 300)
    public static void nightElf(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("night", "night_elf", "none", 16, 16);
        var human = c.player("night_ctl", "human", "none", 10, 16);
        var z = c.mob(EntityType.ZOMBIE, 21, 16, true);
        check(near(p.getVisibilityPercent(z), .75) && near(human.getVisibilityPercent(z), 1), "seen 25% later");
        check(NeoForge.EVENT_BUS.post(new PlayerSpawnPhantomsEvent(p,
            3)).getResult() == PlayerSpawnPhantomsEvent.Result.DENY, "no phantoms");
        z.setTarget(p);
        RacePowers.activate(p);
        check(p.hasEffect(ModEffects.SHADOW) && z.getTarget() == null, "shadow: mobs lose the elf");
        z.setTarget(p);
        check(z.getTarget() == null, "and cannot take it again");
        check(!CommonHooks.onPlayerAttackTarget(p, z), "the elf cannot attack");
        float before = z.getHealth();
        z.hurt(c.level.damageSources().playerAttack(p), 5);
        check(z.getHealth() == before, "nor hurt");
        c.at(210, () -> check(p.hasEffect(MobEffects.NIGHT_VISION), "night vision"));
        c.end(211);
    }

    @GameTest(template = TEMPLATE, batch = "dark_elf", timeoutTicks = 100)
    public static void darkElfPassives(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("dark", "dark_elf", "none", 16, 16);
        var human = c.player("dark_ctl", "human", "none", 10, 16);
        var spider = c.mob(EntityType.SPIDER, 20, 20, true);
        var cave = c.mob(EntityType.CAVE_SPIDER, 20, 12, true);
        spider.setTarget(p);
        cave.setTarget(p);
        check(spider.getTarget() == null && cave.getTarget() == null, "spiders do not attack first");
        spider.setTarget(human);
        check(spider.getTarget() == human, "they attack others");
        spider.setTarget(null);
        spider.setLastHurtByMob(p);
        spider.setTarget(p);
        check(spider.getTarget() == p, "a provoked spider attacks");
        check(stuck(p, Blocks.COBWEB).equals(Vec3.ZERO) && !stuck(human, Blocks.COBWEB).equals(Vec3.ZERO),
            "webs do not hold");
        // the game test arena stands near the bottom of the world, below y=32
        c.at(25, () -> {
            var u = p.getEffect(ModEffects.UNDERGROUND);
            check(p.getY() < 32 && u != null && !u.isVisible() && p.getMaxHealth() == 24,
                "below y=32: Underground, +2 hearts, no particles", u);
            p.teleportTo(p.getX(), 100, p.getZ());
        });
        c.at(50, () -> check(!p.hasEffect(ModEffects.UNDERGROUND) && p.getMaxHealth() == 20, "off above y=32",
            p.getMaxHealth()));
        c.end(51);
    }

    @GameTest(template = TEMPLATE, batch = "dark_elf_knot", timeoutTicks = 200)
    public static void darkElfKnot(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("knot", "dark_elf", "none", 16, 4);
        var zs = new ArrayList<Zombie>();
        for (int i = 0; i < 4; i++) zs.add(c.mob(EntityType.ZOMBIE, 16, 8 + 2 * i, true));
        RacePowers.activate(p);
        int knotted = (int) zs.stream().filter(z -> z.hasEffect(ModEffects.KNOT)).count();
        check(knotted == 3 && !zs.get(3).hasEffect(ModEffects.KNOT), "the three nearest are knotted", knotted);
        BlockPos web = zs.getFirst().blockPosition();
        check(c.level.getBlockState(web).is(Blocks.COBWEB), "a cobweb where they stand");
        c.at(85, () -> check(zs.getFirst().hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
            && zs.getFirst().getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 1, "then Slowness II"));
        c.at(170, () -> check(!c.level.getBlockState(web).is(Blocks.COBWEB), "the web is gone after 8 s"));
        c.end(171);
    }

    @GameTest(template = TEMPLATE, batch = "wood_elf")
    public static void woodElfDrops(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("wood", "wood_elf", "none", 16, 16);
        var human = c.player("wood_ctl", "human", "none", 10, 16);
        for (var killer : List.of(p, human)) {
            var cow = c.mob(EntityType.COW, 19, 19, true);
            var drops = new ArrayList<ItemEntity>();
            drops.add(new ItemEntity(c.level, cow.getX(), cow.getY(), cow.getZ(), new ItemStack(Items.BEEF, 2)));
            drops.add(new ItemEntity(c.level, cow.getX(), cow.getY(), cow.getZ(), new ItemStack(Items.LEATHER, 1)));
            NeoForge.EVENT_BUS.post(new LivingDropsEvent(cow, c.level.damageSources().playerAttack(killer), drops,
                true));
            int beef = drops.get(0).getItem().getCount(), leather = drops.get(1).getItem().getCount();
            check(killer == p ? beef == 3 && leather == 2 : beef == 2 && leather == 1,
                "+1 meat and +1 hide for the elf only",
                killer.getScoreboardName() + " " + beef + " " + leather);
            cow.discard();
        }
        int doubled = 0, control = 0;
        for (int i = 0; i < 400; i++) {
            for (var breaker : List.of(p, human)) {
                var drops = new ArrayList<ItemEntity>();
                drops.add(new ItemEntity(c.level, p.getX(), p.getY(), p.getZ(), new ItemStack(Items.STICK)));
                NeoForge.EVENT_BUS.post(new BlockDropsEvent(c.level, c.pos(18, 1, 18),
                    Blocks.OAK_LEAVES.defaultBlockState(), null,
                    drops, breaker, ItemStack.EMPTY));
                if (drops.getFirst().getItem().getCount() == 2) {
                    if (breaker == p) doubled++;
                    else control++;
                }
            }
        }
        check(doubled > 70 && doubled < 130 && control == 0, "plants +25% for the elf only", doubled + "/400, control "
            + control);
        c.end(1);
    }

    @GameTest(template = TEMPLATE, batch = "wood_elf_trance", timeoutTicks = 300)
    public static void woodElfTrance(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("trance", "wood_elf", "none", 16, 16);
        RacePowers.activate(p);
        check(Channels.isRunning(p) && near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), 0),
            "the trance holds the elf still");
        check(near(c.loss(p, c.level.damageSources().generic(), 4), 6), "and it takes 50% more damage");
        c.at(40, () -> {
            RacePowers.release(p);
            var r = p.getEffect(MobEffects.REGENERATION);
            check(r != null && r.getAmplifier() == 0 && r.getDuration() > 95 && !p.hasEffect(ModEffects.TRANCE)
                && !Channels.isRunning(p),
                "let go early: Regeneration I for 5 s", r);
            check(near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), .1), "free to walk again");
            c.ready(p);
            p.removeEffect(MobEffects.REGENERATION);
            RacePowers.activate(p);
        });
        c.at(246, () -> {
            check(p.hasEffect(ModEffects.TRANCE), "a full trance", p.getActiveEffects());
            check(p.blockPosition().equals(p.getRespawnPosition()), "sets the respawn point", p.getRespawnPosition());
            var z = c.mob(EntityType.ZOMBIE, 21, 16, true);
            z.setTarget(p);
            check(z.getTarget() == null, "the undead ignore the elf");
        });
        c.end(247);
    }

    // ------------------------------------------------------------------ dragonkin

    @GameTest(template = TEMPLATE, batch = "fire")
    public static void firePassives(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("fire", "fire_dragonborn", "none", 16, 16);
        check(near(c.loss(p, c.level.damageSources().onFire(), 4), 1), "fire x0.25");
        check(near(c.exhaustion(p), 1.25), "hunger x1.25", c.exhaustion(p));
        c.end(1);
    }

    @GameTest(template = TEMPLATE, batch = "fire_furnace", timeoutTicks = 200)
    public static void fireFurnace(GameTestHelper h) {
        var c = new Case(h);
        c.player("furnace", "fire_dragonborn", "none", 16, 16);
        BlockPos hot = c.pos(19, 1, 19);
        c.level.setBlock(hot, Blocks.FURNACE.defaultBlockState(), 3);
        var be = (FurnaceBlockEntity) c.level.getBlockEntity(hot);
        be.setItem(0, new ItemStack(Items.RAW_IRON, 16));
        be.setItem(1, new ItemStack(Items.COAL, 4));
        int[] first = new int[2];
        c.at(40, () -> {
            first[0] = field(c.level.getBlockEntity(hot), AbstractFurnaceBlockEntity.class, "cookingProgress");
            first[1] = field(c.level.getBlockEntity(hot), AbstractFurnaceBlockEntity.class, "litTime");
        });
        c.at(160, () -> {
            int cooked = (int) field(c.level.getBlockEntity(hot), AbstractFurnaceBlockEntity.class,
                "cookingProgress") - first[0];
            int burnt = first[1] - (int) field(c.level.getBlockEntity(hot), AbstractFurnaceBlockEntity.class,
                "litTime");
            // vanilla: 120 progress and 120 fuel ticks in 120 ticks
            check(cooked >= 140 && cooked <= 160, "cooks 1.25 times faster", cooked);
            check(burnt >= 98 && burnt <= 110, "fuel lasts 15% longer (burns ~104 of 120)", burnt);
        });
        c.end(161);
    }

    @GameTest(template = TEMPLATE, batch = "fire_breath", timeoutTicks = 100)
    public static void fireBreath(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("breath", "fire_dragonborn", "none", 16, 13);
        var z = c.mob(EntityType.ZOMBIE, 16, 16, true);
        z.getAttribute(Attributes.ARMOR).setBaseValue(0);
        c.look(p, z.getEyePosition());
        RacePowers.activate(p);
        check(Channels.isRunning(p) && near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), .05),
            "breathing halves speed");
        c.at(62, () -> {
            check(near(z.getMaxHealth() - z.getHealth(), 12) && z.isOnFire(), "six pulses of 2 and fire",
                z.getHealth());
            check(p.hasEffect(ModEffects.HOARSE) && !Channels.isRunning(p), "then the Rasp (Weakness I)");
        });
        c.end(63);
    }

    @GameTest(template = TEMPLATE, batch = "frost", timeoutTicks = 150)
    public static void frost(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("frost", "frost_dragonborn", "none", 16, 16);
        var drinker = c.player("frost_drk", "human", "none", 10, 16);
        check(near(c.loss(p, c.level.damageSources().freeze(), 4), 0), "no freezing damage");
        check(near(c.loss(p, c.level.damageSources().onFire(), 4), 6), "fire x1.5");
        check(PowderSnowBlock.canEntityWalkOnPowderSnow(p) && !PowderSnowBlock.canEntityWalkOnPowderSnow(drinker),
            "powder snow holds");
        p.setTicksFrozen(100);
        c.at(2, () -> check(p.getTicksFrozen() == 0, "never freezes"));
        var potion = PotionContents.createItemStack(Items.POTION, Potions.STRENGTH);
        WorldEvents.markBrewed(p, potion);
        var copy = potion.copy();
        EventHooks.onItemUseFinish(drinker, copy, 0, potion.finishUsingItem(c.level, drinker));
        var s = drinker.getEffect(MobEffects.DAMAGE_BOOST);
        check(s != null && s.getDuration() >= 4490, "own potions last 25% longer", s);
        var stand = new SimpleContainer(5);
        stand.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));
        var menu = new BrewingStandMenu(0, p.getInventory(), stand, new SimpleContainerData(2));
        p.getInventory().clearContent();
        menu.quickMoveStack(p, 0);
        var moved = p.getInventory().items.stream().filter(i -> i.is(Items.POTION)).findFirst().orElse(ItemStack.EMPTY);
        var data = moved.get(DataComponents.CUSTOM_DATA);
        check(data != null && data.copyTag().getBoolean("civitatum_alchemist"), "a shift-clicked potion is marked too");
        BlockPos water = c.pos(16, 1, 20);
        c.level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
        var z = c.mob(EntityType.ZOMBIE, 17, 20, true);
        c.look(p, Vec3.atCenterOf(water.below()).add(0, .4, 0));
        c.at(3, () -> {
            RacePowers.activate(p);
            check(c.level.getBlockState(water).is(Blocks.FROSTED_ICE), "rime freezes water");
            check(z.hasEffect(ModEffects.RIME) && near(z.getAttributeValue(Attributes.MOVEMENT_SPEED), 0),
                "and roots enemies");
        });
        c.at(115, () -> check(c.level.getBlockState(water).is(Blocks.WATER), "water again after 5 s"));
        c.end(116);
    }

    @GameTest(template = TEMPLATE, batch = "storm", timeoutTicks = 200)
    public static void storm(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("storm", "storm_dragonborn", "none", 16, 8);
        check(near(c.loss(p, c.level.damageSources().lightningBolt(), 5), 0), "lightning does not hurt");
        check(NeoForge.EVENT_BUS.post(new EntityStruckByLightningEvent(p,
            EntityType.LIGHTNING_BOLT.create(c.level))).isCanceled(),
            "nor sets on fire");
        check(near(p.getAttributeValue(NeoForgeMod.SWIM_SPEED), 1.25) && p.getMaxAirSupply() == 900,
            "swims x1.25, air +30 s");
        c.level.setWeatherParameters(0, 6000, true, true);
        c.at(130, () -> {
            check(near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), .11), "+10% speed in a thunderstorm",
                p.getAttributeValue(Attributes.MOVEMENT_SPEED));
            c.level.setWeatherParameters(6000, 0, false, false);
            BlockPos target = c.pos(16, 0, 16);
            c.look(p, Vec3.atCenterOf(target).add(0, .5, 0));
        });
        c.at(132, () -> {
            RacePowers.activate(p);
            var bolts = c.level.getEntitiesOfClass(LightningBolt.class, new AABB(c.pos(16, 1, 16)).inflate(3));
            check(!bolts.isEmpty(), "a lightning bolt where the dragonkin looks");
        });
        c.end(133);
    }

    @GameTest(template = TEMPLATE, batch = "end", timeoutTicks = 60)
    public static void end(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("end", "end_dragonborn", "none", 16, 22);
        var e = c.mob(EntityType.ENDERMAN, 20, 22, true);
        check(NeoForge.EVENT_BUS.post(new EnderManAngerEvent(e, p)).isCanceled(), "endermen not angered by a gaze");
        e.setTarget(p);
        check(e.getTarget() == null, "nor attack first");
        e.setLastHurtByMob(p);
        e.setTarget(p);
        check(e.getTarget() == p, "a provoked enderman attacks");
        e.discard();
        c.level.setBlock(p.blockPosition(), Blocks.WATER.defaultBlockState(), 3);
        c.at(10, () -> {
            check(near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), .085), "Slowness I in water");
            Vec3 at = p.position();
            RacePowers.activate(p);
            check(p.position().equals(at) && c.cooldown(p) <= 0 && p.hasSaid("power.step_water"), "no Step in water");
            for (var w : BlockPos.betweenClosed(p.blockPosition().offset(-3, 0, -3), p.blockPosition().offset(3, 1, 3)))
                if (!c.level.getFluidState(w).isEmpty()) c.level.setBlock(w, Blocks.AIR.defaultBlockState(), 2);
        });
        c.at(20, () -> {
            c.look(p, p.getEyePosition().add(0, 0, -10));
            Vec3 from = p.position();
            RacePowers.activate(p);
            double moved = p.position().distanceTo(from);
            check(moved > 15.5 && moved < 16.5, "a step of 16 along the gaze", moved);
            check(p.hasEffect(ModEffects.STEP) && near(c.loss(p, c.level.damageSources().fall(), 10), 0),
                "and no fall damage");
            check(near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), .1), "normal speed out of water");
        });
        c.end(21);
    }

    // ------------------------------------------------------------------ turs, kairi, borian

    @GameTest(template = TEMPLATE, batch = "turs")
    public static void turs(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("turs", "orthosian", "none", 16, 16);
        check(p.getMaxHealth() == 24 && near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), .09), "24 HP, speed x0.9");
        check(near(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 1)
            && near(p.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE), 1),
            "cannot be knocked back");
        var a = c.mob(EntityType.ZOMBIE, 26, 16, true);
        var b = c.mob(EntityType.SKELETON, 1, 21, true);
        RacePowers.activate(p);
        check(a.getTarget() == p && b.getTarget() == p, "ancestor's name: mobs in 32 come");
        check(near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 7), "Strength II");
        check(near(c.loss(p, c.level.damageSources().generic(), 10), 8), "Resistance I");
        c.end(1);
    }

    @GameTest(template = TEMPLATE, batch = "kairi", timeoutTicks = 350)
    public static void kairi(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("kairi", "jahar", "none", 16, 16);
        check(near(p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), .5), "falls x0.5");
        check(near(c.loss(p, c.level.damageSources().onFire(), 4), 8), "fire x2");
        Villager v = c.mob(EntityType.VILLAGER, 19, 16, true);
        p.getInventory().clearContent();
        var sell = new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD, 10), 12, 1, .05f);
        for (int i = 0; i < 300; i++) NeoForge.EVENT_BUS.post(new TradeWithVillagerEvent(p, sell, v));
        int extra = p.getInventory().countItem(Items.EMERALD);
        check(extra > 400 && extra < 500, "+15% emeralds when selling", extra + " over 300 x 10");
        p.getInventory().clearContent();
        NeoForge.EVENT_BUS.post(new TradeWithVillagerEvent(p,
            new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD), 12, 1, .05f), v));
        check(p.getInventory().countItem(Items.EMERALD) == 0, "nothing extra when buying");
        RacePowers.activate(p);
        check(near(c.loss(p, c.level.damageSources().onFire(), 4), 0), "the Mask: no fire");
        check(near(c.loss(p, c.level.damageSources().generic(), 4), 6), "the Mask: +2 damage taken");
        check(near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 7)
            && near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), .14),
            "the Mask: Strength II, Speed II");
        c.at(301, () -> {
            var n = p.getEffect(MobEffects.CONFUSION);
            check(p.hasEffect(ModEffects.AFTERMATH) && n != null && !n.showIcon()
                && near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), .07),
                "then the Aftermath with one icon", p.getActiveEffects());
        });
        c.end(302);
    }

    @GameTest(template = TEMPLATE, batch = "borian", timeoutTicks = 60)
    public static void borian(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("borian", "forest_algnor", "none", 16, 16);
        var city = c.player("borian_c", "city_algnor", "none", 10, 16);
        check(near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 3)
            && near(p.getAttributeValue(Attributes.ATTACK_KNOCKBACK), 1)
            && near(city.getAttributeValue(Attributes.ATTACK_DAMAGE), 3),
                "horns: unarmed +2 and knockback, city ones too");
        check(stuck(p, Blocks.SWEET_BERRY_BUSH).equals(Vec3.ZERO), "bushes do not hold");
        check(RaceService.forbidsBoots(p) && RaceService.forbidsBoots(city), "no boots");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        c.at(6, () -> {
            check(near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 1), "no horns with something in hand");
            check(near(p.getAttributeValue(Attributes.ARMOR), 1.5), "a helmet gives half",
                p.getAttributeValue(Attributes.ARMOR));
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        });
        var z = c.mob(EntityType.ZOMBIE, 16, 12, true);
        c.at(12, () -> {
            c.look(p, p.getEyePosition().add(0, 0, -10));
            Vec3 from = p.position();
            RacePowers.activate(p);
            double moved = p.position().distanceTo(from);
            check(moved > 7.5 && moved < 8.5, "the ram dashes 8", moved);
            check(z.hasEffect(ModEffects.RAM) && near(z.getAttributeValue(Attributes.MOVEMENT_SPEED), 0)
                && near(z.getAttributeValue(Attributes.ATTACK_DAMAGE), 0), "and stuns", z.getActiveEffects());
        });
        c.end(13);
    }

    @GameTest(template = TEMPLATE, batch = "borian_forest", timeoutTicks = 60)
    public static void borianForest(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("forest", "forest_algnor", "none", 16, 16);
        // the game test world is flat plains: the arena is made a forest
        // getBiome picks among the neighbouring 4-block quarters (fuzzy zoom), below too: a margin of one quarter all
        // round; 40x16x40 stays under the command's limit of 32768 blocks
        BlockPos from = c.pos(-4, -4, -4), to = c.pos(Case.SIZE + 3, 11, Case.SIZE + 3);
        var server = c.level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
            "fillbiome " + from.getX() + " " + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY()
                + " " + to.getZ() + " minecraft:forest");
        c.at(8, () -> check(near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), .11), "+10% speed in a forest",
            p.getAttributeValue(Attributes.MOVEMENT_SPEED) + " in "
                + c.level.getBiome(p.blockPosition()).getRegisteredName() + " at " + p.blockPosition()));
        c.end(9);
    }

}
