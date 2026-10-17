package team.civitatum.charactercreation.client;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import team.civitatum.charactercreation.*;

public final class ClientCharacters {

    public static final Map<UUID, RaceState> STATES = new HashMap<>();
    public static CharacterData preview;
    public static UUID previewPlayer;
    public static float previewTime;
    public static net.minecraft.world.entity.player.Player previewEntity;

    public static boolean isPreview(net.minecraft.world.entity.Entity entity) {
        return preview != null && entity == previewEntity;
    }

    public static RaceState state(Player p) {
        return STATES.getOrDefault(p.getUUID(), p.getData(Civitatum.STATE));
    }

    public static CharacterData get(Player p) {
        return isPreview(p) ? preview : state(p).character();
    }

    public static void accept(RaceNetwork.Clientbound packet) {
        Minecraft game = Minecraft.getInstance();
        switch (packet.kind()) {
            case 0 -> {
                RaceState state = RaceState.read(packet.data());
                STATES.put(packet.player(), state);
                if (game.level != null) for (Player p : game.level.players())
                    if (p.getUUID().equals(packet.player())) {
                        p.setData(Civitatum.STATE, state);
                        p.refreshDimensions();
                        p.refreshDisplayName();
                    }
                FemaleGenderBridge.apply(packet.player(), state.character(), false);
            }
            case 1, 2, 5 -> {
                RaceState state = RaceState.read(packet.data());
                STATES.put(packet.player(), state);
                game.setScreen(new CharacterScreen(packet.token(), packet.kind() == 1 ? EditorMode.CREATE : packet.kind() == 5 ? EditorMode.COSMETIC : EditorMode.FULL, state.character()));
            }
            case 3 -> {
                game.setScreen(null);
                if (game.player != null) game.player.displayClientMessage(Component.literal(packet.data()), true);
            }
            case 4 -> {
                if (game.screen instanceof CharacterScreen screen) screen.error(packet.data());
            }
        }
    }

    public static void clear() {
        STATES.clear();
        preview = null;
        previewPlayer = null;
        previewEntity = null;
        FemaleGenderBridge.clear();
        SkinTemplates.clear();
    }

}
