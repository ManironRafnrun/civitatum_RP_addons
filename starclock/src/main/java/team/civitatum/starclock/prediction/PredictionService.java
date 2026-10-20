package team.civitatum.starclock.prediction;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import team.civitatum.starclock.CivitatumStarclock;
import team.civitatum.starclock.ability.TowerKeyService;
import team.civitatum.starclock.config.StarClockConfig;
import team.civitatum.starclock.data.PendingPrediction;
import team.civitatum.starclock.data.StarClockData;
import team.civitatum.starclock.prediction.handlers.DurabilityHandler;

public final class PredictionService {
    private record DamageGuard(UUID player, int tier) {}
    private static final ThreadLocal<DamageGuard> DAMAGE_GUARD = new ThreadLocal<>();

    public static void guardedDamage(ActionContext context, float amount, boolean magic) {
        DamageGuard previous = DAMAGE_GUARD.get();
        DAMAGE_GUARD.set(new DamageGuard(context.player().getUUID(), context.tier()));
        try {
            context.player().hurt(magic ? context.level().damageSources().magic()
                    : context.level().damageSources().generic(), amount);
        } finally {
            if (previous == null) DAMAGE_GUARD.remove();
            else DAMAGE_GUARD.set(previous);
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void nonLethalDamage(LivingDamageEvent.Pre event) {
        DamageGuard guard = DAMAGE_GUARD.get();
        if (guard != null && event.getEntity().getUUID().equals(guard.player())) {
            event.setNewDamage(SafeNumbers.damage(event.getNewDamage(), event.getEntity().getHealth(), guard.tier()));
        }
    }

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent event) {
        event.addListener(new PredictionRegistry());
    }

    @SubscribeEvent
    public static void starting(ServerStartingEvent event) {
        StarClockConfig.validate();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 20 != 0) return;
        var data = StarClockData.get(server);
        cleanup(server, data, now);
        int budget = 64;
        for (var pending : data.pending()) {
            if (budget <= 0) break;
            if (!pending.isDue(now)) continue;
            var player = server.getPlayerList().getPlayer(pending.player());
            if (player == null || !player.isAlive() || player.isSpectator()) {
                if (!pending.waited()) data.update(pending.waiting());
                continue;
            }
            budget -= execute(player, data, pending, now, Math.min(budget, 16));
        }
    }

    private static int execute(ServerPlayer player, StarClockData data, PendingPrediction initial, long now, int budget) {
        PendingPrediction pending = initial;
        int used = 0;
        while (pending.nextAction() < pending.plan().actions().size() && used < budget) {
            used++;
            try {
                if (!PredictionExecutor.runNext(new ActionContext(player, data, pending, now))) {
                    PendingPrediction retry = pending.retry(now);
                    // Поиск деревни не повторяется чаще раза в минуту.
                    if ("village_map".equals(Parameters.text(pending.plan().actions().get(pending.nextAction()).params(),
                            "special", ""))) {
                        retry = new PendingPrediction(retry.id(), retry.player(), retry.dimension(), retry.x(), retry.y(), retry.z(),
                                retry.dueGameTime(), retry.tier(), retry.plan(), retry.waited(), retry.nextAction(),
                                retry.attempts(), now + 1200);
                    }
                    data.update(retry);
                    return used;
                }
                pending = pending.advance();
                data.update(pending);
            } catch (RuntimeException error) {
                data.update(pending.retry(now));
                if (pending.attempts() == 0 || (pending.attempts() & (pending.attempts() - 1)) == 0) {
                    CivitatumStarclock.LOG.error("Не удалось исполнить предсказание {} для {}; оно остаётся в очереди",
                            pending.plan().eventId(), player.getUUID(), error);
                }
                return used;
            }
        }
        if (pending.nextAction() >= pending.plan().actions().size()) {
            PredictionExecutor.announce(new ActionContext(player, data, pending, now));
            data.removePrediction(pending.id());
        }
        return used;
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TowerKeyService.sync(player);
            processPlayer(player);
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TowerKeyService.sync(player);
            processPlayer(player);
        }
    }

    private static void processPlayer(ServerPlayer player) {
        var data = StarClockData.get(player.server);
        long now = player.server.overworld().getGameTime();
        int budget = 64;
        for (var pending : data.pending()) {
            if (budget <= 0) break;
            if (pending.player().equals(player.getUUID()) && pending.isDue(now) && player.isAlive()) {
                budget -= execute(player, data, pending, now, Math.min(budget, 16));
            }
        }
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.server.overworld().getGameTime();
        if (now % 20 != 0) return;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            DurabilityHandler.expireTemporaryEnchantment(player, player.getInventory().getItem(i), now);
        }
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension)));
    }

    private static void cleanup(MinecraftServer server, StarClockData data, long now) {
        data.expireZones(now);
        for (var entry : data.spawns()) {
            if (now < entry.expiresAt()) continue;
            var level = level(server, entry.dimension());
            if (level == null) continue;
            var entity = level.getEntity(entry.entity());
            if (entity != null) {
                entity.discard();
                data.removeSpawn(entry.entity());
            }
        }
        for (var entry : data.blocks()) {
            if (now < entry.expiresAt()) continue;
            var level = level(server, entry.dimension());
            var pos = new BlockPos(entry.x(), entry.y(), entry.z());
            if (level == null || !level.hasChunkAt(pos)) continue;
            var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(entry.placedBlock()));
            if (block.isPresent() && level.getBlockState(pos).equals(block.get().defaultBlockState())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            data.removeBlock(entry.key());
        }
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var entity = event.getEntity();
        var data = StarClockData.get(level.getServer());
        long now = level.getServer().overworld().getGameTime();
        long expiry = entity.getPersistentData().getLong("StarclockExpires");
        if (expiry > 0 && now >= expiry) {
            event.setCanceled(true);
            entity.discard();
            data.removeSpawn(entity.getUUID());
            return;
        }
        if (expiry > 0) data.addSpawn(new team.civitatum.starclock.data.TimedSpawn(
                entity.getUUID(), level.dimension().location().toString(), expiry));
        if (!event.loadedFromDisk() && entity instanceof Enemy
                && data.isPeaceful(level.dimension().location().toString(), entity.getX(), entity.getY(), entity.getZ(), now)) {
            event.setCanceled(true);
        }
        if (entity instanceof Wolf wolf && wolf.getPersistentData().getBoolean("StarclockFled")) flee(wolf);
    }

    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity().getRemovalReason() != null
                && event.getEntity().getRemovalReason().shouldDestroy()) {
            StarClockData.get(level.getServer()).removeSpawn(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            StarClockData.get(level.getServer()).removeSpawn(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void target(LivingChangeTargetEvent event) {
        var entity = event.getEntity();
        if (entity.getPersistentData().getBoolean("StarclockAlly")
                && !(event.getNewAboutToBeSetTarget() instanceof Enemy)) {
            event.setNewAboutToBeSetTarget(null);
        }
        if (entity.getPersistentData().getBoolean("StarclockFled")
                && event.getNewAboutToBeSetTarget() instanceof Player) event.setNewAboutToBeSetTarget(null);
    }

    @SubscribeEvent
    public static void bitten(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0 && event.getSource().getEntity() instanceof Wolf wolf
                && wolf.getPersistentData().getBoolean("StarclockBiteAndFlee")) {
            wolf.getPersistentData().putBoolean("StarclockFled", true);
            wolf.getPersistentData().putBoolean("StarclockBiteAndFlee", false);
            flee(wolf);
        }
    }

    private static void flee(Wolf wolf) {
        wolf.setTarget(null);
        wolf.setRemainingPersistentAngerTime(0);
        wolf.setPersistentAngerTarget(null);
        wolf.goalSelector.addGoal(0, new AvoidEntityGoal<>(wolf, Player.class, 24, 1.5, 1.7));
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        var tag = event.getEntity().getPersistentData();
        if (tag.getBoolean("StarclockNoDrops")) event.getDrops().clear();
        int iron = tag.getInt("StarclockGolemIron");
        if (iron > 0) {
            event.getDrops().clear();
            var entity = event.getEntity();
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(entity.level(),
                    entity.getX(), entity.getY(), entity.getZ(), new ItemStack(Items.IRON_INGOT, iron)));
        }
    }

    @SubscribeEvent
    public static void experience(LivingExperienceDropEvent event) {
        if (event.getEntity().getPersistentData().getBoolean("StarclockNoDrops")) event.setDroppedExperience(0);
    }

    @SubscribeEvent
    public static void broken(BlockEvent.BreakEvent event) {
        untrackBlock(event);
    }

    @SubscribeEvent
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        untrackBlock(event);
    }

    private static void untrackBlock(BlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var pos = event.getPos();
            StarClockData.get(level.getServer()).removeBlock(level.dimension().location()
                    + "/" + pos.getX() + "/" + pos.getY() + "/" + pos.getZ());
        }
    }

    private PredictionService() {}
}
