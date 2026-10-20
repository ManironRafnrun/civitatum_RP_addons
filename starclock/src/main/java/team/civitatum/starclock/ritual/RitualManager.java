package team.civitatum.starclock.ritual;

import java.util.Random;
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
import team.civitatum.starclock.item.StarToolItem;
import team.civitatum.starclock.network.CancelRitualPayload;
import team.civitatum.starclock.network.RitualStatePayload;
import team.civitatum.starclock.network.ShowCardPayload;
import team.civitatum.starclock.prediction.Constellation;

// Единственный активный обряд резервирует весь мир, включая подготовку.
public final class RitualManager {
    private static RitualState active;

    public static boolean start(ServerPlayer player, InteractionHand hand, ItemStack stack, StarToolItem tool) {
        String failure = conditionFailure(player);
        try { StarClockConfig.validate(); }
        catch (IllegalArgumentException error) { failure = "configuration"; }
        if (failure == null && active != null) failure = "busy";
        long remaining = StarClockData.get(player.server).remainingCooldown(player.server.overworld().getGameTime());
        if (failure == null && remaining > 0) {
            long days = (remaining + 23_999L) / 24_000L;
            player.displayClientMessage(Component.translatable("message.civitatum_starclock.cooldown", days), true);
            return false;
        }
        if (failure != null) {
            player.displayClientMessage(Component.translatable("message.civitatum_starclock." + failure), true);
            return false;
        }
        active = new RitualState(player.getUUID(), hand, stack, tool.kind(), tool.tier(),
                player.server.overworld().getGameTime());
        sendState(player);
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
        if (ownsRitual(player) && !active.isDrawing()) {
            // Ванильный счётчик использования может закончиться до ServerTickEvent.Post.
            active.preparationFinished = true;
        }
    }

    public static void release(ServerPlayer player) {
        if (ownsRitual(player) && !active.isDrawing() && !active.preparationFinished) {
            cancel(player, "released");
        }
    }


    public static boolean isActive(java.util.UUID player) {
        return active != null && active.player.equals(player);
    }

    private static boolean ownsRitual(ServerPlayer player) {
        return active != null && active.player.equals(player.getUUID());
    }

    private static void sendState(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new RitualStatePayload(active.session, active.hand,
                active.kind, active.tier, active.isDrawing(),
                active.isDrawing() ? active.drawingStartedAt : active.preparationStartedAt,
                active.isDrawing() ? Constellation.draw(new Random(StarClockData.get(player.server).readingSeed())) : null));
    }

    private static void cancel(ServerPlayer player, String reason) {
        var previous = active;
        active = null;
        if (previous == null) return;
        player.stopUsingItem();
        PacketDistributor.sendToPlayer(player, new CancelRitualPayload(previous.session, reason));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (active == null) return;
        MinecraftServer server = event.getServer();
        ServerPlayer player = server.getPlayerList().getPlayer(active.player);
        if (player == null) {
            active = null;
            return;
        }
        String failure = conditionFailure(player);
        if (failure != null || player.getItemInHand(active.hand) != active.stack) {
            cancel(player, failure == null ? "changed_item" : failure);
            return;
        }
        long now = server.overworld().getGameTime();
        if (!active.isDrawing()) {
            if (!active.preparationFinished && (!player.isUsingItem() || player.getUsedItemHand() != active.hand)) {
                cancel(player, "released");
                return;
            }
            if (active.preparationFinished && now - active.preparationStartedAt >= RitualRules.PREPARATION_TICKS) {
                active.drawingStartedAt = now;
                sendState(player);
            }
        } else if (now - active.drawingStartedAt >= RitualRules.DRAWING_TICKS) {
            finish(player, now);
        }
    }


    private static void finish(ServerPlayer player, long now) {
        RitualState finished = active;
        Random random = new Random(StarClockData.get(player.server).readingSeed());
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
        StarClockData.get(player.server).schedule(now, pending);
        active = null;
        player.stopUsingItem();
        PacketDistributor.sendToPlayer(player, new ShowCardPayload(finished.session, sign, orientation, finished.tier,
                finished.tier.strength() >= 2 ? plan.category() : "",
                finished.tier.strength() >= 3 ? plan.target() : ""));
    }

    @SubscribeEvent
    public static void damaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0 && event.getEntity() instanceof ServerPlayer player && ownsRitual(player)) {
            cancel(player, "damaged");
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ownsRitual(player)) active = null;
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        active = null;
    }

    private RitualManager() {}
}
