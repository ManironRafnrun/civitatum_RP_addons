package team.civitatum.charactercreation.power;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import team.civitatum.charactercreation.BirthsignCatalog;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.RaceNetwork;
import team.civitatum.charactercreation.RaceService;
import team.civitatum.charactercreation.RaceState;
import team.civitatum.charactercreation.power.PlayerPowers.ChannelKind;

/** The powers of the birthsigns ("10b", part III), on their own key beside the racial one. */
public final class SignPowers {
    private static final int DEBOUNCE_TICKS = 5;
    private static final int CAMPFIRE_TICKS = 20 * 20;
    private static final int SPIRIT_TICKS = 60 * 20;
    /** «Стая» costs 2 food ("цена — 2 голода"). */
    private static final int SPIRIT_FOOD = 2;
    private static final int SHARE_RADIUS = 8;
    /** «Доля» gives six points of food and as much saturation; the giver is left with 2. */
    private static final int SHARE_FOOD = 6;
    private static final float SHARE_SATURATION_MODIFIER = .5f;
    private static final int SHARE_LEFT = 2;
    private static final int SALT_TICKS = 30 * 20;
    private static final int PULL_RANGE = 16;
    /** Where the pulled target lands: in front of the player, out of reach of the player's own box. */
    private static final double PULL_DISTANCE = 1.5;
    private static final int BOUND_TICKS = 5 * 20;
    private static final int ROLLCALL_RADIUS = 24;
    private static final int ROLLCALL_TICKS = 8 * 20;
    private static final float ROLLCALL_UNDEAD_DAMAGE = 4;
    private static final int BITE_RANGE = 3;
    private static final float BITE_DAMAGE = 8;
    private static final float BITE_PRICE = 4;
    /** Spirit wolves carry this tag with their end time, so a wolf saved with the world leaves after a restart. */
    public static final String SPIRIT_TAG = "civitatum_spirit_until";

    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    public static long cooldownTicks(String sign) {
        var s = BirthsignCatalog.get(sign);
        return s == null ? 0 : s.cooldown() * 20L;
    }

    public static void activate(ServerPlayer p) {
        var character = Traits.character(p);
        if (character == null || !p.isAlive() || p.isSpectator()) return;
        var sign = BirthsignCatalog.get(character.birthsign);
        if (sign == null || !sign.hasPower()) return;
        long now = RaceService.time(p);
        if (now - LAST_REQUEST.getOrDefault(p.getUUID(), -1000L) < DEBOUNCE_TICKS) return;
        LAST_REQUEST.put(p.getUUID(), now);
        if (Channels.isRunning(p)) return;
        var state = p.getData(Civitatum.STATE);
        // Marking a place is free; the path itself waits for the cooldown.
        if (sign.id().equals("wanderer") && (p.isShiftKeyDown() || state.mark() == null)) {
            mark(p, state);
            return;
        }
        if (state.signCooldownUntil() > now) {
            message(p, Civitatum.message("ability_cooldown", (state.signCooldownUntil() - now + 19) / 20));
            return;
        }
        if (!use(p, sign.id())) return;
        // The path is paid when it is walked: a broken focus costs nothing.
        if (!sign.id().equals("wanderer")) {
            p.setData(Civitatum.STATE, state.withSignCooldown(now + sign.cooldown() * 20L));
            RaceNetwork.sync(p);
        }
        message(p, Component.translatable(sign.key("power")));
    }

    private static boolean use(ServerPlayer p, String sign) {
        return switch (sign) {
            case "tower" -> TowerKey.use(p) || fail(p, "power.nothing_to_open");
            case "hearth" -> kindle(p);
            case "beast" -> pack(p);
            case "mother" -> share(p);
            case "merchant" -> salt(p);
            case "spinner" -> pull(p);
            case "shepherd" -> rollcall(p);
            case "wanderer" -> {
                Channels.start(p, ChannelKind.PATH, Channels.PATH_TICKS);
                yield true;
            }
            case "serpent" -> bite(p);
            default -> false;
        };
    }

    private static void mark(ServerPlayer p, RaceState state) {
        p.setData(Civitatum.STATE, state.withMark(new RaceState.Mark(p.level().dimension().location().toString(),
            p.getX(), p.getY(), p.getZ())));
        RaceNetwork.sync(p);
        message(p, Civitatum.message("power.mark_set"));
    }

    private static boolean kindle(ServerPlayer p) {
        // In front of the player, not under: a campfire under the feet would hold the player inside it.
        BlockPos pos = p.blockPosition().relative(p.getDirection());
        if (!TemporaryBlocks.place(p.serverLevel(), pos, Blocks.SOUL_CAMPFIRE.defaultBlockState(), CAMPFIRE_TICKS))
            return fail(p, "no_room");
        Zones.add(Zones.Kind.CAMPFIRE, p, Vec3.atCenterOf(pos), CAMPFIRE_TICKS);
        return true;
    }

    private static boolean pack(ServerPlayer p) {
        if (!p.isCreative() && p.getFoodData().getFoodLevel() < SPIRIT_FOOD) return fail(p, "power.hungry");
        var wolf = EntityType.WOLF.create(p.serverLevel());
        if (wolf == null) return false;
        wolf.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
        wolf.tame(p);
        wolf.setCustomName(Component.translatable("entity.civitatum.spirit_wolf"));
        wolf.getPersistentData().putLong(SPIRIT_TAG, RaceService.time(p) + SPIRIT_TICKS);
        p.serverLevel().addFreshEntity(wolf);
        if (!p.isCreative()) p.getFoodData().setFoodLevel(p.getFoodData().getFoodLevel() - SPIRIT_FOOD);
        p.serverLevel().playSound(null, wolf.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.NEUTRAL, 2, 1);
        return true;
    }

    /** Spirit wolves whose minute is over leave; checked once a second for every loaded wolf with the tag. */
    public static boolean isSpiritGone(Entity e, MinecraftServer server) {
        var data = e.getPersistentData();
        return data.contains(SPIRIT_TAG) && data.getLong(SPIRIT_TAG) <= server.overworld().getGameTime();
    }

    public static boolean isSpirit(Entity e) { return e.getPersistentData().contains(SPIRIT_TAG); }

    private static boolean share(ServerPlayer p) {
        var allies = Targets.around(p, p.position(), SHARE_RADIUS,
            e -> e != p && e instanceof ServerPlayer q && Targets.isFriendly(p, q));
        if (allies.isEmpty()) return fail(p, "power.no_allies");
        for (var e : allies) {
            ((ServerPlayer) e).getFoodData().eat(SHARE_FOOD, SHARE_SATURATION_MODIFIER);
            Effects.give(e, MobEffects.REGENERATION, 4 * 20, 0);
        }
        p.getFoodData().setFoodLevel(Math.min(p.getFoodData().getFoodLevel(), SHARE_LEFT));
        p.getFoodData().setSaturation(0);
        return true;
    }

    private static boolean salt(ServerPlayer p) {
        if (!p.isCreative()) {
            int slot = p.getInventory().findSlotMatchingItem(new ItemStack(Items.SUGAR));
            if (slot < 0) return fail(p, "power.no_sugar");
            p.getInventory().getItem(slot).shrink(1);
        }
        Zones.add(Zones.Kind.SALT, p, p.position(), SALT_TICKS);
        return true;
    }

    private static boolean pull(ServerPlayer p) {
        var target = Targets.looked(p, PULL_RANGE, e -> !Targets.isFriendly(p, e));
        if (target == null) return fail(p, "power.no_target");
        // A boss is bound where it stands: dragging the Queen of the valkyries to one's feet is not a power ("10b").
        if (!target.getType().is(Tags.EntityTypes.BOSSES)) {
            Vec3 to = p.position().add(Targets.facing(p).scale(PULL_DISTANCE + target.getBbWidth() / 2));
            if (target.level().noCollision(target, target.getBoundingBox().move(to.subtract(target.position()))))
                target.teleportTo(to.x, to.y, to.z);
        }
        if (target instanceof Mob mob) mob.setTarget(null);
        Effects.give(target, ModEffects.BOUND, BOUND_TICKS, 0);
        return true;
    }

    private static boolean rollcall(ServerPlayer p) {
        for (LivingEntity e : Targets.around(p, p.position(), ROLLCALL_RADIUS,
                e -> e instanceof Enemy || e.getType().getCategory() == MobCategory.MONSTER)) {
            Effects.give(e, MobEffects.GLOWING, ROLLCALL_TICKS, 0);
            Effects.give(e, MobEffects.MOVEMENT_SLOWDOWN, ROLLCALL_TICKS, 1);
            if (e.getType().is(EntityTypeTags.UNDEAD))
                e.hurt(p.damageSources().indirectMagic(p, p), ROLLCALL_UNDEAD_DAMAGE);
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2, .8f);
        return true;
    }

    private static boolean bite(ServerPlayer p) {
        var target = Targets.looked(p, BITE_RANGE, e -> !Targets.isFriendly(p, e));
        if (target == null) return fail(p, "power.no_target");
        target.hurt(p.damageSources().indirectMagic(p, p), BITE_DAMAGE);
        Effects.give(target, MobEffects.POISON, 10 * 20, 0);
        Effects.give(target, MobEffects.WEAKNESS, 5 * 20, 0);
        p.hurt(p.damageSources().magic(), BITE_PRICE);
        return true;
    }

    public static void logout(ServerPlayer p) { LAST_REQUEST.remove(p.getUUID()); }

    private static boolean fail(ServerPlayer p, String key) {
        message(p, Civitatum.message(key));
        return false;
    }

    private static void message(ServerPlayer p, Component text) { p.displayClientMessage(text, true); }

    private SignPowers() {}
}
