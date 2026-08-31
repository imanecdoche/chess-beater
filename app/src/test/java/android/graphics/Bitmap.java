package android.graphics;

public class Bitmap {
    public enum Config { ARGB_8888, RGB_565 }
    private final int width;
    private final int height;
    private final int[] pixels;
    private boolean recycled = false;
    private final Config config;

    private Bitmap(int w, int h, Config c) {
        this.width = w;
        this.height = h;
        this.config = c;
        this.pixels = new int[w * h];
    }

    public static Bitmap createBitmap(int w, int h, Config c) {
        return new Bitmap(w, h, c);
    }

    public static Bitmap createBitmap(Bitmap source, int x, int y, int width, int height) {
        Bitmap crop = new Bitmap(width, height, source.config);
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                crop.setPixel(col, row, source.getPixel(x + col, y + row));
            }
        }
        return crop;
    }

    public static Bitmap createScaledBitmap(Bitmap src, int dstWidth, int dstHeight, boolean filter) {
        return new Bitmap(dstWidth, dstHeight, src.config);
    }


    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Config getConfig() { return config; }
    public boolean isRecycled() { return recycled; }
    public void recycle() { recycled = true; }

    public void setPixel(int x, int y, int color) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            pixels[y * width + x] = color;
        }
    }

    public int getPixel(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            return pixels[y * width + x];
        }
        return 0;
    }

    public void getPixels(int[] out, int offset, int stride, int x, int y, int w, int h) {
        System.arraycopy(pixels, 0, out, offset, Math.min(w * h, pixels.length));
    }

    public void setPixels(int[] src, int offset, int stride, int x, int y, int w, int h) {
        System.arraycopy(src, offset, pixels, 0, Math.min(w * h, pixels.length));
    }

    public Bitmap copy(Config c, boolean isMutable) {
        Bitmap copy = new Bitmap(width, height, c);
        System.arraycopy(pixels, 0, copy.pixels, 0, pixels.length);
        return copy;
    }
}
