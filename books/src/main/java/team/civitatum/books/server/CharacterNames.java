package team.civitatum.books.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Optional CCC integration reads the server attachment, never a client-supplied signature or team prefix. */
final class CharacterNames {
    static String author(ServerPlayer player) {
        var type = NeoForgeRegistries.ATTACHMENT_TYPES.get(ResourceLocation.fromNamespaceAndPath("civitatum", "character"));
        if (type != null && player.hasData(type)) {
            String name = characterName(player.getData(type));
            if (!name.isEmpty()) return name;
        }
        return player.getGameProfile().getName();
    }

    static String characterName(Object state) {
        try {
            // CCC exposes RaceState.character() and CharacterData's public name/selected fields.
            Object character = state.getClass().getMethod("character").invoke(state);
            if (!character.getClass().getField("selected").getBoolean(character)) return "";
            String name = BookSigning.clean((String) character.getClass().getField("name").get(character));
            return name.isBlank() ? "" : name;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "";
        }
    }

    private CharacterNames() {}
}
