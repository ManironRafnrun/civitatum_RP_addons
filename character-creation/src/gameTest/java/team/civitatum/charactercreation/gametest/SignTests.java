package team.civitatum.charactercreation.gametest;

import static team.civitatum.charactercreation.gametest.Case.*;
import static team.civitatum.charactercreation.gametest.RaceTests.anvil;
import static team.civitatum.charactercreation.gametest.RaceTests.price;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.Foods;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import team.civitatum.charactercreation.BirthsignCatalog;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.power.Channels;
import team.civitatum.charactercreation.power.Effects;
import team.civitatum.charactercreation.power.ModEffects;
import team.civitatum.charactercreation.power.SignPowers;

/**
 * The thirteen birthsigns ("10b", part III). The sign bearers are dwarves: no racial price or hunger of their own mixes
 * into the sign's numbers.
 */
@GameTestHolder(Civitatum.ID)
@PrefixGameTestTemplate(false)
public final class SignTests {
    @GameTest(template = TEMPLATE, batch = "guardians")
    public static void guardians(GameTestHelper h) {
        var c = new Case(h);
        var spear = c.player("spear", "dwarf", "spear", 16, 16);
        check(near(spear.getAttributeValue(Attributes.ATTACK_DAMAGE), 4), "Spear: +3 attack");
        var messenger = c.player("messenger", "dwarf", "messenger", 10, 16);
        check(near(messenger.getAttributeValue(Attributes.MOVEMENT_SPEED), .115)
            && near(messenger.getAttributeValue(Attributes.STEP_HEIGHT), 1.6),
            "Messenger: speed +15%, step +1");
        for (var id : new String[]{"spear", "smith", "messenger", "ancestor"})
            check(!BirthsignCatalog.get(id).hasPower(), "no power key for " + id);
        c.end(1);
    }

    static int wear(Case c, Player p) {
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        for (int i = 0; i < 1000; i++) sword.hurtAndBreak(1, c.level, p, item -> {});
        return sword.getDamageValue();
    }

    @GameTest(template = TEMPLATE, batch = "smith")
    public static void smith(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("smith", "human", "smith", 16, 16);
        var human = c.player("smith_ctl", "human", "none", 10, 16);
        var dwarf = c.player("smith_dw", "dwarf", "smith", 20, 16);
        int w = wear(c, p), base = wear(c, human);
        check(w > 440 && w < 560 && base == 1000, "wear x0.5", w + " vs " + base);
        int a = anvil(p), ab = anvil(dwarf), ac = anvil(human);
        check(a == Math.max(1, Math.round(ac * .5f)), "anvil x0.5", a + " vs " + ac);
        check(ab == Math.max(1, Math.round(ac * .375f)), "with a dwarf x0.375", ab + " vs " + ac);
        c.end(1);
    }

    @GameTest(template = TEMPLATE, batch = "shepherd", timeoutTicks = 60)
    public static void shepherd(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("shepherd", "dwarf", "shepherd", 16, 16);
        var human = c.player("shep_ctl", "dwarf", "none", 10, 16);
        var close = c.mob(EntityType.ZOMBIE, 24, 16, true);
        var far = c.mob(EntityType.ZOMBIE, 16, 31, true);
        for (var q : new Player[]{p, human}) {
            q.getFoodData().setFoodLevel(10);
            q.getFoodData().setSaturation(0);
            q.eat(c.level, new ItemStack(Items.COOKED_BEEF), Foods.COOKED_BEEF);
        }
        check(p.getFoodData().getFoodLevel() == 14 && p.getFoodData().getSaturationLevel() == 0
            && human.getFoodData().getFoodLevel() == 18,
            "meat: half the food, no saturation", p.getFoodData().getFoodLevel());
        p.getFoodData().setFoodLevel(10);
        p.eat(c.level, new ItemStack(Items.BREAD), Foods.BREAD);
        check(p.getFoodData().getFoodLevel() == 15, "bread is not meat");
        c.at(25, () -> {
            var w = close.getEffect(MobEffects.WEAKNESS);
            check(w != null && !w.isVisible() && !far.hasEffect(MobEffects.WEAKNESS),
                "the undead in 12 are weak, without particles", w);
        });
        var target = c.mob(EntityType.ZOMBIE, 1, 6, true);
        var cow = c.mob(EntityType.COW, 21, 21, true);
        c.at(30, () -> {
            float hp = target.getHealth();
            SignPowers.activate(p);
            check(target.hasEffect(MobEffects.GLOWING)
                && target.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 1
                && near(target.getHealth(), hp - 4) && !cow.hasEffect(MobEffects.GLOWING),
                    "roll call: monsters glow, slow, undead -4");
            check(Math.abs(c.signCooldown(p) - 1800) <= 2, "cooldown 90 s", c.signCooldown(p));
        });
        c.end(31);
    }

    @GameTest(template = TEMPLATE, batch = "ancestor", timeoutTicks = 30)
    public static void ancestor(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("ancestor", "dwarf", "ancestor", 16, 16);
        check(p.getMaxHealth() == 16, "-2 hearts");
        var z = c.mob(EntityType.ZOMBIE, 20, 16, true);
        var skeleton = c.mob(EntityType.WITHER_SKELETON, 12, 16, true);
        z.setTarget(p);
        skeleton.setTarget(p);
        check(z.getTarget() == null && skeleton.getTarget() == null, "the undead do not attack first");
        var wither = c.mob(EntityType.WITHER, 16, 26, true);
        wither.setTarget(p);
        check(wither.getTarget() == p, "the Wither still does");
        wither.discard();
        z.hurt(c.level.damageSources().playerAttack(p), 1);
        z.setTarget(p);
        check(z.getTarget() == p, "struck undead strike back");
        p.invulnerableTime = 0;
        p.hurt(c.level.damageSources().generic(), 1000);
        var r = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
        check(p.isAlive() && near(p.getHealth(), 1) && r != null && r.getAmplifier() == 1,
            "reprieve: 1 HP and Resistance II", p.getHealth());
        c.at(2, () -> {
            p.invulnerableTime = 0;
            p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            p.hurt(c.level.damageSources().generic(), 1000);
            check(p.isDeadOrDying(), "only once a day");
        });
        c.end(3);
    }

    @GameTest(template = TEMPLATE, batch = "mother")
    public static void mother(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("mother", "dwarf", "mother", 16, 16);
        var ally = c.player("mother_al", "dwarf", "none", 19, 16);
        var other = c.player("mother_ot", "dwarf", "none", 13, 16);
        c.team("gt_mother", p, ally);
        check(near(c.exhaustion(p), .5) && near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), .09),
            "hunger x0.5, speed -10%");
        ally.getFoodData().setFoodLevel(4);
        other.getFoodData().setFoodLevel(4);
        p.getFoodData().setFoodLevel(18);
        SignPowers.activate(p);
        check(ally.getFoodData().getFoodLevel() == 10 && ally.hasEffect(MobEffects.REGENERATION)
            && other.getFoodData().getFoodLevel() == 4,
            "share feeds the teammate only", ally.getFoodData().getFoodLevel());
        check(p.getFoodData().getFoodLevel() == 2, "the mother is left with 2");
        c.end(1);
    }

    @GameTest(template = TEMPLATE, batch = "spinner", timeoutTicks = 40)
    public static void spinner(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("spinner", "dwarf", "spinner", 16, 16);
        var human = c.player("spin_ctl", "dwarf", "none", 10, 16);
        var mod = p.getAttribute(Attributes.ARMOR).getModifier(Civitatum.res("sign_armor"));
        check(mod != null && near(mod.amount(), -2), "-2 armour");
        var z = c.mob(EntityType.ZOMBIE, 18, 16, true);
        var z2 = c.mob(EntityType.ZOMBIE, 9, 18, true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 4));
        human.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 4));
        z.interact(p, InteractionHand.MAIN_HAND);
        z2.interact(human, InteractionHand.MAIN_HAND);
        check(z.isLeashed() && !z2.isLeashed(), "a lead on a monster, for the Spinner only");
        var cow = c.mob(EntityType.COW, 10, 18, true);
        cow.interact(human, InteractionHand.MAIN_HAND);
        p.teleportTo(p.getX() + 14, p.getY(), p.getZ());
        human.teleportTo(human.getX() - 9, human.getY(), human.getZ() + 12);
        c.at(5, () -> {
            check(z.isLeashed(), "the Spinner's lead holds at 14", z.distanceTo(p));
            check(!cow.isLeashed(), "another lead snaps", cow.distanceTo(human));
            var back = c.vec(16.5, 1, 16.5);
            p.teleportTo(back.x, back.y, back.z);
            z.dropLeash(true, false);
        });
        var far = c.mob(EntityType.ZOMBIE, 16, 6, true);
        c.at(7, () -> {
            c.look(p, far.getEyePosition());
            SignPowers.activate(p);
            check(far.distanceTo(p) < 3.5 && far.hasEffect(ModEffects.BOUND), "pulled and bound", far.distanceTo(p));
            far.setTarget(p);
            check(far.getTarget() == null, "a bound mob cannot target");
            human.addEffect(new MobEffectInstance(ModEffects.BOUND, 100));
            check(!CommonHooks.onPlayerAttackTarget(human, z), "a bound player cannot attack");
        });
        c.end(8);
    }

    @GameTest(template = TEMPLATE, batch = "wanderer", timeoutTicks = 500)
    public static void wanderer(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("wanderer", "dwarf", "wanderer", 6, 16);
        var p2 = c.player("wander2", "dwarf", "wanderer", 6, 26);
        check(near(p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), .5)
            && near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), .11),
            "falls x0.5, speed +10%");
        BlockPos bed = c.pos(7, 1, 17);
        p.setRespawnPosition(c.level.dimension(), bed, 0, false, false);
        check(p.getRespawnPosition() == null, "a bed does not set the spawn");
        p.setRespawnPosition(c.level.dimension(), bed, 0, true, false);
        check(bed.equals(p.getRespawnPosition()), "a forced spawn still does");
        Vec3 mark = p.position();
        SignPowers.activate(p);
        check(p.getData(Civitatum.STATE).mark() != null && c.signCooldown(p) <= 0 && p.hasSaid("power.mark_set"),
            "the first press marks, free");
        p.teleportTo(p.getX() + 8, p.getY(), p.getZ());
        float[] hp = new float[1];
        c.at(6, () -> {
            hp[0] = p.getHealth();
            SignPowers.activate(p);
            check(Channels.isRunning(p), "the path focus starts");
        });
        c.at(211, () -> {
            check(p.position().distanceTo(mark) < .1, "back at the mark", p.position());
            check(near(p.getHealth(), hp[0] - 2), "for a heart", p.getHealth());
            check(Math.abs(c.signCooldown(p) - 24000) <= 10, "once a day", c.signCooldown(p));
        });
        SignPowers.activate(p2);
        p2.teleportTo(p2.getX() + 5, p2.getY(), p2.getZ());
        Vec3[] at = new Vec3[1];
        c.at(6, () -> {
            SignPowers.activate(p2);
            at[0] = p2.position().add(1, 0, 0);
        });
        c.at(26, () -> p2.teleportTo(at[0].x, at[0].y, at[0].z));
        c.at(28, () -> check(!Channels.isRunning(p2) && c.signCooldown(p2) <= 0 && p2.hasSaid("power.interrupted"),
            "moving breaks the focus, nothing paid", p2.messages));
        c.at(230, () -> check(p2.position().distanceTo(at[0]) < .1, "and no travel after"));
        c.end(231);
    }

    @GameTest(template = TEMPLATE, batch = "hearth", timeoutTicks = 700)
    public static void hearth(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("hearth", "dwarf", "hearth", 16, 16);
        var ally = c.player("hearth_al", "dwarf", "none", 14, 18);
        c.team("gt_hearth", p, ally);
        c.level.setBlock(c.pos(20, 1, 16), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
        p.setHealth(10);
        var wet = c.player("hearth_w", "dwarf", "hearth", 4, 4);
        c.level.setBlock(wet.blockPosition(), Blocks.WATER.defaultBlockState(), 3);
        c.at(10, () -> {
            var dmg = wet.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(Civitatum.res("sign_water"));
            check(dmg != null && near(dmg.amount(), -4) && near(wet.getAttributeValue(Attributes.MOVEMENT_SPEED), .085),
                "in water: Slowness I and Weakness I");
        });
        c.at(205, () -> {
            check(p.getHealth() >= 11, "heals near a fire", p.getHealth());
            ally.setHealth(10);
            SignPowers.activate(p);
        });
        BlockPos front = c.pos(16, 1, 17);
        c.at(206, () -> check(c.level.getBlockState(front).is(Blocks.SOUL_CAMPFIRE), "a soul campfire in front",
            c.level.getBlockState(front)));
        c.at(295, () -> check(ally.getHealth() > 10, "it heals the teammate", ally.getHealth()));
        // lit at 205 for 20 s
        c.at(610, () -> check(!c.level.getBlockState(front).is(Blocks.SOUL_CAMPFIRE), "gone after 20 s"));
        c.end(611);
    }

    @GameTest(template = TEMPLATE, batch = "tower", timeoutTicks = 40)
    public static void tower(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("tower", "dwarf", "tower", 16, 16);
        check(near(p.getAttributeValue(Attributes.ARMOR), 2)
            && near(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), .25)
            && near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), .09),
                "+2 armour, +0.25 knockback resistance, -10% speed");
        BlockPos door = c.pos(16, 1, 19);
        c.level.setBlock(door, Blocks.IRON_DOOR.defaultBlockState(), 3);
        c.level.setBlock(door.above(), Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF,
            DoubleBlockHalf.UPPER), 3);
        c.look(p, c.level.getBlockState(door).getShape(c.level, door).bounds().getCenter().add(door.getX(),
            door.getY(), door.getZ()));
        c.at(2, () -> {
            SignPowers.activate(p);
            check(c.level.getBlockState(door).getValue(DoorBlock.OPEN), "the key opens an iron door");
            check(Math.abs(c.signCooldown(p) - 12000) <= 3, "cooldown 10 min", c.signCooldown(p));
        });
        BlockPos chest = c.pos(19, 1, 16);
        var other = c.player("tower_ctl", "dwarf", "none", 18, 14);
        c.at(3, () -> {
            c.level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            var be = c.level.getBlockEntity(chest);
            var tag = be.saveWithoutMetadata(c.level.registryAccess());
            tag.putString("Lock", "secret");
            be.loadWithComponents(tag, c.level.registryAccess());
            c.level.getBlockState(chest).useWithoutItem(c.level, other,
                new BlockHitResult(Vec3.atCenterOf(chest), Direction.WEST, chest, false));
            check(!(other.containerMenu instanceof ChestMenu), "a locked chest stays shut for others");
            c.ready(p);
            c.look(p, Vec3.atCenterOf(chest));
        });
        c.at(10, () -> {
            SignPowers.activate(p);
            check(p.containerMenu instanceof ChestMenu, "the key opens a locked chest", p.containerMenu);
            p.closeContainer();
        });
        c.end(11);
    }

    @GameTest(template = TEMPLATE, batch = "merchant", timeoutTicks = 80)
    public static void merchant(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("merchant", "dwarf", "merchant", 16, 16);
        check(near(p.getAttributeValue(Attributes.LUCK), 2), "luck +2");
        check(price(c, p) == 8, "villagers -20%: 10 -> 8", price(c, p));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 3600));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 400, 0, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        Effects.give(p, ModEffects.NABAT, 1200, 0);
        check(p.getEffect(MobEffects.DAMAGE_BOOST).getDuration() == 1800, "a potion lasts half");
        check(p.getEffect(MobEffects.DIG_SPEED).getDuration() == 400, "a beacon's effect is untouched");
        check(p.getEffect(MobEffects.POISON).getDuration() == 400, "harm is untouched");
        check(p.getEffect(ModEffects.NABAT).getDuration() == 1200, "the powers' effects are untouched");
        p.removeEffect(MobEffects.POISON);
        SignPowers.activate(p);
        check(c.signCooldown(p) <= 0 && p.hasSaid("power.no_sugar"), "no sugar, no circle");
        p.getInventory().add(new ItemStack(Items.SUGAR, 1));
        var z = c.mob(EntityType.ZOMBIE, 19, 16, false);
        c.at(10, () -> {
            SignPowers.activate(p);
            check(p.getInventory().countItem(Items.SUGAR) == 0 && c.signCooldown(p) > 3000,
                "the salt circle costs a sugar");
        });
        c.at(50, () -> check(z.distanceTo(p) > 6.5, "the undead are kept beyond 8", z.distanceTo(p)));
        c.end(51);
    }

    @GameTest(template = TEMPLATE, batch = "beast", timeoutTicks = 60)
    public static void beast(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("beast", "dwarf", "beast", 16, 16);
        var human = c.player("beast_ctl", "dwarf", "none", 46, 16);
        check(price(c, p) == 13, "villagers +25%: 10 -> 13", price(c, p));
        var rabbit = c.mob(EntityType.RABBIT, 18, 18, false);
        var goal = new AvoidEntityGoal<>(rabbit, Player.class, 8, 1, 1);
        check(!goal.canUse() && field(goal, AvoidEntityGoal.class, "toAvoid") == p,
            "animals see the Beast and do not flee");
        int tamed = 0, others = 0;
        for (int i = 0; i < 10; i++) {
            var w = c.mob(EntityType.WOLF, 17, 14, true);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE));
            w.mobInteract(p, InteractionHand.MAIN_HAND);
            if (w.isTame()) tamed++;
            var w2 = c.mob(EntityType.WOLF, 45, 14, true);
            human.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE));
            w2.mobInteract(human, InteractionHand.MAIN_HAND);
            if (w2.isTame()) others++;
            w.discard();
            w2.discard();
        }
        check(tamed == 10 && others < 10, "a wolf at the first bone", tamed + " vs " + others);
        var cat = c.mob(EntityType.CAT, 18, 16, true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COD));
        cat.mobInteract(p, InteractionHand.MAIN_HAND);
        var parrot = c.mob(EntityType.PARROT, 14, 16, true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS));
        parrot.mobInteract(p, InteractionHand.MAIN_HAND);
        check(cat.isTame() && parrot.isTame(), "a cat and a parrot at once");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var horse = c.mob(EntityType.HORSE, 16, 20, false);
        double base = horse.getAttributeValue(Attributes.MOVEMENT_SPEED);
        p.startRiding(horse, true);
        c.at(5, () -> {
            check(horse.isTamed(), "a horse is tamed on the first ride");
            check(near(horse.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.15), "and rides 15% faster");
            p.stopRiding();
        });
        c.at(9, () -> check(near(horse.getAttributeValue(Attributes.MOVEMENT_SPEED), base),
            "the bonus leaves with the rider"));
        c.end(10);
    }

    @GameTest(template = TEMPLATE, batch = "beast_pack", timeoutTicks = 1300)
    public static void beastPack(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("pack", "dwarf", "beast", 16, 16);
        var other = c.player("pack_ctl", "dwarf", "none", 10, 10);
        p.getFoodData().setFoodLevel(10);
        SignPowers.activate(p);
        var wolves = c.level.getEntitiesOfClass(Wolf.class, p.getBoundingBox().inflate(3), SignPowers::isSpirit);
        check(wolves.size() == 1 && wolves.getFirst().isOwnedBy(p) && p.getFoodData().getFoodLevel() == 8,
            "a spirit wolf for 2 food");
        var wolf = wolves.getFirst();
        // spirits are checked once a second: gone within a second after the minute
        c.at(1225, () -> check(wolf.isRemoved(), "it leaves after 60 s"));
        // after the debounce of a press (5 ticks)
        c.at(8, () -> {
            c.ready(p);
            SignPowers.activate(p);
            var second = c.level.getEntitiesOfClass(Wolf.class, p.getBoundingBox().inflate(3),
                w -> SignPowers.isSpirit(w) && w != wolf);
            check(second.size() == 1, "a second wolf");
            BlockPos at = second.getFirst().blockPosition();
            second.getFirst().hurt(c.level.damageSources().playerAttack(other), 1000);
            c.at(3, () -> check(c.level.getEntitiesOfClass(ExperienceOrb.class, new AABB(at).inflate(3)).isEmpty(),
                "a dead spirit leaves no XP"));
        });
        c.end(1226);
    }

    @GameTest(template = TEMPLATE, batch = "serpent", timeoutTicks = 20)
    public static void serpent(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("serpent", "dwarf", "serpent", 16, 16);
        p.setHealth(10);
        p.heal(4);
        check(near(p.getHealth(), 12), "healing x0.5", p.getHealth());
        p.setHealth(p.getMaxHealth());
        var cow = c.mob(EntityType.COW, 16, 18, true);
        c.at(2, () -> {
            c.look(p, cow.getEyePosition());
            float start = cow.getHealth();
            SignPowers.activate(p);
            check(near(cow.getHealth(), start - 8) && cow.hasEffect(MobEffects.POISON)
                && cow.hasEffect(MobEffects.WEAKNESS),
                "bite: 8, Poison, Weakness", cow.getHealth());
            check(near(p.getHealth(), 16), "costs 2 hearts", p.getHealth());
            check(Math.abs(c.signCooldown(p) - 900) <= 3, "cooldown 45 s");
        });
        c.end(3);
    }
}
