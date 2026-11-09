package team.civitatum.charactercreation.power;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * What follows a power when its effect ends ("После — эффект …"): the rage leaves a comedown, the roots leave
 * Slowness. It also follows when milk or a command takes the effect away, otherwise milk would skip the price.
 * <p>
 * An effect ends inside the entity's loop over its effects, and adding one there would break that loop, so the next
 * effect is given at the end of the server tick.
 */
public final class Aftermaths {
    /** Slowness II, the second half of the roots and the knot. */
    private static final int SLOWNESS_II = 1;
    private static final Map<Holder<MobEffect>, Consumer<LivingEntity>> NEXT = Map.of(
        ModEffects.RAGE, e -> Effects.give(e, ModEffects.COMEDOWN, 30 * 20, 0),
        ModEffects.ROOTS, e -> Effects.give(e, MobEffects.MOVEMENT_SLOWDOWN, 5 * 20, SLOWNESS_II),
        ModEffects.KNOT, e -> Effects.give(e, MobEffects.MOVEMENT_SLOWDOWN, 4 * 20, SLOWNESS_II),
        ModEffects.REPLACEMENT, e -> Effects.give(e, MobEffects.WEAKNESS, 60 * 20, 0),
        ModEffects.MASK, e -> {
            Effects.give(e, ModEffects.AFTERMATH, 10 * 20, 0);
            // Nausea is drawn by the client for its own effect only; without an icon «Последствие» stays one icon.
            Effects.give(e, new MobEffectInstance(MobEffects.CONFUSION, 10 * 20, 0, false, false, false));
        });
    private static final List<Runnable> PENDING = new ArrayList<>();

    /** The effect of {@code entity} ended or was taken away. */
    public static void ended(LivingEntity entity, MobEffectInstance instance) {
        if (instance == null || entity.level().isClientSide) return;
        // Looked up by registry key: the instance holds the registry's own holder, not the DeferredHolder above.
        Consumer<LivingEntity> next = null;
        for (var entry : NEXT.entrySet()) if (instance.is(entry.getKey())) next = entry.getValue();
        if (next == null) return;
        var follow = next;
        PENDING.add(() -> {
            if (entity.isAlive() && !entity.isRemoved()) follow.accept(entity);
        });
    }

    public static void tick() {
        if (PENDING.isEmpty()) return;
        var now = List.copyOf(PENDING);
        PENDING.clear();
        now.forEach(Runnable::run);
    }

    private Aftermaths() {}
}
