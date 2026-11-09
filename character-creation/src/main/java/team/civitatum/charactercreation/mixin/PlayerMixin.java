package team.civitatum.charactercreation.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.charactercreation.power.Hooks;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float civitatum$exhaustion(float value) {
        return Hooks.exhaustion((Player) (Object) this, value);
    }

    /** Player overrides Entity.makeStuckInBlock; webs, berry bushes and powder snow all come through here. */
    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void civitatum$free(BlockState state, Vec3 speed, CallbackInfo ci) {
        if (Hooks.isFree((Player) (Object) this, state)) ci.cancel();
    }

    @ModifyVariable(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;"
        + "Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"),
        argsOnly = true)
    private FoodProperties civitatum$food(FoodProperties food, Level level, ItemStack stack) {
        return Hooks.food((Player) (Object) this, stack, food);
    }
}
