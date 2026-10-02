# Jsoup
-keep public class org.jsoup.** { public *; }
-dontwarn org.jsoup.**

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature, *Annotation*
-dontwarn com.google.gson.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Keygen Data — mantener para Gson serialization
-keep class com.drywall.keygen.data.** { *; }

# Keygen Security — preservar solo lo necesario
-keep class com.drywall.keygen.security.KeyGenSecurity {
    public *;
}

# SQLCipher
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**
-keepclasseswithmembernames class * {
    native <methods>;
}

# AndroidX Security Crypto
-keep class androidx.security.crypto.** { *; }

# OkHttp
-keep class okhttp3.internal.platform.** { *; }

# LicenseInfo necesario para Gson en keygen
-keep class com.drywall.keygen.LicenseInfo { *; }
-keep class com.drywall.keygen.data.IssuedLicense { *; }

# Biometric
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# --- Optimization Rules ---

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Metadata
-keep class kotlin.Metadata { *; }

# Production logging: strip ALL log levels for security
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Remove all println/print statements
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# Obfuscate security-related classes
-renamesourcefileattribute SourceFile
-repackageclasses "a"
-allowaccessmodification
-optimizationpasses 3
