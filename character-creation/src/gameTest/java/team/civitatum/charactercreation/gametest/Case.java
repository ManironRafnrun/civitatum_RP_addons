package team.civitatum.charactercreation.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.RaceCatalog;
import team.civitatum.charactercreation.RaceService;
import team.civitatum.charactercreation.RaceState;

/**
 * One game test: the arena (a stone floor at y=0 of the 32×12×32 template, night, players ticked), the players and
 * mobs it made, the steps by tick and the checks. Coordinates are relative to the arena; its centre is (16, 1, 16).
 */
final class Case {
    /** The arena's side; the template is gametest/arena. */
    static final int SIZE = 32;
    static final String TEMPLATE = "gametest/arena";

    final GameTestHelper h;
    final ServerLevel level;
    private final List<TestPlayer> players = new ArrayList<>();

    Case(GameTestHelper h) {
        this.h = h;
        this.level = h.getLevel();
        h.setNight();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        for (int x = 0; x < SIZE; x++)
            for (int z = 0; z < SIZE; z++) h.setBlock(x, 0, z, Blocks.STONE);
        h.onEachTick(() -> {
            for (var p : players) if (!p.isRemoved() && p.isAlive()) p.doTick();
        });
    }

    BlockPos pos(int x, int y, int z) { return h.absolutePos(new BlockPos(x, y, z)); }

    Vec3 vec(double x, double y, double z) { return h.absoluteVec(new Vec3(x, y, z)); }

    /** A player of a race and sign standing at (x, 1, z), facing +z. */
    TestPlayer player(String name, String race, String sign, double x, double z) {
        var p = TestPlayer.join(level, name);
        players.add(p);
        // the game test world makes every player creative: invulnerable and free of every price
        p.setGameMode(GameType.SURVIVAL);
        var c = RaceCatalog.defaults(race, "male");
        c.birthsign = sign;
        p.setData(Civitatum.STATE, RaceState.of(c));
        RaceService.apply(p);
        p.setHealth(p.getMaxHealth());
        Vec3 at = vec(x, 1, z);
        p.teleportTo(level, at.x, at.y, at.z, 0, 0);
        return p;
    }

    <T extends Mob> T mob(EntityType<T> type, double x, double z, boolean noAi) {
        T m = type.create(level);
        Vec3 at = vec(x, 1, z);
        m.moveTo(at.x, at.y, at.z, 0, 0);
        m.setPersistenceRequired();
        m.setNoAi(noAi);
        level.addFreshEntity(m);
        return m;
    }

    void block(int x, int y, int z, Block block) { h.setBlock(x, y, z, block); }

    void look(ServerPlayer p, Vec3 target) { p.lookAt(EntityAnchorArgument.Anchor.EYES, target); }

    /** Makes the powers ready again, as a new game day would. */
    void ready(ServerPlayer p) {
        p.setData(Civitatum.STATE, p.getData(Civitatum.STATE).withCooldown(0).withSignCooldown(0));
    }

    long cooldown(ServerPlayer p) { return p.getData(Civitatum.STATE).cooldownUntil() - RaceService.time(p); }

    long signCooldown(ServerPlayer p) { return p.getData(Civitatum.STATE).signCooldownUntil() - RaceService.time(p); }

    /** Health lost from a full bar to one hit; the bar is full again after. */
    float loss(LivingEntity e, DamageSource source, float amount) {
        e.setHealth(e.getMaxHealth());
        e.invulnerableTime = 0;
        e.hurt(source, amount);
        float lost = e.getMaxHealth() - e.getHealth();
        e.setHealth(e.getMaxHealth());
        e.invulnerableTime = 0;
        return lost;
    }

    float exhaustion(Player p) {
        p.getFoodData().setExhaustion(0);
        p.causeFoodExhaustion(1);
        float e = p.getFoodData().getExhaustionLevel();
        p.getFoodData().setExhaustion(0);
        return e;
    }

    /** A vanilla team: allies for the powers. */
    void team(String name, ServerPlayer... members) {
        var board = level.getScoreboard();
        var team = board.getPlayerTeam(name);
        if (team == null) team = board.addPlayerTeam(name);
        for (var m : members) board.addPlayerToTeam(m.getScoreboardName(), team);
    }

    void at(int tick, Runnable step) { h.runAfterDelay(tick, step); }

    /** The test passes at {@code tick} if no check failed before; the players leave. */
    void end(int tick) {
        h.runAfterDelay(tick, () -> {
            for (var p : players) p.leave();
            h.succeed();
        });
    }

    static void check(boolean ok, String what, Object detail) {
        if (!ok) throw new GameTestAssertException(what + " :: " + detail);
    }

    static void check(boolean ok, String what) { check(ok, what, ""); }

    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-3; }

    @SuppressWarnings("unchecked")
    static <T> T field(Object o, Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static void setField(Object o, Class<?> owner, String name, Object value) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            f.set(o, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static Object call(Object o, Class<?> owner, String name, Class<?>[] types, Object... args) {
        try {
            Method m = owner.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(o, args);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException r ? r : new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
