package team.civitatum.starclock.animation;

import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import team.civitatum.starclock.item.StarToolItem;

// Контроллер получает время от серверной фазы, а не от числа отрисованных кадров.
public final class RitualAnimationController extends AnimationController<StarToolItem> {
    private double ritualTick;

    public RitualAnimationController(StarToolItem item) {
        super(item, "ritual", 0, state -> {
            state.setAnimation(RawAnimation.begin().thenPlayAndHold("animation." + item.kind().id() + ".ritual"));
            return software.bernie.geckolib.animation.PlayState.CONTINUE;
        });
    }

    public void seek(double tick) {
        ritualTick = Math.clamp(tick, 0.0, 39.999);
    }

    @Override
    protected double adjustTick(double tick) {
        if (shouldResetTick || currentAnimation == null || animationState == State.TRANSITIONING) {
            return super.adjustTick(tick);
        }
        return ritualTick;
    }
}
