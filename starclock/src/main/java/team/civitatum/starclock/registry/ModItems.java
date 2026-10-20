package team.civitatum.starclock.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import team.civitatum.starclock.CivitatumStarclock;
import team.civitatum.starclock.item.StarToolItem;
import team.civitatum.starclock.item.ToolKind;
import team.civitatum.starclock.item.ToolTier;
import team.civitatum.starclock.item.TowerKeyItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CivitatumStarclock.ID);
    public static final List<DeferredItem<StarToolItem>> TOOLS = registerTools();
    public static final DeferredItem<TowerKeyItem> TOWER_KEY = ITEMS.register("tower_key",
            () -> new TowerKeyItem(new Item.Properties().durability(5).rarity(Rarity.UNCOMMON)));

    private static List<DeferredItem<StarToolItem>> registerTools() {
        List<DeferredItem<StarToolItem>> tools = new ArrayList<>();
        for (ToolKind kind : ToolKind.values()) {
            for (ToolTier tier : ToolTier.values()) {
                tools.add(ITEMS.register(kind.id() + "_" + tier.strength(),
                        () -> new StarToolItem(kind, tier,
                                new Item.Properties().stacksTo(1).rarity(tier.rarity()))));
            }
        }
        return Collections.unmodifiableList(tools);
    }

    private ModItems() {}
}
