package team.civitatum.starclock.client;

import net.minecraft.client.Minecraft;
import team.civitatum.starclock.network.RitualStatePayload;
import team.civitatum.starclock.ritual.RitualRules;

public final class RitualClientState {
    private static RitualStatePayload state;

    public static void accept(RitualStatePayload packet) {
        state = packet;
    }

    public static RitualStatePayload current() {
        return state;
    }

    public static boolean isActive() {
        return state != null;
    }

    public static float progress() {
        var level = Minecraft.getInstance().level;
        if (state == null || level == null) return 0;
        int duration = state.drawing() ? RitualRules.DRAWING_TICKS : RitualRules.PREPARATION_TICKS;
        return Math.clamp((float) (level.getGameTime() - state.startedAt()) / duration, 0.0F, 1.0F);
    }

    public static void clear() {
        state = null;
    }

    private RitualClientState() {}
}
