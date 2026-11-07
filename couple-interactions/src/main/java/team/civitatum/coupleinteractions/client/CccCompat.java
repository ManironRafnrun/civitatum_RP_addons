package team.civitatum.coupleinteractions.client;

import java.lang.reflect.Field;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/** CCC editor ghosts share a UUID with the player; interaction poses must not leak into that editor. */
public final class CccCompat {
    private static final Field PREVIEW = previewField();

    private static Field previewField() {
        try {
            if (ModList.get().isLoaded("civitatum")) {
                return Class.forName("team.civitatum.charactercreation.client.ClientCharacters")
                    .getField("previewEntity");
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    public static boolean preview(Player p) {
        try {
            return PREVIEW != null && PREVIEW.get(null) == p;
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    private CccCompat() {}
}
