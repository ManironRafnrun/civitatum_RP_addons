package team.civitatum.starclock.prediction.handlers;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import team.civitatum.starclock.config.StarClockConfig;
import team.civitatum.starclock.data.TimedSpawn;
import team.civitatum.starclock.prediction.ActionContext;
import team.civitatum.starclock.prediction.Constellation;
import team.civitatum.starclock.prediction.Parameters;
import team.civitatum.starclock.prediction.WorldSafety;
import team.civitatum.starclock.trader.SignTrades;

public final class TraderHandler implements ActionHandler {
    @Override
    public boolean run(ActionContext context, JsonObject params) {
        var trader = EntityType.WANDERING_TRADER.create(context.level());
        if (trader == null) throw new IllegalStateException("Не удалось создать торговца");
        var angle = context.random().nextDouble() * Math.PI * 2;
        var wanted = context.player().position().add(Math.cos(angle) * 10, 0, Math.sin(angle) * 10);
        var position = WorldSafety.nearby(context.level(), trader, wanted, 4);
        if (position == null) return false;
        trader.moveTo(position.x, position.y, position.z, 0, 0);
        Constellation sign = params.has("sign")
                ? Constellation.valueOf(params.get("sign").getAsString().toUpperCase(java.util.Locale.ROOT))
                : context.prediction().plan().sign();
        trader.overrideOffers(SignTrades.create(context.player(), sign, Parameters.flag(params, "discount", false)));
        int lifetime = StarClockConfig.TRADER_LIFETIME.get() * 20;
        trader.setDespawnDelay(lifetime);
        trader.getPersistentData().putLong("StarclockExpires", context.now() + lifetime);
        if (!context.level().addFreshEntity(trader)) return false;
        context.data().addSpawn(new TimedSpawn(trader.getUUID(), context.level().dimension().location().toString(),
                context.now() + lifetime));
        return true;
    }
}
