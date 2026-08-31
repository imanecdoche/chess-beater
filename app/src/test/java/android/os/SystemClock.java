package android.os;

public class SystemClock {
    public static long uptimeMillis() { return System.currentTimeMillis(); }
    public static long elapsedRealtime() { return System.currentTimeMillis(); }
}
