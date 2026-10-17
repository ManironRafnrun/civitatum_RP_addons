package team.civitatum.charactercreation.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.RaceCatalog;
import team.civitatum.charactercreation.RaceDefinition;

/**
 * Racial geometry and skinned body details, attached to the animated player skeleton.
 */
public final class CharacterLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public static final ModelLayerLocation MODEL = new ModelLayerLocation(Civitatum.res("body_details"), "main");
    private static final net.minecraft.resources.ResourceLocation TEXTURE = Civitatum.res("textures/entity/details.png");
    private final ModelPart root;
    private final AlgnorLegs leftAlgnorLeg = new AlgnorLegs(true), rightAlgnorLeg = new AlgnorLegs(false);
    private final ModelPart bust = createBustModel().bakeRoot();

    public CharacterLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ModelPart root) {
        super(parent);
        this.root = root;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p,
                       float walk, float amount, float partial, float age, float yaw, float pitch) {
        var c = ClientCharacters.get(p);
        if (!c.selected || p.isInvisible() || p.isSpectator()) return;
        RaceDefinition r = RaceCatalog.get(c.race);
        if (r == null) return;
        if (!ClientCharacters.isPreview(p)) FemaleGenderBridge.apply(p.getUUID(), c, false);
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        boolean helmet = !p.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        if (!helmet) {
            pair(pose, buffer, light, getParentModel().head, "ears" + c.ears, c.ears != 0, r.color("skin", c.skin), 1, c.earPitch, c.earYaw, c.earRoll, c.earOffset, false);
            if (c.hairAsset.isEmpty() && c.hairStyle != 0 && r.hairAllowed)
                attached(pose, buffer, light, getParentModel().head, "hair" + c.hairStyle, SkinTemplates.hairColor(c));
            if (r.id.equals("orc")) attached(pose, buffer, light, getParentModel().head, "tusks", 0xEADBB7);
            if (r.id.equals("firbolg"))
                attached(pose, buffer, light, getParentModel().head, "nose", r.color("skin", c.skin));
            if (r.dragon()) attached(pose, buffer, light, getParentModel().head, "muzzle", r.color("skin", c.skin));

        }
        if (c.horns != 0)
            pair(pose, buffer, light, getParentModel().head, "horns" + c.horns, true, r.color("horn", c.hornColor), c.hornSize,
                    c.hornPitch, c.hornYaw, c.hornRoll, c.hornOffset, c.hornOnForehead);
        if (c.tail != 0) {
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            var tail = root.getChild("tail" + c.tail);
            tail.yRot = (float) Math.sin(age * .09f) * .2f;
            tail.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, 0xFF000000 | r.color("skin", c.skin));
            pose.popPose();
        }
        if (r.algnor()) {
            leftAlgnorLeg.render(pose, buffers, light, getParentModel().leftLeg, r.color("skin", c.skin), p.getSkin().texture());
            rightAlgnorLeg.render(pose, buffers, light, getParentModel().rightLeg, r.color("skin", c.skin), p.getSkin().texture());
        } else if (r.id.equals("hobbit")) {
            attached(pose, buffer, light, getParentModel().leftLeg, "foot", r.color("skin", c.skin));
            attached(pose, buffer, light, getParentModel().rightLeg, "foot", r.color("skin", c.skin));
        }
        var glow = SkinTemplates.glow(p.getSkin().texture());
        if (glow != null)
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(glow)), 0xF000F0, OverlayTexture.NO_OVERLAY);
        if (c.female() && c.bust > 0 && !r.id.equals("forged") && (!FemaleGenderBridge.available() || ClientCharacters.isPreview(p))
                && (p.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || c.showInArmor)) {
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            float jiggle = c.breastPhysics && (p.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || c.armorPhysics)
                    ? (float) Math.sin(walk * 1.7f) * amount * c.bounce * c.floppiness * .08f : 0;
            pose.translate(0, c.bustY / 16f + jiggle, (-2.01f + c.bustZ) / 16f);
            pose.scale(1, 1, c.bust * 1.4f);
            var skinBuffer = buffers.getBuffer(RenderType.entityCutoutNoCull(p.getSkin().texture()));
            for (int sign : new int[]{-1, 1}) {
                pose.pushPose();
                pose.translate(sign * (c.bustX / 32f + (c.uniboob ? 0 : c.cleavage)), 0, 0);
                String side = sign < 0 ? "left" : "right";
                bust.getChild(side).render(pose, skinBuffer, light, OverlayTexture.NO_OVERLAY);
                bust.getChild(side + "_overlay").render(pose, skinBuffer, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            pose.popPose();
        }
    }

    private void attached(PoseStack pose, VertexConsumer b, int light, ModelPart anchor, String part, int color) {
        pose.pushPose();
        anchor.translateAndRotate(pose);
        root.getChild(part).render(pose, b, light, OverlayTexture.NO_OVERLAY, 0xFF000000 | color);
        pose.popPose();
    }

    private void pair(PoseStack pose, VertexConsumer b, int light, ModelPart anchor, String part, boolean show, int color, float size,
                      float pitch, float yaw, float roll, float offset, boolean forehead) {
        if (!show) return;
        pose.pushPose();
        anchor.translateAndRotate(pose);
        var group = root.getChild(part);
        for (int sign : new int[]{-1, 1}) {
            var node = group.getChild(sign < 0 ? "left" : "right");
            pose.pushPose();
            pose.translate(node.x / 16f, (node.y + offset + (forehead ? 2.8f : 0)) / 16f, (node.z + (forehead ? -4 : 0)) / 16f);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw * sign));
            pose.mulPose(Axis.ZP.rotationDegrees(roll * sign));
            pose.scale(size, size, size);
            pose.translate(-node.x / 16f, -node.y / 16f, -node.z / 16f);
            node.render(pose, b, light, OverlayTexture.NO_OVERLAY, 0xFF000000 | color);
            pose.popPose();
        }
        pose.popPose();
    }

    private static LayerDefinition createBustModel() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        for (int side = 0; side < 2; side++) {
            String name = side == 0 ? "left" : "right";
            float x = side == 0 ? -4 : 0;
            root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(17 + side * 4, 17).addBox(x, 2, -3.4f, 4, 4, 3.4f), PartPose.ZERO);
            root.addOrReplaceChild(name + "_overlay", CubeListBuilder.create().texOffs(17 + side * 4, 33)
                    .addBox(x, 2, -3.4f, 4, 4, 3.4f, new CubeDeformation(.035f)), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static CubeListBuilder cube() {
        return CubeListBuilder.create().texOffs(0, 0);
    }

    private static void box(PartDefinition root, String name, float x, float y, float z, float w, float h, float d) {
        root.addOrReplaceChild(name, cube().addBox(x, y, z, w, h, d), PartPose.ZERO);
    }

    public static LayerDefinition createModel() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        for (int style = 1; style <= 7; style++) {
            var ears = root.addOrReplaceChild("ears" + style, cube(), PartPose.ZERO);
            for (int sign : new int[]{-1, 1}) {
                float height = switch (style) {
                    case 3, 6 -> 7;
                    case 2, 4, 5 -> 5;
                    default -> 4;
                };
                float width = style == 5 ? 2 : style == 2 ? 1.6f : 1.1f;
                var build = cube().addBox(-width / 2, -height, -.65f, width, height, 1.3f)
                        .addBox(-width / 4, -height - 1, -.4f, width / 2, 1, .8f);
                float roll = switch (style) {
                    case 3, 6 -> sign * 1.1f;
                    case 2 -> sign * .9f;
                    case 4 -> sign * .75f;
                    case 5 -> sign * 1.8f;
                    case 7 -> sign * .8f;
                    default -> sign * .6f;
                };
                ears.addOrReplaceChild(sign < 0 ? "left" : "right", build, PartPose.offsetAndRotation(sign * 4, -3.6f, 0, 0, style == 4 ? sign * .55f : 0, roll));
            }
        }
        for (int style = 1; style <= 10; style++) {
            var horns = root.addOrReplaceChild("horns" + style, cube(), PartPose.ZERO);
            for (int sign : new int[]{-1, 1}) {
                var build = cube();
                boolean hidden = style == 8 && sign < 0;
                float len = style == 10 ? 1 : style == 9 ? 2.5f : style == 7 && sign < 0 ? 2 : style >= 5 ? 5.5f : 4;
                if (!hidden) {
                    build.addBox(-.8f, -len, -.8f, 1.6f, len, 1.6f);
                    if (style != 10) build.addBox(-.45f, -len - 1.4f, -.45f, .9f, 1.4f, .9f);
                    if (style == 5 || style == 6)
                        build.addBox(sign < 0 ? -3 : -.3f, -len + 1, -.5f, 3.3f, 1, 1).addBox(sign < 0 ? -3 : -.3f, -len - 1, -.4f, 1, 2, .8f);
                }
                float pitch = style == 2 ? -.65f : style == 3 ? -.9f : 0;
                var node = horns.addOrReplaceChild(sign < 0 ? "left" : "right", build, PartPose.offsetAndRotation(sign * 2.4f, -7.8f, 0, pitch, 0, style == 4 ? sign * .6f : sign * .15f));
                if (style == 2 || style == 6)
                    node.addOrReplaceChild("tip", cube().addBox(-.5f, -2.5f, -.5f, 1, 2.5f, 1), PartPose.offsetAndRotation(0, -len, 0, -.7f, 0, 0));
            }
        }
        box(root, "hair1", -4.2f, -8.3f, -4.2f, 8.4f, 1.5f, 8.4f);
        root.addOrReplaceChild("hair2", cube().addBox(-4.2f, -8.3f, -4.2f, 8.4f, 1.6f, 8.4f).addBox(-4.2f, -6.7f, 2.7f, 8.4f, 7, 1.6f), PartPose.ZERO);
        root.addOrReplaceChild("hair3", cube().addBox(-4.2f, -8.3f, -4.2f, 8.4f, 1.4f, 8.4f).addBox(-1.3f, -5.5f, 4, 2.6f, 7, 2), PartPose.ZERO);
        box(root, "hair4", -1, -10, -3, 2, 3, 6);
        root.addOrReplaceChild("tusks", cube().addBox(-2.8f, -1.8f, -4.6f, 1, 2.4f, 1).addBox(1.8f, -1.8f, -4.6f, 1, 2.4f, 1), PartPose.ZERO);
        box(root, "nose", -1, -4, -5.2f, 2, 2.5f, 1.3f);
        box(root, "muzzle", -2.5f, -2.8f, -5.5f, 5, 2.8f, 1.7f);
        root.addOrReplaceChild("tail1", cube().addBox(-1.2f, 8, 1.5f, 2.4f, 4, 2.8f), PartPose.rotation(.3f, 0, 0));
        root.addOrReplaceChild("tail2", cube().addBox(-.8f, 8, 1.5f, 1.6f, 7, 1.8f), PartPose.rotation(.35f, 0, 0));
        root.addOrReplaceChild("tail3", cube().addBox(-1.5f, 8, 1.5f, 3, 3, 3.5f), PartPose.rotation(.2f, 0, 0));
        box(root, "foot", -2.7f, 9, -3.8f, 5.4f, 3.1f, 6.2f);
        return LayerDefinition.create(mesh, 32, 32);
    }
}
