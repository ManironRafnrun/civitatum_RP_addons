package team.civitatum.starclock.prediction;

import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import team.civitatum.starclock.data.PendingPrediction;
import team.civitatum.starclock.data.StarClockData;

public record ActionContext(ServerPlayer player, StarClockData data, PendingPrediction prediction, long now) {
    public ServerLevel level() {
        return player.serverLevel();
    }

    public int tier() {
        return prediction.tier();
    }

    public Random random() {
        return new Random(prediction.id().getMostSignificantBits() ^ prediction.id().getLeastSignificantBits()
                ^ (long) prediction.nextAction() * 65537L ^ prediction.attempts());
    }
}
