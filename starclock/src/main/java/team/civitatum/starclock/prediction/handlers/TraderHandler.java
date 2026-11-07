package team.civitatum.starclock.prediction.handlers;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.neoforged.neoforge.event.EventHooks;
import team.civitatum.starclock.config.StarClockConfig;
import team.civitatum.starclock.data.TimedSpawn;
import team.civitatum.starclock.prediction.ActionContext;
import team.civitatum.starclock.prediction.Constellation;
import team.civitatum.starclock.prediction.Parameters;
import team.civitatum.starclock.prediction.VillagersReborn;
import team.civitatum.starclock.prediction.WorldSafety;
import team.civitatum.starclock.trader.SignTrades;

public final class TraderHandler implements ActionHandler {
    @Override
    public boolean run(ActionContext context, JsonObject params) {
        var type = VillagersReborn.resolve(EntityType.WANDERING_TRADER);
        if (!(type.create(context.level()) instanceof WanderingTrader trader)) {
            throw new IllegalStateException("Не удалось создать торговца");
        }
        var angle = context.random().nextDouble() * Math.PI * 2;
        var wanted = context.player().position().add(Math.cos(angle) * 10, 0, Math.sin(angle) * 10);
        var position = WorldSafety.nearby(context.level(), trader, wanted, 4);
        if (position == null) return false;
        trader.moveTo(position.x, position.y, position.z, 0, 0);
        var difficulty = context.level().getCurrentDifficultyAt(trader.blockPosition());
        EventHooks.finalizeMobSpawn(trader, context.level(), difficulty, MobSpawnType.EVENT, null);
        Constellation sign = params.has("sign")
                ? Constellation.valueOf(params.get("sign").getAsString().toUpperCase(java.util.Locale.ROOT))
                : context.prediction().plan().sign();
        // overrideOffers у жителей пустой метод, поэтому товары меняются в самом списке.
        var offers = trader.getOffers();
        offers.clear();
        offers.addAll(SignTrades.create(context.player(), sign, Parameters.flag(params, "discount", false)));
        int lifetime = StarClockConfig.TRADER_LIFETIME.get() * 20;
        trader.setDespawnDelay(lifetime);
        trader.getPersistentData().putLong("StarclockExpires", context.now() + lifetime);
        // Отмена спавна другим модом окончательна: он мог поставить своего торговца, и повтор плодил бы новых.
        if (!context.level().addFreshEntity(trader)) return true;
        context.data().addSpawn(new TimedSpawn(trader.getUUID(), context.level().dimension().location().toString(),
                context.now() + lifetime));
        return true;
    }
}
