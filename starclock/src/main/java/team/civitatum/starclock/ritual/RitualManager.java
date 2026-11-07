package team.civitatum.starclock.ritual;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import team.civitatum.starclock.config.StarClockConfig;
import team.civitatum.starclock.prediction.PredictionPlan;
import team.civitatum.starclock.prediction.PredictionRegistry;
import team.civitatum.starclock.prediction.PredictionBonuses;
import team.civitatum.starclock.prediction.PredictionTimer;
import team.civitatum.starclock.data.PendingPrediction;
import team.civitatum.starclock.CivitatumStarclock;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import team.civitatum.starclock.data.StarClockData;
import team.civitatum.starclock.discord.DiscordNotifier;
import team.civitatum.starclock.item.StarToolItem;
import team.civitatum.starclock.network.CancelRitualPayload;
import team.civitatum.starclock.network.RitualStatePayload;
import team.civitatum.starclock.network.ShowCardPayload;
import team.civitatum.starclock.prediction.Constellation;

// Предсказание действует вокруг одного игрока, поэтому обряды разных игроков друг другу не мешают.
public final class RitualManager {
    private static final Map<UUID, RitualState> ACTIVE = new HashMap<>();

    public static boolean start(ServerPlayer player, InteractionHand hand, ItemStack stack, StarToolItem tool) {
        String failure = conditionFailure(player);
        try { StarClockConfig.validate(); }
        catch (IllegalArgumentException error) { failure = "configuration"; }
        if (failure == null && ACTIVE.containsKey(player.getUUID())) failure = "busy";
        long remaining = StarClockData.get(player.server)
                .remainingCooldown(player.getUUID(), player.server.overworld().getDayTime());
        if (failure == null && remaining > 0) {
            long days = (remaining + 23_999L) / 24_000L;
            player.displayClientMessage(Component.translatable("message.civitatum_starclock.cooldown", days), true);
            return false;
        }
        if (failure != null) {
            player.displayClientMessage(Component.translatable("message.civitatum_starclock." + failure), true);
            return false;
        }
        var state = new RitualState(player.getUUID(), hand, stack, tool.kind(), tool.tier(),
                player.server.overworld().getGameTime());
        ACTIVE.put(player.getUUID(), state);
        sendState(player, state);
        return true;
    }

    private static String conditionFailure(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping()) return "unavailable";
        if (player.level().dimension() != Level.OVERWORLD) return "dimension";
        var level = player.serverLevel();
        if (!RitualRules.isNight(level.getDayTime())) return "night";
        if (level.isRaining() || level.isThundering()) return "weather";
        if (!level.canSeeSky(player.blockPosition().above())) return "sky";
        if (player.getXRot() > RitualRules.MAX_LOOK_PITCH) return "look_up";
        return null;
    }

    public static void beginDrawing(ServerPlayer player) {
        var state = ACTIVE.get(player.getUUID());
        if (state != null && !state.isDrawing()) {
            // Ванильный счётчик использования может закончиться до ServerTickEvent.Post.
            state.preparationFinished = true;
        }
    }

    public static void release(ServerPlayer player) {
        var state = ACTIVE.get(player.getUUID());
        if (state != null && !state.isDrawing() && !state.preparationFinished) {
            cancel(player, "released");
        }
    }

    public static boolean isActive(UUID player) {
        return ACTIVE.containsKey(player);
    }

    private static void sendState(ServerPlayer player, RitualState state) {
        PacketDistributor.sendToPlayer(player, new RitualStatePayload(state.session, state.hand,
                state.kind, state.tier, state.isDrawing(),
                state.isDrawing() ? state.drawingStartedAt : state.preparationStartedAt,
                state.isDrawing() ? Constellation.draw(new Random(state.readingSeed)) : null));
    }

    private static void cancel(ServerPlayer player, String reason) {
        var previous = ACTIVE.remove(player.getUUID());
        if (previous == null) return;
        player.stopUsingItem();
        PacketDistributor.sendToPlayer(player, new CancelRitualPayload(previous.session, reason));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        // Копия: завершение и отмена убирают обряд из карты прямо во время обхода.
        for (var state : List.copyOf(ACTIVE.values())) tick(event.getServer(), state);
    }

    private static void tick(MinecraftServer server, RitualState state) {
        ServerPlayer player = server.getPlayerList().getPlayer(state.player);
        if (player == null) {
            ACTIVE.remove(state.player);
            return;
        }
        String failure = conditionFailure(player);
        if (failure != null || player.getItemInHand(state.hand) != state.stack) {
            cancel(player, failure == null ? "changed_item" : failure);
            return;
        }
        long now = server.overworld().getGameTime();
        if (!state.isDrawing()) {
            if (!state.preparationFinished && (!player.isUsingItem() || player.getUsedItemHand() != state.hand)) {
                cancel(player, "released");
                return;
            }
            if (state.preparationFinished && now - state.preparationStartedAt >= RitualRules.PREPARATION_TICKS) {
                state.readingSeed = StarClockData.get(server).readingSeed(state.player);
                state.drawingStartedAt = now;
                sendState(player, state);
            }
        } else if (now - state.drawingStartedAt >= RitualRules.DRAWING_TICKS) {
            finish(player, state, now);
        }
    }

    private static void finish(ServerPlayer player, RitualState finished, long now) {
        Random random = new Random(finished.readingSeed);
        Constellation sign = Constellation.draw(random);
        var orientation = sign.drawOrientation(random);
        PredictionPlan plan;
        long delay;
        try {
            plan = PredictionRegistry.catalog().draw(sign, orientation, random);
            if (finished.tier.strength() == 3) {
                plan = PredictionBonuses.append(plan);
            }
            delay = PredictionTimer.choose(random, finished.tier.strength(),
                    StarClockConfig.TIMER_MIN.get(),
                    StarClockConfig.TIMER_MAX.get(),
                    StarClockConfig.TIMER_THIRD_MAX.get());
        } catch (RuntimeException error) {
            CivitatumStarclock.LOG.error("Не удалось подготовить предсказание", error);
            cancel(player, "configuration");
            return;
        }
        var pos = player.blockPosition();
        var pending = new PendingPrediction(finished.session, player.getUUID(),
                player.level().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ(),
                now + delay, finished.tier.strength(), plan, false, 0, 0, 0);
        StarClockData.get(player.server).schedule(player.server.overworld().getDayTime(), pending);
        ACTIVE.remove(player.getUUID());
        player.stopUsingItem();
        var card = new ShowCardPayload(finished.session, sign, orientation, finished.tier,
                finished.tier.strength() >= 2 ? plan.category() : "",
                finished.tier.strength() >= 3 ? plan.target() : "");
        PacketDistributor.sendToPlayer(player, card);
        DiscordNotifier.predictionRead(player.getGameProfile().getName(), card);
    }

    @SubscribeEvent
    public static void damaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0 && event.getEntity() instanceof ServerPlayer player) {
            cancel(player, "damaged");
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ACTIVE.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        ACTIVE.clear();
    }

    private RitualManager() {}
}
