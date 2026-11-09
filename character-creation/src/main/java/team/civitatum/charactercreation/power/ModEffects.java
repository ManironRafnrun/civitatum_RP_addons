package team.civitatum.charactercreation.power;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import team.civitatum.charactercreation.Civitatum;

/**
 * The effects of the powers ("10b": composite effects, one icon each). The numbers are the vanilla ones of the
 * effects they stand for, so "Сила II" here is what Strength II gives. Parts that are not attributes (resistance,
 * immunity to fire, losing a target) are read by {@link PowerEvents} from the effect's presence.
 */
public final class ModEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT,
        Civitatum.ID);

    /** Speed adds 20% per level (MobEffects.MOVEMENT_SPEED). */
    private static final double SPEED_LEVEL = .2;
    /** Slowness takes 15% per level (MobEffects.MOVEMENT_SLOWDOWN). */
    private static final double SLOWNESS_LEVEL = -.15;
    /** Strength adds 3 attack damage per level (MobEffects.DAMAGE_BOOST). */
    private static final double STRENGTH_LEVEL = 3;
    /** Weakness takes 4 attack damage per level (MobEffects.WEAKNESS). */
    private static final double WEAKNESS_LEVEL = -4;
    /** Haste adds 10% attack speed per level (MobEffects.DIG_SPEED) ... */
    private static final double HASTE_ATTACK_LEVEL = .1;
    /** ... and mines 20% faster per level (Player.getDigSpeed): the same through the block break speed attribute. */
    private static final double HASTE_MINING_LEVEL = .2;
    /** All of the speed, as a multiplier of the total: nothing is left, the entity stands. */
    private static final double ROOTED = -1;

    /** «Набат» of the human: Speed I. */
    public static final DeferredHolder<MobEffect, PowerEffect> NABAT = register("nabat", () -> beneficial(0xC9A44A)
        .modifier(Attributes.MOVEMENT_SPEED, id("nabat"), SPEED_LEVEL, Operation.ADD_MULTIPLIED_TOTAL));
    /** «Ярость» of the orc: +50% melee damage, Haste II, no knockback. */
    public static final DeferredHolder<MobEffect, PowerEffect> RAGE = register("rage", () -> haste(beneficial(0xB0302A),
        "rage", 2)
        .modifier(Attributes.ATTACK_DAMAGE, id("rage_damage"), .5, Operation.ADD_MULTIPLIED_TOTAL)
        .modifier(Attributes.KNOCKBACK_RESISTANCE, id("rage_knockback"), 1, Operation.ADD_VALUE));
    /** «Отходняк»: Hunger III after the rage. */
    public static final DeferredHolder<MobEffect, PowerEffect> COMEDOWN = register("comedown",
        () -> harmful(0x587A33).hunger(3));
    /** «Корни» of the firbolg: rooted; Slowness II follows (see {@link Aftermaths}). */
    public static final DeferredHolder<MobEffect, PowerEffect> ROOTS = register("roots",
        () -> rooted(harmful(0x5C4632), "roots"));
    /** «Звон» of the goblin's bell: Weakness I and Slowness I. */
    public static final DeferredHolder<MobEffect, PowerEffect> RING = register("ring", () -> harmful(0x9C8F6A)
        .modifier(Attributes.ATTACK_DAMAGE, id("ring_damage"), WEAKNESS_LEVEL, Operation.ADD_VALUE)
        .modifier(Attributes.MOVEMENT_SPEED, id("ring_speed"), SLOWNESS_LEVEL,
            Operation.ADD_MULTIPLIED_TOTAL));
    /** «Замена» of the mechanist: Resistance II (in {@link PowerEvents}) and Haste II. */
    public static final DeferredHolder<MobEffect, PowerEffect> REPLACEMENT = register("replacement",
        () -> haste(beneficial(0xB08D57), "replacement", 2));
    /** «Завтрак» of the hobbit: Regeneration I for 15 of its 60 seconds and absorption of 2 hearts. */
    public static final DeferredHolder<MobEffect, PowerEffect> BREAKFAST = register("breakfast",
        () -> beneficial(0xE3B35B).regeneration(1, 15, 60).absorption(2)
            .modifier(Attributes.MAX_ABSORPTION, id("breakfast"), 4, Operation.ADD_VALUE));
    /** «Подземный» of the dark elf below y=32: +2 hearts. A passive: given without particles. */
    public static final DeferredHolder<MobEffect, PowerEffect> UNDERGROUND = register("underground",
        () -> beneficial(0x4A4458)
            .modifier(Attributes.MAX_HEALTH, id("underground"), 4, Operation.ADD_VALUE));
    /** «Тень» of the night elf: mobs lose the player, the player cannot attack (in {@link PowerEvents}). */
    public static final DeferredHolder<MobEffect, PowerEffect> SHADOW = register("shadow",
        () -> beneficial(0x2D2840));
    /** «Узел» of the dark elf: rooted; Slowness II follows. */
    public static final DeferredHolder<MobEffect, PowerEffect> KNOT = register("knot",
        () -> rooted(harmful(0xD8D8D8), "knot"));
    /** «Транс» of the wood elf: Regeneration II for 10 of its 60 seconds; the undead ignore the player. */
    public static final DeferredHolder<MobEffect, PowerEffect> TRANCE = register("trance",
        () -> beneficial(0x6F9A57).regeneration(2, 10, 60));
    /** «Хрип» after the fire dragonborn's breath: Weakness I. */
    public static final DeferredHolder<MobEffect, PowerEffect> HOARSE = register("hoarse", () -> harmful(0x6B4A3A)
        .modifier(Attributes.ATTACK_DAMAGE, id("hoarse"), WEAKNESS_LEVEL, Operation.ADD_VALUE));
    /** «Иней» of the frost dragonborn: rooted. */
    public static final DeferredHolder<MobEffect, PowerEffect> RIME = register("rime",
        () -> rooted(harmful(0xA8D8F0), "rime"));
    /** «Шаг» of the end dragonborn: no fall damage (in {@link PowerEvents}). */
    public static final DeferredHolder<MobEffect, PowerEffect> STEP = register("step", () -> beneficial(0x7A4FA0));
    /** «Имя предка» of the turs: Strength II and Resistance I (in {@link PowerEvents}). */
    public static final DeferredHolder<MobEffect, PowerEffect> ANCESTOR_NAME = register("ancestor_name",
        () -> beneficial(0x7C9CC0)
            .modifier(Attributes.ATTACK_DAMAGE, id("ancestor_name"), 2 * STRENGTH_LEVEL,
                Operation.ADD_VALUE));
    /** «Сила Маски» of the kairi: Strength II, Speed II; immune to fire, +2 damage taken (in {@link PowerEvents}). */
    public static final DeferredHolder<MobEffect, PowerEffect> MASK = register("mask", () -> beneficial(0xE8E2D4)
        .modifier(Attributes.ATTACK_DAMAGE, id("mask_damage"), 2 * STRENGTH_LEVEL, Operation.ADD_VALUE)
        .modifier(Attributes.MOVEMENT_SPEED, id("mask_speed"), 2 * SPEED_LEVEL,
            Operation.ADD_MULTIPLIED_TOTAL));
    /** «Последствие» after the Mask: Slowness II and Hunger IV; Nausea, a client effect, is given beside it. */
    public static final DeferredHolder<MobEffect, PowerEffect> AFTERMATH = register("aftermath",
        () -> harmful(0x3A3A3A).hunger(4)
            .modifier(Attributes.MOVEMENT_SPEED, id("aftermath"), 2 * SLOWNESS_LEVEL,
                Operation.ADD_MULTIPLIED_TOTAL));
    /**
     * «Таран» of the borianin: a stun. Vanilla has none, so the target can neither walk, jump nor hurt: Slowness and
     * Weakness of a level that leaves nothing ("10b").
     */
    public static final DeferredHolder<MobEffect, PowerEffect> RAM = register("ram",
        () -> stunned(harmful(0x8B6B3E), "ram"));
    /** «Стянуть» of the Spinner: a stun; the bound also cannot use items (in {@link PowerEvents}). */
    public static final DeferredHolder<MobEffect, PowerEffect> BOUND = register("bound",
        () -> stunned(harmful(0xC4B49A), "bound"));

    private static DeferredHolder<MobEffect, PowerEffect> register(String name, Supplier<PowerEffect> effect) {
        return EFFECTS.register(name, effect);
    }

    public static void register(IEventBus bus) { EFFECTS.register(bus); }

    private static net.minecraft.resources.ResourceLocation id(String name) { return Civitatum.res("effect." + name); }

    private static PowerEffect beneficial(int color) { return new PowerEffect(MobEffectCategory.BENEFICIAL, color); }

    private static PowerEffect harmful(int color) { return new PowerEffect(MobEffectCategory.HARMFUL, color); }

    private static PowerEffect haste(PowerEffect effect, String name, int level) {
        return effect
            .modifier(Attributes.ATTACK_SPEED, id(name + "_attack_speed"), level * HASTE_ATTACK_LEVEL,
                Operation.ADD_MULTIPLIED_TOTAL)
            .modifier(Attributes.BLOCK_BREAK_SPEED, id(name + "_mining"), level * HASTE_MINING_LEVEL,
                Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static PowerEffect rooted(PowerEffect effect, String name) {
        return effect
            .modifier(Attributes.MOVEMENT_SPEED, id(name + "_speed"), ROOTED, Operation.ADD_MULTIPLIED_TOTAL)
            .modifier(Attributes.JUMP_STRENGTH, id(name + "_jump"), ROOTED,
                Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** Rooted, and every blow does nothing: attack damage cannot go below zero. */
    private static PowerEffect stunned(PowerEffect effect, String name) {
        return rooted(effect, name)
            .modifier(Attributes.ATTACK_DAMAGE, id(name + "_damage"), ROOTED,
                Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** Whether the holder is one of these effects: the merchant's halving leaves them alone. */
    public static boolean isOwn(Holder<MobEffect> effect) {
        return effect.unwrapKey().map(k -> k.location().getNamespace().equals(Civitatum.ID)).orElse(false);
    }

    private ModEffects() {}
}
