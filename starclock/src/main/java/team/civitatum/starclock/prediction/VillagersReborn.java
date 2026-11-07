package team.civitatum.starclock.prediction;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** Подбирает мобов Villagers Reborn вместо ванильных, которых этот мод заменяет. Без мода остаются ванильные. */
public final class VillagersReborn {
    private static final String NAMESPACE = "slimpatch";
    private static final Set<String> REPLACED = Set.of("pillager", "vindicator", "evoker", "wandering_trader");

    public static EntityType<?> resolve(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        boolean replaced = ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace()) && REPLACED.contains(id.getPath());
        if (!replaced) return type;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath(NAMESPACE, id.getPath()))
                .orElse(type);
    }

    private VillagersReborn() {}
}
