package team.civitatum.charactercreation.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

/**
 * A survival player that logs in through the server's own player list, as a client would. GameTestHelper's mock player
 * is creative: invulnerable and free of every price, so it cannot test a power. Its messages are kept for the checks.
 * Nothing ticks a player without a network connection, so {@link Case} ticks it.
 */
final class TestPlayer extends ServerPlayer {
    final List<String> messages = new ArrayList<>();

    private TestPlayer(ServerLevel level, GameProfile profile) {
        super(level.getServer(), level, profile, ClientInformation.createDefault());
    }

    static TestPlayer join(ServerLevel level, String name) {
        // a new UUID every run: the game test world is kept, and a known UUID would log in with last run's data
        var profile = new GameProfile(UUID.randomUUID(), name.length() > 16 ? name.substring(0, 16) : name);
        var player = new TestPlayer(level, profile);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player,
            CommonListenerCookie.createInitial(profile, false));
        player.noSpawnProtection();
        return player;
    }

    /** A new player cannot be hurt for its first three seconds; the tests hurt it at once. */
    private void noSpawnProtection() {
        try {
            Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(this, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    void leave() {
        server.getPlayerList().remove(this);
    }

    boolean hasSaid(String key) {
        return messages.stream().anyMatch(m -> m.endsWith(key));
    }

    @Override
    public void displayClientMessage(Component c, boolean actionBar) { messages.add(key(c)); }

    @Override
    public void sendSystemMessage(Component c) { messages.add(key(c)); }

    @Override
    public void sendSystemMessage(Component c, boolean overlay) { messages.add(key(c)); }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }
}
