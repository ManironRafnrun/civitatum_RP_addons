package team.civitatum.starclock.mixin;

import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.civitatum.starclock.client.RitualClientState;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void starclock$blockScroll(long window, double horizontal, double vertical, CallbackInfo callback) {
        if (RitualClientState.isActive()) callback.cancel();
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void starclock$blockPress(long window, int button, int action, int modifiers, CallbackInfo callback) {
        if (RitualClientState.isActive() && action != GLFW.GLFW_RELEASE
                && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            callback.cancel();
        }
    }
}
