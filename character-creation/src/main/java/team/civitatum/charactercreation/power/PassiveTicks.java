package team.civitatum.charactercreation.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.mixin.AbstractFurnaceAccessor;

/** The passives that act over time. Every effect given here is a passive one: without particles. */
public final class PassiveTicks {
    /**
     * Night vision is renewed every 10 s for 30 s: vanilla makes the screen flicker in its last 10 s
     * ("обновлять раз в 10 с (иначе мигает на последних секундах)").
     */
    private static final int NIGHT_VISION_EVERY = 200;
    private static final int NIGHT_VISION_TICKS = 600;
    /** «Подземный»: below y=32 ("ниже y=32"). */
    private static final int UNDERGROUND_Y = 32;
    private static final int SAPLING_RADIUS = 16;
    /**
     * Random block picks per tick that double the growth of saplings around a firbolg: vanilla gives every block of a
     * section 3 random ticks per 4096 blocks per tick, and the 33³ cube around the firbolg holds about 36 000.
     */
    private static final int SAPLING_PICKS = 27;
    private static final int FURNACE_RADIUS = 8;
    /** Furnaces cook 1.25 times faster: a quarter of a tick more each tick, so 5 more per second. */
    private static final int FURNACE_EXTRA_PROGRESS = 5;
    /**
     * Fuel burns 15% longer: 1 - 1/1.15 of every second is given back, 2.6 ticks; given as 2 or 3 at random, 2.6 on
     * average.
     */
    private static final double FURNACE_FUEL_BACK = 20 * (1 - 1 / 1.15);
    private static final int SHEPHERD_RADIUS = 12;
    private static final int HEARTH_RADIUS = 8;
    /** The Hearth heals 1 HP once in 5 s near a fire. */
    private static final int HEARTH_EVERY = 100;
    /** The Beast rides 15% faster ("верхом ты быстрее на 15%"). */
    private static final double MOUNT_BONUS = .15;
    private static final net.minecraft.resources.ResourceLocation MOUNT_ID = Civitatum.res("beast_mount");

    public static void tick(ServerPlayer p) {
        String race = Traits.race(p), sign = Traits.sign(p);
        if (race.isEmpty()) return;
        long tick = p.tickCount;
        switch (race) {
            case "dwarf", "astral_elf", "night_elf" -> nightVision(p, tick);
            case "dark_elf" -> {
                nightVision(p, tick);
                if (tick % 20 == 0) underground(p);
            }
            case "firbolg" -> saplings(p);
            case "fire_dragonborn" -> { if (tick % 20 == 0) furnaces(p); }
            case "frost_dragonborn" -> { if (p.getTicksFrozen() > 0) p.setTicksFrozen(0); }
            default -> {}
        }
        switch (sign) {
            case "shepherd" -> { if (tick % 20 == 0) shepherd(p); }
            case "hearth" -> {
                if (tick % HEARTH_EVERY == 0 && p.getHealth() < p.getMaxHealth() && isNearFire(p)) p.heal(1);
            }
            default -> {}
        }
        mount(p, sign.equals("beast"));
    }

    private static void nightVision(ServerPlayer p, long tick) {
        if (tick % NIGHT_VISION_EVERY == 0) Effects.passive(p, MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, false);
    }

    private static void underground(ServerPlayer p) {
        boolean below = p.getY() < UNDERGROUND_Y;
        if (below && !p.hasEffect(ModEffects.UNDERGROUND))
            Effects.passive(p, ModEffects.UNDERGROUND, net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION,
                true);
        else if (!below && p.hasEffect(ModEffects.UNDERGROUND)) p.removeEffect(ModEffects.UNDERGROUND);
    }

    private static void saplings(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        var random = level.getRandom();
        BlockPos center = p.blockPosition();
        for (int i = 0; i < SAPLING_PICKS; i++) {
            BlockPos pos = center.offset(random.nextInt(2 * SAPLING_RADIUS + 1) - SAPLING_RADIUS,
                random.nextInt(2 * SAPLING_RADIUS + 1) - SAPLING_RADIUS,
                random.nextInt(2 * SAPLING_RADIUS + 1) - SAPLING_RADIUS);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.SAPLINGS) && state.isRandomlyTicking()) state.randomTick(level, pos, random);
        }
    }

    private static void furnaces(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos center = p.blockPosition();
        int from = SectionPos.blockToSectionCoord(center.getX() - FURNACE_RADIUS);
        int to = SectionPos.blockToSectionCoord(center.getX() + FURNACE_RADIUS);
        int fromZ = SectionPos.blockToSectionCoord(center.getZ() - FURNACE_RADIUS);
        int toZ = SectionPos.blockToSectionCoord(center.getZ() + FURNACE_RADIUS);
        for (int cx = from; cx <= to; cx++)
            for (int cz = fromZ; cz <= toZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof AbstractFurnaceBlockEntity furnace)
                        || be.getBlockPos().distSqr(center) > FURNACE_RADIUS * FURNACE_RADIUS) continue;
                    var access = (AbstractFurnaceAccessor) furnace;
                    if (access.civitatum$getLitTime() <= 0) continue;
                    int extra = (int) FURNACE_FUEL_BACK + (level.getRandom().nextDouble()
                        < FURNACE_FUEL_BACK - (int) FURNACE_FUEL_BACK ? 1 : 0);
                    access.civitatum$setLitTime(access.civitatum$getLitTime() + extra);
                    int progress = access.civitatum$getCookingProgress();
                    // Only a furnace that cooks; the last tick is left to vanilla, which finishes the item.
                    if (progress > 0) access.civitatum$setCookingProgress(Math.min(progress + FURNACE_EXTRA_PROGRESS,
                        access.civitatum$getCookingTotalTime() - 1));
                    furnace.setChanged();
                }
            }
    }

    private static void shepherd(ServerPlayer p) {
        for (var mob : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(SHEPHERD_RADIUS),
                m -> m.getType().is(EntityTypeTags.UNDEAD) && m.distanceToSqr(p) <= SHEPHERD_RADIUS * SHEPHERD_RADIUS))
            Effects.passive(mob, MobEffects.WEAKNESS, 40, true);
    }

    /** A burning fire within 8 blocks: a campfire, a furnace, a torch, a lantern, a candle, fire itself. */
    private static boolean isNearFire(ServerPlayer p) {
        var level = p.level();
        for (BlockPos pos : BlockPos.betweenClosed(p.blockPosition().offset(-HEARTH_RADIUS, -HEARTH_RADIUS,
                -HEARTH_RADIUS), p.blockPosition().offset(HEARTH_RADIUS, HEARTH_RADIUS, HEARTH_RADIUS))) {
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) continue;
            if (s.is(BlockTags.FIRE) || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH)
                || s.is(Blocks.SOUL_WALL_TORCH) || s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN)) return true;
            boolean lit = s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
            if (lit && (s.is(BlockTags.CAMPFIRES) || s.is(BlockTags.CANDLES)
                || s.getBlock() instanceof AbstractFurnaceBlock)) return true;
        }
        return false;
    }

    /** The Beast's mount gets the bonus while ridden, and loses it when the Beast gets off. */
    private static void mount(ServerPlayer p, boolean beast) {
        var state = PlayerPowers.peek(p);
        var vehicle = beast && p.getVehicle() instanceof LivingEntity ridden ? ridden : null;
        if (vehicle == null && (state == null || state.mount == null)) return;
        var powers = PlayerPowers.of(p);
        if (powers.mount != null && (vehicle == null || !vehicle.getUUID().equals(powers.mount))) {
            if (p.serverLevel().getEntity(powers.mount) instanceof LivingEntity old) bonus(old, false);
            powers.mount = null;
        }
        if (vehicle != null && powers.mount == null) {
            bonus(vehicle, true);
            powers.mount = vehicle.getUUID();
        }
    }

    private static void bonus(LivingEntity mount, boolean on) {
        for (var attribute : java.util.List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            var instance = mount.getAttribute(attribute);
            if (instance == null) continue;
            instance.removeModifier(MOUNT_ID);
            if (on) instance.addTransientModifier(new AttributeModifier(MOUNT_ID, MOUNT_BONUS,
                Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Takes the mount bonus off when the Beast leaves or dies in the saddle. */
    public static void dismount(ServerPlayer p) {
        var powers = PlayerPowers.peek(p);
        if (powers == null || powers.mount == null) return;
        if (p.serverLevel().getEntity(powers.mount) instanceof LivingEntity old) bonus(old, false);
        powers.mount = null;
    }

    private PassiveTicks() {}
}
