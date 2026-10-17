package team.civitatum.inventory.mixin;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Only the client-side presentation coordinates change; slot IDs and storage do not. */
@Mixin(Slot.class)
public interface SlotPositionAccessor {
    @Mutable @Accessor("x") void civitatumInventory$setX(int x);
    @Mutable @Accessor("y") void civitatumInventory$setY(int y);
}
