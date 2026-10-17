package team.civitatum.menu;

public record BackgroundGeometry(int x, int y, int width, int height) {
    public static BackgroundGeometry fit(int viewWidth, int viewHeight, int imageWidth, int imageHeight, double intensity, double cursorX, double cursorY) {
        double panX = (double)viewWidth * intensity;
        double panY = (double)viewHeight * intensity;
        double scale = Math.max(((double)viewWidth + 2.0 * panX) / (double)imageWidth, ((double)viewHeight + 2.0 * panY) / (double)imageHeight);
        int w = (int)Math.ceil((double)imageWidth * scale) + 2;
        int h = (int)Math.ceil((double)imageHeight * scale) + 2;
        int x = (int)Math.floor((double)(viewWidth - w) / 2.0 - clamp(cursorX) * panX);
        int y = (int)Math.floor((double)(viewHeight - h) / 2.0 - clamp(cursorY) * panY);
        return new BackgroundGeometry(x, y, w, h);
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }
}
