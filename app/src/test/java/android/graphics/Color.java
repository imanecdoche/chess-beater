package android.graphics;

public class Color {
    public static final int WHITE = 0xFFFFFFFF;
    public static int red(int color) { return (color >> 16) & 0xFF; }
    public static int green(int color) { return (color >> 8) & 0xFF; }
    public static int blue(int color) { return color & 0xFF; }
    public static int alpha(int color) { return (color >> 24) & 0xFF; }
    public static int rgb(int r, int g, int b) { return (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF); }
    public static int argb(int a, int r, int g, int b) { return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF); }

    public static void colorToHSV(int color, float[] hsv) {
        float r = red(color) / 255.0f;
        float g = green(color) / 255.0f;
        float b = blue(color) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h = 0f;
        if (delta != 0) {
            if (max == r) h = 60f * (((g - b) / delta) % 6f);
            else if (max == g) h = 60f * (((b - r) / delta) + 2f);
            else h = 60f * (((r - g) / delta) + 4f);
            if (h < 0) h += 360f;
        }
        hsv[0] = h;
        hsv[1] = (max == 0) ? 0 : (delta / max);
        hsv[2] = max;
    }

    public static int HSVToColor(int alpha, float[] hsv) {
        float h = hsv[0]; float s = hsv[1]; float v = hsv[2];
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = v - c;
        float r = 0, g = 0, b = 0;
        if (h < 60) { r = c; g = x; }
        else if (h < 120) { r = x; g = c; }
        else if (h < 180) { g = c; b = x; }
        else if (h < 240) { g = x; b = c; }
        else if (h < 300) { r = x; b = c; }
        else { r = c; b = x; }
        return argb(alpha, (int)((r + m) * 255), (int)((g + m) * 255), (int)((b + m) * 255));
    }
}
