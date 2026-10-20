package team.civitatum.starclock.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.util.TriState;
import team.civitatum.starclock.data.StarClockData;
import team.civitatum.starclock.network.TowerKeyStatePayload;
import team.civitatum.starclock.ritual.RitualManager;

public final class TowerKeyService {
    public static void grant(ServerPlayer player) {
        StarClockData.get(player.server).grantTowerKey(player.getUUID());
        sync(player);
        player.displayClientMessage(Component.translatable("ability.civitatum_starclock.tower_key.granted"), false);
    }

    public static void restoreLegacy(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        int remaining = Math.clamp(stack.getMaxDamage() - stack.getDamageValue(), 0, 5);
        var data = StarClockData.get(player.server);
        data.restoreLegacyTowerKey(player.getUUID(), remaining);
        sync(player);
        player.displayClientMessage(Component.translatable("ability.civitatum_starclock.tower_key.remaining",
                data.towerKeyCharges(player.getUUID())), false);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new TowerKeyStatePayload(
                StarClockData.get(player.server).towerKeyCharges(player.getUUID())));
    }

    public static void cast(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping()
                || RitualManager.isActive(player.getUUID())) return;
        var data = StarClockData.get(player.server);
        if (data.towerKeyCharges(player.getUUID()) <= 0) return;
        long now = player.server.overworld().getGameTime();
        long previous = player.getPersistentData().getLong("StarclockLastKeyCast");
        if (previous > 0 && now - previous < 5) return;
        player.getPersistentData().putLong("StarclockLastKeyCast", now);
        var level = player.serverLevel();
        var origin = player.getEyePosition();
        var hit = level.clip(new ClipContext(origin, origin.add(player.getLookAngle().scale(5)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            failed(player);
            return;
        }
        var pos = hit.getBlockPos();
        var state = level.getBlockState(pos);
        boolean door = state.is(Blocks.IRON_DOOR);
        boolean trapdoor = state.is(Blocks.IRON_TRAPDOOR);
        if ((!door && !trapdoor) || (door && state.getValue(DoorBlock.OPEN))
                || (trapdoor && state.getValue(TrapDoorBlock.OPEN))) {
            failed(player);
            return;
        }
        if (!player.mayBuild() || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, hit.getDirection(), player.getMainHandItem())) {
            failed(player);
            return;
        }
        var interaction = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(
                player, InteractionHand.MAIN_HAND, pos, hit));
        if (interaction.isCanceled() || interaction.getUseBlock() == TriState.FALSE) {
            failed(player);
            return;
        }
        state = level.getBlockState(pos);
        if ((door && (!state.is(Blocks.IRON_DOOR) || state.getValue(DoorBlock.OPEN)))
                || (trapdoor && (!state.is(Blocks.IRON_TRAPDOOR) || state.getValue(TrapDoorBlock.OPEN)))) {
            failed(player);
            return;
        }
        if (door) {
            ((DoorBlock) state.getBlock()).setOpen(player, level, state, pos, true);
        } else {
            level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1, 1);
        }
        var opened = level.getBlockState(pos);
        if ((door && (!opened.is(Blocks.IRON_DOOR) || !opened.getValue(DoorBlock.OPEN)))
                || (trapdoor && (!opened.is(Blocks.IRON_TRAPDOOR) || !opened.getValue(TrapDoorBlock.OPEN)))) {
            failed(player);
            return;
        }
        if (data.consumeTowerKey(player.getUUID())) {
            sync(player);
            player.displayClientMessage(Component.translatable("ability.civitatum_starclock.tower_key.remaining",
                    data.towerKeyCharges(player.getUUID())), true);
        }
    }

    private static void failed(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("ability.civitatum_starclock.tower_key.no_target"), true);
    }

    private TowerKeyService() {}
}
