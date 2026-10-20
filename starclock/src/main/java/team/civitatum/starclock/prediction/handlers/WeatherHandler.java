package team.civitatum.starclock.prediction.handlers;

import com.google.gson.JsonObject;
import team.civitatum.starclock.prediction.ActionContext;
import team.civitatum.starclock.prediction.Parameters;

public final class WeatherHandler implements ActionHandler {
    @Override
    public boolean run(ActionContext context, JsonObject params) {
        if (!context.level().dimensionType().hasSkyLight()) return false;
        int duration = Parameters.integer(params, "seconds", 60) * 20;
        boolean rain = Parameters.flag(params, "rain", false);
        context.level().setWeatherParameters(rain ? 0 : duration, duration, rain,
                rain && Parameters.flag(params, "thunder", false));
        return true;
    }
}
