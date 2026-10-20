package team.civitatum.starclock.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import team.civitatum.starclock.CivitatumStarclock;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CivitatumStarclock.ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> STARCLOCK =
            TABS.register("starclock", () -> CreativeModeTab.builder()
                    .title(Component.translatable("tab.civitatum_starclock"))
                    .icon(() -> ModItems.TOOLS.get(0).get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        ModItems.TOOLS.forEach(item -> output.accept(item.get()));
                        output.accept(ModItems.TOWER_KEY.get());
                    }).build());

    private ModTabs() {}
}
