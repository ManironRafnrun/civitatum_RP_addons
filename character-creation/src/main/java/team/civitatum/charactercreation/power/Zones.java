package team.civitatum.charactercreation.power;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Areas a power leaves behind for a while. They live in memory: the blocks that mark them are {@link TemporaryBlocks}
 * and leave with a restart, so the area leaves with it too.
 */
public final class Zones {
    public enum Kind {
        /** «Запечатать» of the dwarf: no monster appears in a 9×9×9 cube. */
        SEAL,
        /** «Заводной колокол» of the goblin: monsters within 16 ring with «Звон» and forget the owner. */
        BELL,
        /** «Разжечь» of the Hearth: the campfire heals allies within 6. */
        CAMPFIRE,
        /** «Соляной круг» of the Merchant: the undead cannot come within 8 of the owner. */
        SALT
    }

    /** Half of the dwarf's 9-block cube, measured from the centre of the chain's block. */
    private static final double SEAL_HALF = 4.5;
    private static final double BELL_RADIUS = 16;
    private static final double CAMPFIRE_RADIUS = 6;
    private static final double SALT_RADIUS = 8;
    /** «Звон» lasts 5 s and is renewed every second while the monster stays near the bell ("10b"). */
    private static final int RING_TICKS = 5 * 20;
    /** The campfire heals 1 HP every 2 s: Regeneration I heals every 2.5 s, a campfire of a power a bit more. */
    private static final int CAMPFIRE_HEAL_INTERVAL = 40;
    /** How hard the salt pushes a walking undead back: a little more than its walking speed. */
    private static final double SALT_PUSH = .4;

    private record Zone(Kind kind, ResourceKey<Level> dimension, Vec3 center, UUID owner, long until) {}

    private static final List<Zone> ZONES = new ArrayList<>();

    public static void add(Kind kind, ServerPlayer owner, Vec3 center, int ticks) {
        ZONES.add(new Zone(kind, owner.level().dimension(), center, owner.getUUID(),
            owner.server.overworld().getGameTime() + ticks));
    }

    /** Whether a monster may not appear at this place: inside a dwarf's seal. */
    public static boolean isSealed(Level level, Vec3 at) {
        for (var z : ZONES) {
            if (z.kind != Kind.SEAL || !z.dimension.equals(level.dimension())) continue;
            var d = at.subtract(z.center);
            if (Math.abs(d.x) <= SEAL_HALF && Math.abs(d.y) <= SEAL_HALF && Math.abs(d.z) <= SEAL_HALF) return true;
        }
        return false;
    }

    /** Whether a phantom may not come for this player: a goblin's bell rings near. */
    public static boolean isNearBell(Player p) {
        for (var z : ZONES)
            if (z.kind == Kind.BELL && z.dimension.equals(p.level().dimension())
                && z.center.distanceToSqr(p.position()) <= BELL_RADIUS * BELL_RADIUS) return true;
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (ZONES.isEmpty()) return;
        long now = server.overworld().getGameTime();
        ZONES.removeIf(z -> z.until <= now || server.getLevel(z.dimension) == null);
        for (var z : List.copyOf(ZONES)) {
            ServerLevel level = server.getLevel(z.dimension);
            var owner = server.getPlayerList().getPlayer(z.owner);
            switch (z.kind) {
                case BELL -> { if (now % 20 == 0) bell(level, z, owner, now); }
                case CAMPFIRE -> { if (now % CAMPFIRE_HEAL_INTERVAL == 0) campfire(level, z, owner); }
                case SALT -> { if (owner != null && owner.level() == level) salt(level, owner); }
                case SEAL -> {}
            }
        }
    }

    private static void bell(ServerLevel level, Zone z, ServerPlayer owner, long now) {
        var box = new AABB(z.center, z.center).inflate(BELL_RADIUS);
        for (var mob : level.getEntitiesOfClass(Mob.class, box, m -> m.getType().getCategory() == MobCategory.MONSTER
                && m.distanceToSqr(z.center) <= BELL_RADIUS * BELL_RADIUS)) {
            Effects.give(mob, ModEffects.RING, RING_TICKS, 0);
            if (owner != null && mob.getTarget() == owner) mob.setTarget(null);
        }
        if (now % 60 == 0) level.playSound(null, z.center.x, z.center.y, z.center.z, SoundEvents.BELL_BLOCK,
            SoundSource.BLOCKS, 1.5f, 1);
    }

    private static void campfire(ServerLevel level, Zone z, ServerPlayer owner) {
        for (var p : level.players()) {
            if (p.distanceToSqr(z.center) > CAMPFIRE_RADIUS * CAMPFIRE_RADIUS || !p.isAlive()) continue;
            if (owner == null ? p.getUUID().equals(z.owner) : Targets.isFriendly(owner, p)) p.heal(1);
        }
    }

    private static void salt(ServerLevel level, ServerPlayer owner) {
        var center = owner.position();
        for (var mob : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(SALT_RADIUS),
                m -> m.getType().is(EntityTypeTags.UNDEAD) && m.distanceToSqr(center) < SALT_RADIUS * SALT_RADIUS)) {
            var away = mob.position().subtract(center).multiply(1, 0, 1);
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            away = away.normalize().scale(SALT_PUSH);
            mob.setDeltaMovement(away.x, Math.max(mob.getDeltaMovement().y, 0), away.z);
            mob.hurtMarked = true;
            if (mob.getTarget() == owner) mob.getNavigation().stop();
        }
    }

    /** The owner left the game: an area around the owner has nobody to follow. */
    public static void logout(ServerPlayer p) {
        ZONES.removeIf(z -> z.kind == Kind.SALT && z.owner.equals(p.getUUID()));
    }

    public static boolean hasSalt(ServerPlayer p) {
        for (var z : ZONES) if (z.kind == Kind.SALT && z.owner.equals(p.getUUID())) return true;
        return false;
    }

    private Zones() {}
}
