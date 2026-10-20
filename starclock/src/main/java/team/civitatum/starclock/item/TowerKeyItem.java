package team.civitatum.starclock.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class TowerKeyItem extends Item {
    public TowerKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        boolean door = state.is(Blocks.IRON_DOOR);
        boolean trapdoor = state.is(Blocks.IRON_TRAPDOOR);
        if ((!door && !trapdoor) || player == null) return InteractionResult.PASS;
        if (!player.mayBuild() || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) {
            return InteractionResult.FAIL;
        }
        if ((door && state.getValue(DoorBlock.OPEN)) || (trapdoor && state.getValue(TrapDoorBlock.OPEN))) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (door) {
                ((DoorBlock) state.getBlock()).setOpen(player, level, state, pos, true);
            } else {
                level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), 3);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            EquipmentSlot slot = context.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            context.getItemInHand().hurtAndBreak(1, serverPlayer, slot);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean isEnchantable(net.minecraft.world.item.ItemStack stack) {
        return false;
    }
}
