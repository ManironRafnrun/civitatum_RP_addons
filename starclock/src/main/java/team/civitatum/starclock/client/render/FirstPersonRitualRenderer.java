package team.civitatum.starclock.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import team.civitatum.starclock.client.RitualClientState;

public final class FirstPersonRitualRenderer {
    public static boolean render(AbstractClientPlayer player, InteractionHand hand, ItemStack stack,
                                 PoseStack pose, MultiBufferSource buffers, int light) {
        var state = RitualClientState.current();
        if (state == null || !RitualClientState.matches(player.getItemInHand(state.hand()))) return false;
        // Обе руки входят в одну модель, поэтому второй ванильный проход подавляется.
        if (hand != state.hand()) return true;
        if (!RitualClientState.matches(stack)) return false;
        if (!(GeoRenderProvider.of(stack).getGeoItemRenderer() instanceof StarToolRenderer renderer)) return false;
        boolean left = (hand == InteractionHand.MAIN_HAND ? player.getMainArm()
                : player.getMainArm().getOpposite()) == HumanoidArm.LEFT;
        renderer.setMirrored(left);
        pose.pushPose();
        pose.translate(0, -0.38, -0.78);
        pose.scale(left ? -0.9F : 0.9F, 0.9F, 0.9F);
        try {
            renderer.renderByItem(stack, left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        } finally {
            pose.popPose();
        }
        return true;
    }

    private FirstPersonRitualRenderer() {}
}
