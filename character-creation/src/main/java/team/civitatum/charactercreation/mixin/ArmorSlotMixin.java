package team.civitatum.charactercreation.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.civitatum.charactercreation.RaceService;

@Mixin(targets="net.minecraft.world.inventory.ArmorSlot")
public abstract class ArmorSlotMixin {
    @Shadow @Final private LivingEntity owner;
    @Shadow @Final private EquipmentSlot slot;
    @Inject(method="mayPlace",at=@At("HEAD"),cancellable=true)
    private void civitatum$boots(ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if(slot==EquipmentSlot.FEET && owner instanceof Player p && RaceService.forbidsBoots(p)) ci.setReturnValue(false);
    }
}
