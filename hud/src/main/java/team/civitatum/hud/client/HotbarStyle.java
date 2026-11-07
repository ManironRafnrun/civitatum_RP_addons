package team.civitatum.hud.client;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** How {@link CivitatumHud} shows the hotbar. */
public enum HotbarStyle {
    /** Only the held item, the selected ability and the off hand, next to the bars in the corner. */
    MORROWIND,
    /** All nine hotbar slots and the selected ability in one strip at the bottom centre, as in vanilla. */
    CLASSIC;

    public Component label() {
        return CivitatumInventoryScreen.tr("hotbar." + name().toLowerCase(Locale.ROOT));
    }

    public HotbarStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
