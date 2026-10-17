package team.civitatum.charactercreation.mixin;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import team.civitatum.charactercreation.Civitatum;

@Mixin(Villager.class)
public abstract class VillagerMixin {

    @Redirect(method="rewardTradeXp",at=@At(value="NEW",target="net/minecraft/world/entity/ExperienceOrb"))
    private ExperienceOrb civitatum$trade(Level level,double x,double y,double z,int xp) {
        var villager=(Villager)(Object)this;
        var player=villager.getTradingPlayer();
        if(player!=null && player.getData(Civitatum.STATE).character().selected
            && player.getData(Civitatum.STATE).character().race.equals("city_algnor")) xp=Math.round(xp*1.2f);
        return new ExperienceOrb(level,x,y,z,xp);
    }

}
