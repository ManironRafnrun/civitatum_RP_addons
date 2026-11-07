package team.civitatum.starclock.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import team.civitatum.starclock.network.ShowCardPayload;
import team.civitatum.starclock.prediction.Orientation;
import team.civitatum.starclock.registry.ModSounds;

public final class StarclockAudio {
    public static void play(SoundEvent sound, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, volume));
    }

    public static void revealStars() {
        play(ModSounds.STARS_REVEAL.get(), 0.5F);
    }

    public static void revealCard(ShowCardPayload card) {
        play(ModSounds.CARD_FLIP.get(), 0.5F);
        // У Змея звук не раскрывает выбранное событие или его знак.
        if (card.orientation() == Orientation.UPRIGHT) play(ModSounds.OMEN_GOOD.get(), 0.4F);
        else if (card.orientation() == Orientation.REVERSED) play(ModSounds.OMEN_BAD.get(), 0.4F);
    }

    private StarclockAudio() {}
}
