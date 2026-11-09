package team.civitatum.charactercreation.power;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;
import team.civitatum.charactercreation.RaceService;

/**
 * The passives that are attribute modifiers: hearts, speed, armour, falls. They are recomputed often (on every
 * change of the character and every few ticks) because some depend on the moment: a storm, a forest, water, an empty
 * hand. A modifier that keeps its value is not touched ({@link RaceService#modifier}).
 */
public final class PassiveAttributes {
    /** "+2 сердца" and "−2 сердца". */
    private static final double TWO_HEARTS = 4;
    /** "−10% скорости", "+10%", "+15%": a share of the base speed, as vanilla Speed counts it. */
    private static final double TENTH = .1;
    /** Slowness I in water of the end dragonborn and the Hearth, without its particles. */
    private static final double SLOWNESS_I = -.15;
    /** Weakness I in water of the Hearth. */
    private static final double WEAKNESS_I = -4;
    /** "Урон от падения −50%". */
    private static final double HALF = -.5;
    /** The borianin's horns: "без оружия +2 урона и отбрасывание". */
    private static final double HORNS_DAMAGE = 2;
    private static final double HORNS_KNOCKBACK = 1;

    public static void apply(ServerPlayer p) {
        String race = Traits.race(p), sign = Traits.sign(p);
        boolean algnor = Traits.isAlgnor(p);
        boolean water = p.isInWaterOrBubble();

        double health = switch (race) {
            case "orc", "firbolg", "orthosian" -> TWO_HEARTS;
            case "hobbit" -> -TWO_HEARTS;
            default -> 0;
        };
        double speed = switch (race) {
            case "firbolg", "orthosian" -> -TENTH;
            case "storm_dragonborn" -> p.level().isThundering() ? TENTH : 0;
            case "end_dragonborn" -> water ? SLOWNESS_I : 0;
            default -> algnor && isForest(p) ? TENTH : 0;
        };
        set(p, Attributes.MAX_HEALTH, "health", health, Operation.ADD_VALUE);
        set(p, Attributes.MOVEMENT_SPEED, "speed", speed, Operation.ADD_MULTIPLIED_BASE);
        set(p, Attributes.FALL_DAMAGE_MULTIPLIER, "fall",
            race.equals("goblin") || race.equals("jahar") ? HALF : 0, Operation.ADD_MULTIPLIED_TOTAL);
        boolean turs = race.equals("orthosian");
        set(p, Attributes.KNOCKBACK_RESISTANCE, "knockback", turs ? 1 : 0, Operation.ADD_VALUE);
        set(p, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, "explosion_knockback", turs ? 1 : 0, Operation.ADD_VALUE);
        set(p, NeoForgeMod.SWIM_SPEED, "swim", race.equals("storm_dragonborn") ? .25 : 0,
            Operation.ADD_MULTIPLIED_BASE);
        boolean unarmed = algnor && p.getMainHandItem().isEmpty();
        set(p, Attributes.ATTACK_DAMAGE, "horns", unarmed ? HORNS_DAMAGE : 0, Operation.ADD_VALUE);
        set(p, Attributes.ATTACK_KNOCKBACK, "horns_knockback", unarmed ? HORNS_KNOCKBACK : 0, Operation.ADD_VALUE);
        set(p, Attributes.ARMOR, "helmet", algnor ? -helmetArmor(p) / 2 : 0, Operation.ADD_VALUE);

        set(p, Attributes.ATTACK_DAMAGE, "sign_damage", sign.equals("spear") ? 3 : 0, Operation.ADD_VALUE);
        set(p, Attributes.MAX_HEALTH, "sign_health", sign.equals("ancestor") ? -TWO_HEARTS : 0, Operation.ADD_VALUE);
        set(p, Attributes.MOVEMENT_SPEED, "sign_speed", switch (sign) {
            case "messenger" -> 1.5 * TENTH;
            case "wanderer" -> TENTH;
            case "mother", "tower" -> -TENTH;
            case "hearth" -> water ? SLOWNESS_I : 0;
            default -> 0;
        }, Operation.ADD_MULTIPLIED_BASE);
        set(p, Attributes.STEP_HEIGHT, "sign_step", sign.equals("messenger") ? 1 : 0, Operation.ADD_VALUE);
        set(p, Attributes.ARMOR, "sign_armor", switch (sign) {
            case "tower" -> 2;
            case "spinner" -> -2;
            default -> 0;
        }, Operation.ADD_VALUE);
        set(p, Attributes.KNOCKBACK_RESISTANCE, "sign_knockback", sign.equals("tower") ? .25 : 0, Operation.ADD_VALUE);
        set(p, Attributes.FALL_DAMAGE_MULTIPLIER, "sign_fall", sign.equals("wanderer") ? HALF : 0,
            Operation.ADD_MULTIPLIED_TOTAL);
        set(p, Attributes.LUCK, "sign_luck", sign.equals("merchant") ? 2 : 0, Operation.ADD_VALUE);
        set(p, Attributes.ATTACK_DAMAGE, "sign_water", sign.equals("hearth") && water ? WEAKNESS_I : 0,
            Operation.ADD_VALUE);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    /** The forests of the borianin: "биомы по тегу #minecraft:is_forest и #minecraft:is_taiga". */
    private static boolean isForest(ServerPlayer p) {
        var biome = p.level().getBiome(p.blockPosition());
        return biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA);
    }

    /** The armour the worn helmet adds, with its enchantments and other mods' modifiers on it. */
    private static double helmetArmor(ServerPlayer p) {
        var helmet = p.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) return 0;
        double[] armor = {0};
        helmet.forEachModifier(EquipmentSlot.HEAD, (attribute, modifier) -> {
            if (attribute.is(Attributes.ARMOR) && modifier.operation() == Operation.ADD_VALUE)
                armor[0] += modifier.amount();
        });
        return armor[0];
    }

    private static void set(ServerPlayer p, Holder<Attribute> attribute, String id, double amount, Operation op) {
        RaceService.modifier(p, attribute, id, amount, op);
    }

    private PassiveAttributes() {}
}
