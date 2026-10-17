package team.civitatum.coupleinteractions.client;

import java.util.UUID;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import team.civitatum.coupleinteractions.CarryHooks;
import team.civitatum.coupleinteractions.core.Interaction;
import team.civitatum.coupleinteractions.core.RadialLayout;
import team.civitatum.coupleinteractions.network.CoupleNetwork;

public final class RadialScreen extends Screen {
    private final UUID target;
    private final Component targetName;
    private final boolean release;
    private int selected = -1;
    private boolean committed;
    private int frames;
    public RadialScreen(Player target) {
        this(target.getUUID(), target.getDisplayName(), CarryHooks.occupied(net.minecraft.client.Minecraft.getInstance().player));
    }
    public RadialScreen(UUID target, Component name, boolean release) {
        super(Component.translatable("ui.civitatum_couple.title"));
        this.target = target; this.targetName = name; this.release = release;
    }
    @Override protected void init() {
        if (frames == 0) GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), minecraft.getWindow().getScreenWidth() / 2.0, minecraft.getWindow().getScreenHeight() / 2.0);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        if (minecraft.player == null || !minecraft.player.isAlive()) { onClose(); return; }
        // Screens reset KeyMapping state on opening. Poll the physical rebound key instead.
        if (frames > 1 && !ClientEvents.held(CoupleClient.MENU)) {
            selected = selectionAt(minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth(),
                    minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight());
            commit();
        }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        frames++;
        int cx = width / 2, cy = height / 2;
        float outer = outerRadius();
        float inner = outer * 0.43F;
        selected = RadialLayout.select(mouseX - cx, mouseY - cy, inner, outer, 6);
        g.fill(0, 0, width, height, 0x65080A0E);
        g.drawCenteredString(font, title, cx, (int)(cy - outer - 23), 0xFFEAE3DA);
        g.flush();
        for (int i = 0; i < 6; i++) ring(g, cx, cy, inner, outer, i, 6, i == selected ? 0xF05B3945 : 0xED20232B);
        g.flush();
        for (int i = 0; i < 6; i++) {
            double angle = RadialLayout.centerAngle(i, 6);
            int x = (int)(cx + Math.cos(angle) * outer * 0.72), y = (int)(cy + Math.sin(angle) * outer * 0.72);
            int color = i == selected ? 0xFFFFD6C9 : 0xFFDAD5CC;
            String label=switch(i){case 0->release?"action.civitatum_couple.release":"action.civitatum_couple.carry";case 1->"action.civitatum_couple.kiss";case 2->"action.civitatum_couple.hug";case 3->"action.civitatum_couple.rest";case 4->CoupleClientConfig.trusted(target)?"ui.civitatum_couple.untrust":"ui.civitatum_couple.trust";default->"ui.civitatum_couple.settings";};
            Component text=Component.translatable(label);
            var lines = font.split(text, Math.max(70, (int)(outer * 0.92)));
            int lineY = y - lines.size() * 5;
            for (var line : lines) { g.drawString(font, line, x - font.width(line) / 2, lineY, color, false); lineY += 10; }
        }
        String name = font.plainSubstrByWidth(targetName.getString(), (int)(inner * 1.8));
        g.drawCenteredString(font, name, cx, cy - 10, 0xFFF3EDE4);
        g.drawCenteredString(font, Component.translatable("ui.civitatum_couple.cancel"), cx, cy + 5, 0xFF9198A4);
        Component hint = Component.translatable("ui.civitatum_couple.release_key", CoupleClient.MENU.getTranslatedKeyMessage());
        int hintY = (int)(cy + outer + 12), hintWidth = font.width(hint);
        g.fill(cx-hintWidth/2-4,hintY-3,cx+hintWidth/2+4,hintY+11,0xED16181F);
        g.drawCenteredString(font, hint, cx, hintY, 0xFFC9C2B8);
        g.flush();
        g.pose().popPose();
    }
    private void commit() {
        if (committed) return;
        committed = true;
        minecraft.setScreen(null);
        if(selected==5){minecraft.setScreen(new SettingsScreen(null,target));return;}
        if(selected==4){CoupleClientConfig.toggleTrusted(target,targetName.getString());return;}
        Interaction action=switch(selected){case 0->release?Interaction.RELEASE:Interaction.CARRY;case 1->Interaction.KISS;case 2->Interaction.HUG;case 3->Interaction.REST;default->null;};
        if(action!=null)PacketDistributor.sendToServer(new CoupleNetwork.Action(target,action.ordinal()));
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selected = selectionAt(x, y);
            commit(); return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { onClose(); return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public void onClose() { committed = true; minecraft.setScreen(null); }

    private float outerRadius() { return Math.max(56, Math.min(106, Math.min(width / 2F - 14, height / 2F - 58))); }
    private int selectionAt(double x, double y) {
        float outer = outerRadius();
        return RadialLayout.select(x - width / 2.0, y - height / 2.0, outer * 0.43, outer, 6);
    }

    private static void ring(GuiGraphics graphics, float cx, float cy, float inner, float outer, int segment, int count, int color) {
        Matrix4f matrix = graphics.pose().last().pose();
        VertexConsumer b = graphics.bufferSource().getBuffer(RenderType.gui());
        double center = RadialLayout.centerAngle(segment, count), span = Math.PI * 2 / count;
        double start = center - span / 2 + 0.035, end = center + span / 2 - 0.035;
        int steps = 32;
        for (int i = 0; i < steps; i++) {
            double a = start + (end - start) * i / steps, n = start + (end - start) * (i + 1) / steps;
            // GUI projection reverses winding: emit the same front-face order as GuiGraphics.fill.
            vertex(b,matrix,cx,cy,inner,a,color); vertex(b,matrix,cx,cy,inner,n,color);
            vertex(b,matrix,cx,cy,outer,n,color); vertex(b,matrix,cx,cy,outer,a,color);
        }
    }
    private static void vertex(VertexConsumer b, Matrix4f m, float cx, float cy, float radius, double angle, int color) {
        b.addVertex(m, cx + (float)Math.cos(angle) * radius, cy + (float)Math.sin(angle) * radius, 0).setColor(color);
    }
}
