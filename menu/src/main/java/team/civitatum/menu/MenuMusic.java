package team.civitatum.menu;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Streams the menu playlist while no world is loaded and shows the current track and time. */
public final class MenuMusic {
    private static List<Track> tracks = List.of();
    private static ShuffleBag shuffle;
    private static MenuTrack sound;
    private static boolean session;
    private static boolean initialized;
    private static int age;
    private static int retryDelay;
    private static long started;
    private static int lastSecond = -1;
    private static String label = "";
    private static Track selected;

    private MenuMusic() {
    }

    public static void enter() {
        session = true;
        if (!initialized) {
            initialized = true;

            try (InputStream stream = MenuMusic.class.getResourceAsStream("/assets/civitatum_menu/tracks.json")) {
                if (stream == null) {
                    throw new IllegalStateException("Missing tracks.json");
                }

                tracks = List.of(new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Track[].class));
                shuffle = new ShuffleBag(tracks.size(), new Random());
            } catch (Exception e) {
                CivitatumMenu.LOG.error("Cannot load menu playlist", e);
                tracks = List.of();
            }
        }
    }

    public static boolean sessionActive() {
        return session;
    }

    public static void tick(Minecraft minecraft) {
        if (session) {
            if (inMenu(minecraft) && MenuConfig.current.enabled) {
                if (!MenuConfig.current.music
                    || MenuConfig.current.musicVolume <= 0.0
                    || minecraft.options.getSoundSourceVolume(SoundSource.MASTER) <= 0.0F
                    || minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0.0F
                    || tracks.isEmpty()) {
                    stop();
                    label = "Музыка выключена";
                } else if (retryDelay > 0) {
                    retryDelay--;
                } else {
                    if (sound == null) {
                        minecraft.getMusicManager().stopPlaying();
                        selected = tracks.get(shuffle.next());
                        sound = new MenuTrack(selected);
                        minecraft.getSoundManager().play(sound);
                        started = Util.getMillis();
                        age = 0;
                        lastSecond = -1;
                    }

                    age++;
                    boolean active = minecraft.getSoundManager().isActive(sound);
                    if (age > 60 && !active) {
                        if ((double)(Util.getMillis() - started) < selected.durationSeconds() * 1000.0 - 2000.0) {
                            CivitatumMenu.LOG.warn("Menu track {} stopped early or could not start", selected.id());
                            retryDelay = 100;
                        }

                        sound = null;
                    } else {
                        int second = Math.min((int)selected.durationSeconds(), (int)((Util.getMillis() - started) / 1000L));
                        if (second != lastSecond) {
                            lastSecond = second;
                            label = "Сейчас играет: " + selected.title() + "  " + time(second) + " / " + time((int)selected.durationSeconds());
                        }
                    }
                }
            } else {
                session = false;
                stop();
                SeasonalTitleScreen.releaseBackground(minecraft);
            }
        }
    }

    /**
     * The menu ends as soon as a world or server starts loading, not only once the level exists: the game stops all
     * sounds while loading, and the stopped track must not be taken for a broken one or restarted on the loading screen.
     */
    private static boolean inMenu(Minecraft minecraft) {
        return minecraft.level == null && minecraft.getConnection() == null
            && !(minecraft.screen instanceof LevelLoadingScreen || minecraft.screen instanceof ReceivingLevelScreen
                || minecraft.screen instanceof ConnectScreen || minecraft.screen instanceof GenericMessageScreen
                || minecraft.screen instanceof ProgressScreen);
    }

    public static String label() {
        return label;
    }

    public static void next() {
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
            sound = null;
        }

        retryDelay = 0;
    }

    public static void stop() {
        if (sound != null) {
            sound.fadeOut();
            sound = null;
        }

        selected = null;
        lastSecond = -1;
    }

    private static String time(int seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static final class MenuTrack extends AbstractTickableSoundInstance {
        private boolean fading;

        MenuTrack(Track track) {
            super(SoundEvent.createVariableRangeEvent(CivitatumMenu.resource(track.id())), SoundSource.MUSIC, RandomSource.create());
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.looping = false;
            this.volume = 0.01F;
        }

        void fadeOut() {
            this.fading = true;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (this.fading) {
                this.volume = Math.max(0.0F, this.volume - 0.05F);
                if (this.volume == 0.0F) {
                    this.stop();
                }
            } else {
                float target = (float)MenuConfig.current.musicVolume;
                this.volume = this.volume + Math.max(-0.05F, Math.min(0.05F, target - this.volume));
            }
        }
    }

    public record Track(String id, String title, double durationSeconds) {
    }
}
