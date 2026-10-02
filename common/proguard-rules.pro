# Common module ProGuard rules

# Keep all public API in common module
-keep class com.drywall.common.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Compose
-dontwarn androidx.compose.**

# Security
-keep class androidx.security.crypto.** { *; }

# Biometric
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# OkHttp
-keep class okhttp3.internal.platform.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# SQLCipher (defensive)
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**

# Production logging: strip debug and verbose logs
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
