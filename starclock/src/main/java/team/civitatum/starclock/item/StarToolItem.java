package team.civitatum.starclock.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import team.civitatum.starclock.ritual.RitualManager;
import team.civitatum.starclock.ritual.RitualRules;

public final class StarToolItem extends Item {
    private final ToolKind kind;
    private final ToolTier tier;

    public StarToolItem(ToolKind kind, ToolTier tier, Properties properties) {
        super(properties);
        this.kind = kind;
        this.tier = tier;
    }

    public ToolKind kind() {
        return kind;
    }

    public ToolTier tier() {
        return tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer
                && !RitualManager.start(serverPlayer, hand, stack, this)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return RitualRules.PREPARATION_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) RitualManager.beginDrawing(player);
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (entity instanceof ServerPlayer player) RitualManager.release(player);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.civitatum_starclock.tier", tier.name()));
        tooltip.add(Component.translatable("tooltip.civitatum_starclock.use"));
    }
}
