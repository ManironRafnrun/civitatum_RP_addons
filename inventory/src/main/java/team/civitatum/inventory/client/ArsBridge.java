package team.civitatum.inventory.client;

import java.lang.reflect.Method;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.mojang.logging.LogUtils;

/** Ars Nouveau 5.x: reads the client-synchronised attachment through the published mana API. */
public final class ArsBridge {
    public record Mana(double current,double max,double reserved){}
    private static Method getMana,current,max,reserve;
    private static boolean failed;
    public static Mana mana(Player player){
        if(failed||!CompatBridge.loaded("ars_nouveau"))return null;
        try{
            if(getMana==null){
                getMana=Class.forName("com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry").getMethod("getMana",LivingEntity.class);
                Class<?> cap=Class.forName("com.hollingsworth.arsnouveau.common.capability.ManaCap");
                current=cap.getMethod("getCurrentMana");max=cap.getMethod("getMaxMana");reserve=cap.getMethod("getReserve");
            }
            Object cap=getMana.invoke(null,player);if(cap==null)return null;
            return new Mana(((Number)current.invoke(cap)).doubleValue(),((Number)max.invoke(cap)).doubleValue(),((Number)reserve.invoke(cap)).doubleValue());
        }catch(ReflectiveOperationException|LinkageError ex){failed=true;LogUtils.getLogger().warn("Civitatum Inventory: Ars mana adapter unavailable",ex);return null;}
    }
}
