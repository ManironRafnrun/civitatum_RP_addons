package team.civitatum.charactercreation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Shulker;
import team.civitatum.charactercreation.power.Traits;

/**
 * «Эндер-слух» of the end dragonborn: endermen and shulkers within 32 blocks are outlined through walls for this
 * player only. A server Glowing effect would show them to everyone ("иначе их увидят все").
 */
public final class EnderSense {
    private static final double RADIUS = 32;

    public static boolean isSensed(Entity entity) {
        if (!(entity instanceof EnderMan) && !(entity instanceof Shulker)) return false;
        var player = Minecraft.getInstance().player;
        return player != null && Traits.isRace(player, "end_dragonborn")
            && player.distanceToSqr(entity) <= RADIUS * RADIUS;
    }

    private EnderSense() {}
}
