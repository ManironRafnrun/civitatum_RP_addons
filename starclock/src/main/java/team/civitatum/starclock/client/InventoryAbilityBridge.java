package team.civitatum.starclock.client;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import team.civitatum.starclock.CivitatumStarclock;
import team.civitatum.starclock.registry.ModItems;

@EventBusSubscriber(modid = CivitatumStarclock.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class InventoryAbilityBridge {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("civitatum_inventory")) Linked.register();
        });
    }

    private static final class Linked {
        static void register() {
            team.civitatum.inventory.client.AbilitySelection.register(CivitatumStarclock.ID, player -> {
                int charges = TowerKeyClient.charges();
                if (charges <= 0) return List.of();
                return List.of(new team.civitatum.inventory.client.AbilitySelection.Ability(
                        "civitatum_starclock:tower_key",
                        Component.translatable("ability.civitatum_starclock.tower_key"),
                        new ItemStack(ModItems.TOWER_KEY.get(), charges),
                        List.of(Component.translatable("ability.civitatum_starclock.tower_key.description"),
                                Component.translatable("ability.civitatum_starclock.tower_key.remaining", charges)),
                        () -> TowerKeyClient.charges() > 0 && !RitualClientState.isActive(),
                        TowerKeyClient::cast));
            });
        }
    }

    private InventoryAbilityBridge() {}
}
