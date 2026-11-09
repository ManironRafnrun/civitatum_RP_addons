package team.civitatum.charactercreation.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The fire dragonborn's heat speeds the furnace's own counters; they are package-private. */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceAccessor {
    @Accessor("litTime")
    int civitatum$getLitTime();

    @Accessor("litTime")
    void civitatum$setLitTime(int value);

    @Accessor("cookingProgress")
    int civitatum$getCookingProgress();

    @Accessor("cookingProgress")
    void civitatum$setCookingProgress(int value);

    @Accessor("cookingTotalTime")
    int civitatum$getCookingTotalTime();
}
