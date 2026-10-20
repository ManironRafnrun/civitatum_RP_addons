package team.civitatum.starclock.client;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import team.civitatum.starclock.item.StarToolItem;
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

    public static UUID session() {
        return state == null ? null : state.session();
    }

    public static boolean isActive() {
        return state != null;
    }

    public static boolean matches(ItemStack stack) {
        return state != null && stack.getItem() instanceof StarToolItem item
                && item.kind() == state.kind() && item.tier() == state.tier();
    }

    public static float phaseTicks(float partialTick) {
        var level = Minecraft.getInstance().level;
        if (state == null || level == null) return 0;
        int duration = state.drawing() ? RitualRules.DRAWING_TICKS : RitualRules.PREPARATION_TICKS;
        return Math.clamp((float) (level.getGameTime() - state.startedAt()) + partialTick, 0.0F, duration);
    }

    public static float animationTicks(float partialTick) {
        return state != null && state.drawing() ? 39.999F : Math.min(39.999F, phaseTicks(partialTick));
    }

    public static float progress() {
        int duration = state != null && state.drawing()
                ? RitualRules.DRAWING_TICKS : RitualRules.PREPARATION_TICKS;
        return phaseTicks(0) / duration;
    }

    public static float cameraEnvelope(float partialTick) {
        if (state == null || state.drawing()) return 0;
        float tick = phaseTicks(partialTick);
        float fade = Math.clamp(Math.min(tick / 8.0F, (40.0F - tick) / 6.0F), 0.0F, 1.0F);
        return fade * fade * (3 - 2 * fade);
    }

    public static void clear() {
        state = null;
    }

    private RitualClientState() {}
}
