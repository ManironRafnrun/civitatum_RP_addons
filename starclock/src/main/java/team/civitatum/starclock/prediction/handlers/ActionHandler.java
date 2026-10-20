package team.civitatum.starclock.prediction.handlers;

import com.google.gson.JsonObject;
import team.civitatum.starclock.prediction.ActionContext;

@FunctionalInterface
public interface ActionHandler {
    boolean run(ActionContext context, JsonObject params);
}
