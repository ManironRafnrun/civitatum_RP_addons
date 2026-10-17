package team.civitatum.inventory.client;

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
    public record Ability(String id,Component name,ItemStack icon,List<Component> description,BooleanSupplier ready,Runnable activate){}
    private static final LinkedHashMap<String,Function<Player,List<Ability>>> providers=new LinkedHashMap<>();
    public static void register(String id,Function<Player,List<Ability>> provider){providers.put(id,provider);}
    public static List<Ability> available(Player player){
        var result=new ArrayList<Ability>();var race=RacesBridge.read(player);
        if(race!=null&&!race.ability().isBlank())result.add(new Ability("civitatum:racial_gift",Component.literal(race.ability()),new ItemStack(Items.NETHER_STAR),
            List.of(Component.literal(race.race()),Component.literal(race.ability()),CivitatumInventoryScreen.tr("cost",race.food()),
                race.remainingTicks()>0?CivitatumInventoryScreen.tr("remaining",(race.remainingTicks()+19)/20):CivitatumInventoryScreen.tr("ready")),
            ()->race.remainingTicks()==0&&player.getFoodData().getFoodLevel()>=race.food(),RacesBridge::activate));
        for(var provider:providers.values())result.addAll(provider.apply(player));
        return result;
    }
    public static Ability selected(Player player){
        var list=available(player);var id=InventoryPreferences.get().selectedAbility;
        return list.stream().filter(a->a.id().equals(id)).findFirst().orElse(list.isEmpty()?null:list.getFirst());
    }
    public static void select(String id){InventoryPreferences.get().selectedAbility=id;InventoryPreferences.get().save();}
    public static void cycle(Player player,int direction){
        var list=available(player);if(list.isEmpty())return;var selected=selected(player);int index=0;
        for(int i=0;i<list.size();i++)if(list.get(i).id().equals(selected.id()))index=i;
        select(list.get(Math.floorMod(index+direction,list.size())).id());
    }
    public static void activate(Player player){var a=selected(player);if(a!=null&&a.ready().getAsBoolean())a.activate().run();}
}
