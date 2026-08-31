# ProGuard / R8 Optimization & Obfuscation Rules for Chess Beater
# Aligned with PRD Section 3, 7.1, & 7.2

# 1. Native C++ & JNI Bridge Keep Rules
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.chessbeater.engine.StockfishNativeBridge {
    public *;
    native <methods>;
}

-keep class com.chessbeater.engine.models.** { *; }

# 2. OpenCV Android SDK Keep Rules
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# 3. TensorFlow Lite & LiteRT Hardware Delegates (NNAPI, GPU)
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.nnapi.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.**

# 4. Vision Models & Enums
-keep class com.chessbeater.vision.models.** { *; }
-keepclassmembers enum com.chessbeater.vision.models.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 5. Jetpack DataStore Preferences & Coroutines
-keep class androidx.datastore.preferences.** { *; }
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# 6. Compose & Android Support
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# 7. UI, Activities, Services, & ViewModels
-keep class com.chessbeater.ui.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}
-keep class com.chessbeater.MainActivity
-keep class com.chessbeater.capture.ScreenCaptureService
-keep class com.chessbeater.overlay.OverlayService

