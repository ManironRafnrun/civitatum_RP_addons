package team.civitatum.charactercreation.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.charactercreation.client.FemaleGenderBridge;

/**
 * FGM's sync from the server writes every player's own FGM settings over the character's: the players looked as their
 * FGM screen says, not as their character. The character is written again at the next frame.
 */
@Pseudo
@Mixin(targets="com.wildfire.main.networking.AbstractSyncPacket",remap=false)
public abstract class FemaleGenderSyncMixin {
    @Inject(method="updatePlayerFromPacket",at=@At("RETURN"),require=1)
    private void civitatum$invalidate(CallbackInfo ci) {
        FemaleGenderBridge.invalidate();
    }
}
