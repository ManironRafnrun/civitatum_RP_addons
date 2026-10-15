package team.civitatum.couple.client;

import java.lang.reflect.Field;
import net.minecraft.world.entity.player.Player;

/** CCC editor ghosts share a UUID with the player; interaction poses must not leak into that editor. */
public final class CccCompat {
    private static final Field PREVIEW=previewField();
    private static Field previewField(){try{if(net.neoforged.fml.ModList.get().isLoaded("civitatum"))return Class.forName("ru.teacraft.civitatum.client.ClientCharacters").getField("previewEntity");}catch(ReflectiveOperationException ignored){}return null;}
    public static boolean preview(Player p){try{return PREVIEW!=null && PREVIEW.get(null)==p;}catch(IllegalAccessException ignored){return false;}}
    private CccCompat(){}
}
