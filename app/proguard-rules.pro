# 保留 Compose 运行时与反射所需
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# Coil
-dontwarn okio.**
-dontwarn okhttp3.**
