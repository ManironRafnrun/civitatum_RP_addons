package team.civitatum.starclock.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.RenderUtil;
import team.civitatum.starclock.item.StarToolItem;

public final class StarToolRenderer extends GeoItemRenderer<StarToolItem> {
    private boolean mirrored;

    public StarToolRenderer() {
        super(new StarToolModel());
    }

    public void setMirrored(boolean value) {
        mirrored = value;
    }

    @Override
    public long getInstanceId(StarToolItem item) {
        // Рендер обслуживает только один локальный обряд. Кэш не растёт с числом обрядов.
        return 0;
    }

    @Override
    public void preRender(PoseStack pose, StarToolItem item, BakedGeoModel model, MultiBufferSource buffers,
                          VertexConsumer buffer, boolean rerender, float partialTick, int light, int overlay, int colour) {
        super.preRender(pose, item, model, buffers, buffer, rerender, partialTick, light, overlay, colour);
        if (!rerender) pose.translate(-0.5, -0.51, -0.5);
    }

    @Override
    public void renderRecursively(PoseStack pose, StarToolItem item, GeoBone bone, RenderType renderType,
                                  MultiBufferSource buffers, VertexConsumer buffer, boolean rerender,
                                  float partialTick, int light, int overlay, int colour) {
        var player = Minecraft.getInstance().player;
        boolean right = bone.getName().equals("hand_right");
        boolean left = bone.getName().equals("hand_left");
        if ((right || left) && player != null && !player.isInvisible() && !rerender) {
            if (Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer) {
                pose.pushPose();
                RenderUtil.prepMatrixForBone(pose, bone);
                RenderUtil.translateToPivotPoint(pose, bone);
                // Нормальная матрица остаётся правильной и при левой основной руке.
                // Кисть ванильной руки находится в 12 пикселях от плеча.
                pose.mulPose(Axis.ZP.rotationDegrees(180));
                pose.translate(right ? 0.375 : -0.375, -0.75, 0);
                if (mirrored) pose.scale(-1, 1, 1);
                boolean actualRight = right != mirrored;
                if (actualRight) renderer.renderRightHand(pose, buffers, light, player);
                else renderer.renderLeftHand(pose, buffers, light, player);
                pose.popPose();
            }
        }
        super.renderRecursively(pose, item, bone, renderType, buffers, buffer, rerender,
                partialTick, light, overlay, colour);
    }
}
