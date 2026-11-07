package team.civitatum.hud.client;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/**
 * Xaero's minimap in the frame of the inventory windows, square or round like the map; Xaero's own frame is switched
 * off by {@code MinimapFrameMixin}. The map's rectangle is taken from Xaero's own call that draws it, through the pose
 * of that call, so the frame fits the map whatever Xaero's scales and roundings are.
 */
public final class MinimapFrame {
    /**
     * The frame is drawn around the map, not over it: {@link MorrowindSkin#frame} paints 2 px (outline and gold) inside
     * its left and top edges and 2 px (outline and shadow) inside its right and bottom edges.
     */
    static final int OUTSET = 2;
    /** Float noise of the pose transform; a map edge closer than this to a whole pixel is on that pixel. */
    private static final float EPSILON = 1.0E-3F;
    /** The map of the current {@code renderMinimap} call, in GUI pixels: whole pixels it covers fully. */
    private static boolean recorded;
    private static boolean round;
    private static int left;
    private static int top;
    private static int right;
    private static int bottom;

    private MinimapFrame() {
    }

    /** Xaero is about to draw the map at {@code (x, y)}, {@code width} by {@code height}, under {@code pose}. */
    public static void mapDrawn(Matrix4f pose, float x, float y, float width, float height, boolean isRound) {
        recorded = true;
        round = isRound;
        left = innerStart(pose.m00() * x + pose.m10() * y + pose.m30());
        top = innerStart(pose.m01() * x + pose.m11() * y + pose.m31());
        right = innerEnd(pose.m00() * (x + width) + pose.m10() * (y + height) + pose.m30());
        bottom = innerEnd(pose.m01() * (x + width) + pose.m11() * (y + height) + pose.m31());
    }

    /** After Xaero drew the minimap: frames the map it recorded, if it drew one. */
    public static void draw(GuiGraphics graphics) {
        if (!recorded) return;
        recorded = false;
        int x = left - OUTSET;
        int y = top - OUTSET;
        int width = right - left + 2 * OUTSET;
        int height = bottom - top + 2 * OUTSET;
        MinimapBounds.set(x, y, width);
        if (round) {
            MorrowindSkin.circleFrame(graphics, x, y, width, false);
        } else {
            MorrowindSkin.frame(graphics, x, y, width, height, false);
        }
    }

    /**
     * First whole pixel at or after a map edge. With the frame starting there, a map edge between pixels is covered
     * by the frame by less than a pixel instead of leaving a gap.
     */
    static int innerStart(float edge) {
        return (int) Math.ceil(edge - EPSILON);
    }

    /** Last whole pixel boundary at or before a map edge. */
    static int innerEnd(float edge) {
        return (int) Math.floor(edge + EPSILON);
    }
}
