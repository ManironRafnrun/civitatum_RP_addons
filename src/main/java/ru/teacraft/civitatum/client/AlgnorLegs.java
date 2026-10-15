package ru.teacraft.civitatum.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Thigh -> angled shank -> raised hock -> pastern -> split hoof, replacing the vanilla shins. */
public final class AlgnorLegs {
    private final ModelPart root;
    public AlgnorLegs() {
        var mesh=new MeshDefinition();var part=mesh.getRoot();
        var thigh=part.addOrReplaceChild("thigh",CubeListBuilder.create().addBox(-1.9f,0,-1.8f,3.8f,5,3.6f),PartPose.rotation(-.22f,0,0));
        var shank=thigh.addOrReplaceChild("shank",CubeListBuilder.create().addBox(-1.55f,0,-1.5f,3.1f,4.7f,3),PartPose.offsetAndRotation(0,4.6f,0,.65f,0,0));
        var pastern=shank.addOrReplaceChild("pastern",CubeListBuilder.create().addBox(-1.1f,0,-1,2.2f,3,2),PartPose.offsetAndRotation(0,4.2f,0,-.65f,0,0));
        pastern.addOrReplaceChild("hoof",CubeListBuilder.create().addBox(-2.05f,0,-2.7f,1.9f,1.8f,4.3f).addBox(.15f,0,-2.7f,1.9f,1.8f,4.3f),PartPose.offsetAndRotation(0,2.15f,0,.22f,0,0));
        root=LayerDefinition.create(mesh,32,32).bakeRoot();
    }
    public void render(PoseStack pose,VertexConsumer buffer,int light,ModelPart anchor,int color) {
        pose.pushPose();anchor.translateAndRotate(pose);
        var thigh=root.getChild("thigh"),shank=thigh.getChild("shank"),pastern=shank.getChild("pastern"),hoof=pastern.getChild("hoof");
        hoof.visible=false;root.render(pose,buffer,light,OverlayTexture.NO_OVERLAY,0xFF000000|color);hoof.visible=true;
        pose.pushPose();thigh.translateAndRotate(pose);shank.translateAndRotate(pose);pastern.translateAndRotate(pose);
        hoof.render(pose,buffer,light,OverlayTexture.NO_OVERLAY,0xFF34302B);pose.popPose();pose.popPose();
    }
}
