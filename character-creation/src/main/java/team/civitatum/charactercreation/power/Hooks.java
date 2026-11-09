package team.civitatum.charactercreation.power;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/** What the mixins ask: each hook only calls one of these. */
public final class Hooks {
    /** The dwarf's own tools and armour: "прочнее на 10%". */
    private static final double SMITHED_DURABILITY = 1.1;
    private static final String SMITHED_TAG = "civitatum_smithed";
    /** Vanilla air is 300 ticks; the storm dragonborn breathes 30 s longer ("воздух +30 с"). */
    private static final int STORM_AIR = 300 + 30 * 20;
    /** The forged heals 25% less from potions of healing. */
    private static final float FORGED_POTION_HEAL = .75f;

    /** Hunger: "Голод убывает на 15% медленнее" and the like; race and sign multiply. */
    public static float exhaustion(Player p, float value) {
        float factor = switch (Traits.race(p)) {
            case "human" -> .85f;
            case "hobbit" -> .8f;
            case "fire_dragonborn" -> 1.25f;
            default -> 1;
        };
        if (Traits.isSign(p, "mother")) factor *= .5f;
        return value * factor;
    }

    /** Blocks that do not hold the player: leaves for the firbolg, webs for the dark elf, bushes for the borianin. */
    public static boolean isFree(Player p, BlockState state) {
        String race = Traits.race(p);
        if (race.equals("dark_elf")) return state.is(Blocks.COBWEB);
        if (race.equals("firbolg")) return state.is(BlockTags.LEAVES) || state.is(Blocks.VINE)
            || state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT);
        return Traits.isAlgnor(p) && (state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.SHORT_GRASS)
            || state.is(Blocks.TALL_GRASS));
    }

    /**
     * What a meal gives: the hobbit gets one more saturation ("еда даёт +1 насыщения"); the Shepherd gets half the
     * food of meat and no saturation from it ("мясо даёт вдвое меньше голода и не даёт насыщения").
     */
    public static FoodProperties food(Player p, ItemStack stack, FoodProperties food) {
        int nutrition = food.nutrition();
        float saturation = food.saturation();
        if (Traits.isSign(p, "shepherd") && stack.is(ItemTags.MEAT)) {
            nutrition /= 2;
            saturation = 0;
        }
        if (Traits.isRace(p, "hobbit")) saturation += 1;
        if (nutrition == food.nutrition() && saturation == food.saturation()) return food;
        return new FoodProperties(nutrition, saturation, food.canAlwaysEat(), food.eatSeconds(),
            food.usingConvertsTo(), food.effects());
    }

    /** The anvil's price: the dwarf and goblin pay 25% less, the Smith half; together they multiply. */
    public static long anvilCost(Player p, long cost) {
        double factor = 1;
        if (Traits.isRace(p, "dwarf") || Traits.isRace(p, "goblin")) factor *= .75;
        if (Traits.isSign(p, "smith")) factor *= .5;
        if (factor == 1 || cost <= 0) return cost;
        return Math.max(1, Math.round(cost * factor));
    }

    /** A dwarf made it: 10% more durability, once per item. */
    public static void crafted(ItemStack stack, Player p) {
        if (p == null || p.level().isClientSide || !Traits.isRace(p, "dwarf") || !stack.isDamageableItem()) return;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().getBoolean(SMITHED_TAG)) return;
        stack.set(DataComponents.MAX_DAMAGE, (int) Math.round(stack.getMaxDamage() * SMITHED_DURABILITY));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(SMITHED_TAG, true));
    }

    /** The Smith: "шанс 50% отменить потерю прочности", each point on its own, as Unbreaking counts them. */
    public static int durability(LivingEntity user, int amount) {
        if (amount <= 0 || !Traits.isSign(user, "smith")) return amount;
        int kept = amount;
        for (int i = 0; i < amount; i++) if (user.getRandom().nextBoolean()) kept--;
        return kept;
    }

    /** The orc while no weapon is drawn: piglins wait as for a player in gold. */
    public static boolean isPiglinNeutral(LivingEntity e) {
        if (!Traits.isRace(e, "orc")) return false;
        var hand = e.getMainHandItem();
        return !(hand.is(ItemTags.SWORDS) || hand.is(ItemTags.AXES) || hand.is(Tags.Items.MELEE_WEAPON_TOOLS)
            || hand.is(Tags.Items.RANGED_WEAPON_TOOLS));
    }

    /**
     * The price a villager asks, as a share of the base price: the human −10%, the Merchant −20%, the goblin +15%,
     * the Beast +25%; summed.
     */
    public static void prices(Villager villager, Player p) {
        int percent = switch (Traits.race(p)) {
            case "human" -> -10;
            case "goblin" -> 15;
            default -> 0;
        } + switch (Traits.sign(p)) {
            case "merchant" -> -20;
            case "beast" -> 25;
            default -> 0;
        };
        if (percent == 0) return;
        for (MerchantOffer offer : villager.getOffers()) {
            int count = offer.getBaseCostA().getCount();
            int diff = (int) Math.round(count * percent / 100.0);
            // At least one emerald either way, as vanilla gives the Hero of the Village at least one.
            if (diff == 0) diff = Integer.signum(percent);
            offer.addToSpecialPriceDiff(diff);
        }
    }

    public static float potionHeal(LivingEntity e, float amount) {
        return Traits.isRace(e, "forged") ? amount * FORGED_POTION_HEAL : amount;
    }

    /** The frost dragonborn walks on powder snow as in leather boots. */
    public static boolean isSnowWalker(Entity e) { return Traits.isRace(e, "frost_dragonborn"); }

    /** The air of the storm dragonborn, or -1 for the vanilla one. */
    public static int maxAir(Entity e) { return Traits.isRace(e, "storm_dragonborn") ? STORM_AIR : -1; }

    /**
     * The Spinner leashes anything that takes a leash at all, monsters too ("включая враждебных мобов"), but not a
     * boss: a leashed Wither would be a pet.
     */
    public static boolean mayLeash(Player p, Entity target) {
        return Traits.isSign(p, "spinner") && !target.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** A leash held by the Spinner does not break with distance. */
    public static boolean isUnbreakableLeash(Mob mob) { return Traits.isSign(mob.getLeashHolder(), "spinner"); }

    /** Animals do not flee from the Beast. */
    public static boolean isFearless(Entity avoided) { return Traits.isSign(avoided, "beast"); }

    /** The Beast tames at the first try. */
    public static boolean isTamer(Player p) { return Traits.isSign(p, "beast"); }

    private Hooks() {}
}
