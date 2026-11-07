package team.civitatum.hud.client;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

/** Client extension point; providers must activate through their own server-validated protocol. */
public final class AbilitySelection {
    public static final String RACIAL_GIFT = "civitatum:racial_gift";
    /** Ticks are shown as whole seconds, rounded up: 1 tick left still reads as 1 second. */
    private static final int TICKS_PER_SECOND = 20;

    public record Ability(String id, Component name, ItemStack icon, List<Component> description, BooleanSupplier ready,
                          Runnable activate) {}

    private static final LinkedHashMap<String, Function<Player, List<Ability>>> providers = new LinkedHashMap<>();

    public static void register(String id, Function<Player, List<Ability>> provider) {
        providers.put(id, provider);
    }

    public static List<Ability> available(Player player) {
        var result = new ArrayList<Ability>();
        var race = RacesBridge.read(player);
        if (race != null && !race.ability().isBlank()) {
            var description = List.of(Component.literal(race.race()), Component.literal(race.ability()),
                CivitatumInventoryScreen.tr("cost", race.food()), remaining(race.remainingTicks()));
            result.add(new Ability(RACIAL_GIFT, Component.literal(race.ability()), new ItemStack(Items.NETHER_STAR),
                description,
                () -> race.remainingTicks() == 0 && player.getFoodData().getFoodLevel() >= race.food(),
                RacesBridge::activate));
        }
        for (var provider : providers.values()) result.addAll(provider.apply(player));
        return result;
    }

    static Component remaining(long ticks) {
        return ticks > 0
            ? CivitatumInventoryScreen.tr("remaining", (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND)
            : CivitatumInventoryScreen.tr("ready");
    }

    public static Ability selected(Player player) {
        return selected(available(player));
    }

    private static Ability selected(List<Ability> list) {
        var id = InventoryPreferences.get().selectedAbility;
        for (var ability : list) if (ability.id().equals(id)) return ability;
        return list.isEmpty() ? null : list.getFirst();
    }

    public static void select(String id) {
        InventoryPreferences.get().selectedAbility = id;
        InventoryPreferences.get().save();
    }

    public static void cycle(Player player, int direction) {
        var list = available(player);
        if (list.isEmpty()) return;
        var selected = selected(list);
        int index = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(selected.id())) index = i;
        select(list.get(Math.floorMod(index + direction, list.size())).id());
    }

    public static void activate(Player player) {
        var a = selected(player);
        if (a != null && a.ready().getAsBoolean()) a.activate().run();
    }
}
