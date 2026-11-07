package team.civitatum.hud.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Original tiled textures; the stone is opaque, as the restyled vanilla and mod screens are. */
public final class MorrowindSkin {
    /** The lit gold of a frame, see-through: a slot under the cursor, here and in the restyled vanilla screens. */
    public static final int SLOT_HOVER = 0x60E2C18A;
    /** The same gold, opaque: the rim AE2 draws around the slot under the cursor. */
    public static final int SLOT_HOVER_EDGE = 0xFFE2C18A;
    /** The gold of frames, for the rims mods draw in their code (AE2's upgrade panel). */
    public static final int FRAME_GOLD = 0xFFB29A6B;
    private static final ResourceLocation STONE =
        ResourceLocation.fromNamespaceAndPath("civitatum_hud", "textures/gui/stone.png");
    private static final int TILE = 32;
    private static final int OUTLINE = 0xFF17130E, SHADOW = 0xFF54422C, GOLD = 0xFFB29A6B, LIT_GOLD = 0xFFE2C18A;

    public static void body(GuiGraphics g, int x, int y, int w, int h) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // The vertices GuiGraphics.blit would emit tile by tile, as one mesh and one draw call for the whole panel.
        RenderSystem.setShaderTexture(0, STONE);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Matrix4f pose = g.pose().last().pose();
        BufferBuilder tiles = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int dy = 0; dy < h; dy += TILE) {
            for (int dx = 0; dx < w; dx += TILE) {
                int tw = Math.min(TILE, w - dx), th = Math.min(TILE, h - dy);
                float x1 = x + dx, x2 = x + dx + tw, y1 = y + dy, y2 = y + dy + th;
                float u = (float) tw / TILE, v = (float) th / TILE;
                tiles.addVertex(pose, x1, y1, 0).setUv(0, 0);
                tiles.addVertex(pose, x1, y2, 0).setUv(0, v);
                tiles.addVertex(pose, x2, y2, 0).setUv(u, v);
                tiles.addVertex(pose, x2, y1, 0).setUv(u, 0);
            }
        }
        MeshData mesh = tiles.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh); // null: a panel without area
        g.flush();
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    public static void frame(GuiGraphics g, int x, int y, int w, int h, boolean lit) {
        int gold = lit ? LIT_GOLD : GOLD;
        GuiBatch.fills(g, () -> {
            g.fill(x, y, x + w, y + 1, OUTLINE);
            g.fill(x, y + h - 1, x + w, y + h, OUTLINE);
            g.fill(x, y, x + 1, y + h, OUTLINE);
            g.fill(x + w - 1, y, x + w, y + h, OUTLINE);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, gold);
            g.fill(x + 1, y + 1, x + 2, y + h - 1, gold);
            g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, SHADOW);
            g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, SHADOW);
        });
    }

    /**
     * {@link #frame} as a ring ({@link FrameRing}) inscribed in the square {@code size} wide.
     * Pixels of one colour in a row are drawn as one fill.
     */
    public static void circleFrame(GuiGraphics g, int x, int y, int size, boolean lit) {
        int[] colors = {0, OUTLINE, lit ? LIT_GOLD : GOLD, SHADOW};
        double radius = size / 2.0;
        GuiBatch.fills(g, () -> circleRows(g, x, y, size, colors, radius));
    }

    private static void circleRows(GuiGraphics g, int x, int y, int size, int[] colors, double radius) {
        for (int row = 0; row < size; row++) {
            int runStart = 0;
            int runPart = FrameRing.NONE;
            for (int column = 0; column <= size; column++) {
                int part = column < size
                    ? FrameRing.at(column + 0.5 - radius, row + 0.5 - radius, radius) : FrameRing.NONE;
                if (part != runPart) {
                    if (runPart != FrameRing.NONE) {
                        g.fill(x + runStart, y + row, x + column, y + row + 1, colors[runPart]);
                    }
                    runStart = column;
                    runPart = part;
                }
            }
        }
    }
}
