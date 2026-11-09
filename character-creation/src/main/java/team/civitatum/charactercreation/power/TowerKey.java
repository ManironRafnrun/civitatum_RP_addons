package team.civitatum.charactercreation.power;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * «Ключ Башни» of the Tower sign: any door, trapdoor or container within 5 blocks, iron ones and locked chests too.
 * Starclock's own Tower Key is a separate omen with charges; this one is the sign's and has only a cooldown, so the two
 * never share a counter. Supplementaries' locks are not opened: they are not checked here.
 */
public final class TowerKey {
    public static final int REACH = 5;
    /** Set while the key opens a container: its lock (the vanilla {@code minecraft:lock}) lets the player in. */
    private static final ThreadLocal<ServerPlayer> OPENING = new ThreadLocal<>();

    public static boolean isOpening(net.minecraft.world.entity.player.Player p) { return OPENING.get() == p; }

    /** Opens what the player looks at; false when there is nothing to open there. */
    public static boolean use(ServerPlayer p) {
        var hit = Targets.block(p, REACH);
        if (hit == null) return false;
        var level = p.serverLevel();
        var pos = hit.getBlockPos();
        var state = level.getBlockState(pos);
        if (!level.mayInteract(p, pos)) return false;
        if (state.getBlock() instanceof DoorBlock door) {
            if (state.getValue(BlockStateProperties.OPEN)) return false;
            door.setOpen(p, level, state, pos, true);
            return true;
        }
        if (state.getBlock() instanceof TrapDoorBlock) {
            if (state.getValue(BlockStateProperties.OPEN)) return false;
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1, 1);
            return true;
        }
        if (level.getBlockEntity(pos) instanceof BaseContainerBlockEntity) {
            OPENING.set(p);
            try {
                return state.useWithoutItem(level, p, hit).consumesAction();
            } finally {
                OPENING.remove();
            }
        }
        return false;
    }

    private TowerKey() {}
}
