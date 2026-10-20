package team.civitatum.starclock.prediction;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class PredictionRegistry extends SimpleJsonResourceReloadListener {
    private static PredictionCatalog catalog;

    public PredictionRegistry() {
        super(new Gson(), "predictions");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, JsonObject> definitions = new HashMap<>();
        resources.forEach((id, json) -> {
            if ("starclock".equals(id.getNamespace())) definitions.put(id.toString(), json.getAsJsonObject());
        });
        PredictionCatalog loaded = PredictionCatalog.parse(definitions);
        loaded.allPlans().forEach(plan -> plan.actions().forEach(ActionValidator::validate));
        catalog = loaded;
    }

    public static PredictionCatalog catalog() {
        if (catalog == null) throw new IllegalStateException("Таблицы Starclock ещё не загружены сервером");
        return catalog;
    }
}
