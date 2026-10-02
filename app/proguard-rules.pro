# --- Project Specific Rules ---

# Global
-keepattributes Signature, Annotation, InnerClasses, EnclosingMethod
-dontwarn com.google.errorprone.annotations.**
-keep class androidx.compose.material3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Hilt / Dagger
-keep class **_HiltModules* { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-dontwarn dagger.hilt.android.internal.**

# Gson
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_** { *; }

# Security - ONLY keep what Gson needs for serialization, obfuscate everything else
-keep class androidx.security.crypto.** { *; }

# LicenseInfo and ConsumedLicense must keep field names for Gson deserialization
-keepclassmembers class com.drywall.calculator.utils.security.LicenseInfo {
    <fields>;
}
-keepclassmembers class com.drywall.calculator.utils.security.ConsumedLicense {
    <fields>;
}

# Keep Gson-annotated fields
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Project Data
-keep class com.drywall.calculator.data.local.entity.** { *; }

# --- Optimization Rules ---

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Compose
-dontwarn androidx.compose.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Metadata
-keep class kotlin.Metadata { *; }

# SQLCipher
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**
-keepclasseswithmembernames class * {
    native <methods>;
}

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

# Obfuscate security-related classes (remove broad keep)
-renamesourcefileattribute SourceFile
-repackageclasses "a"
-allowaccessmodification
-optimizationpasses 3
