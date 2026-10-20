package team.civitatum.starclock.ritual;

import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import team.civitatum.starclock.item.ToolKind;
import team.civitatum.starclock.item.ToolTier;

final class RitualState {
    final UUID session = UUID.randomUUID();
    final UUID player;
    final InteractionHand hand;
    final ItemStack stack;
    final ToolKind kind;
    final ToolTier tier;
    final long preparationStartedAt;
    boolean preparationFinished;
    long drawingStartedAt = -1L;

    RitualState(UUID player, InteractionHand hand, ItemStack stack, ToolKind kind, ToolTier tier, long now) {
        this.player = player;
        this.hand = hand;
        this.stack = stack;
        this.kind = kind;
        this.tier = tier;
        preparationStartedAt = now;
    }

    boolean isDrawing() {
        return drawingStartedAt >= 0;
    }
}
