package team.civitatum.starclock.registry;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import team.civitatum.starclock.CivitatumStarclock;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, CivitatumStarclock.ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> SHUFFLE = register("shuffle");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEXTANT_CLICK = register("sextant_click");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATER_POUR = register("water_pour");
    public static final DeferredHolder<SoundEvent, SoundEvent> RING_HUM = register("ring_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> STARS_REVEAL = register("stars_reveal");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_FLIP = register("card_flip");
    public static final DeferredHolder<SoundEvent, SoundEvent> OMEN_GOOD = register("omen_good");
    public static final DeferredHolder<SoundEvent, SoundEvent> OMEN_BAD = register("omen_bad");
    public static final List<DeferredHolder<SoundEvent, SoundEvent>> ALL = List.of(
            SHUFFLE, SEXTANT_CLICK, WATER_POUR, RING_HUM, STARS_REVEAL, CARD_FLIP, OMEN_GOOD, OMEN_BAD);

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CivitatumStarclock.resource(name)));
    }

    private ModSounds() {}
}
