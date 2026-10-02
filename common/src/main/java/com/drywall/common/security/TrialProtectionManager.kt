package com.drywall.common.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File
import java.security.MessageDigest

object TrialProtectionManager {
    private const val TAG = "TrialProtection"

    @Volatile
    private var initialized = false

    @Volatile
    private var cachedRootStatus: Boolean? = null

    @Volatile
    private var cachedTamperStatus: Boolean? = null
    
    private var contextRef: java.lang.ref.WeakReference<Context>? = null

    fun setContext(context: Context) {
        contextRef = java.lang.ref.WeakReference(context.applicationContext)
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        setContext(context)
        Thread {
            try {
                detectRoot(context)
                detectTamper(context)
            } catch (_: Exception) { }
        }.start()
    }

    fun refreshStorageIntegrity(context: Context) {
        cachedRootStatus = null
        cachedTamperStatus = null
    }

    fun onDeviceAdminDeactivated(context: Context) {
        // Could trigger license revocation in the future
    }

    fun onBootCompleted(context: Context) {
        // Re-check security status on boot
        cachedRootStatus = null
        cachedTamperStatus = null
    }

    fun isPermanentlyLocked(context: Context): Boolean = false
    fun getLockReason(context: Context): String = ""

    fun detectRoot(context: Context): Boolean {
        cachedRootStatus?.let { return it }
        val isRooted = try {
            checkSuBinary() || checkMagisk() || checkSuperSU() || checkRootApps(context)
        } catch (_: Exception) { false }
        cachedRootStatus = isRooted
        return isRooted
    }

    private fun checkSuBinary(): Boolean {
        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk",
            "/data/adb/magisk", "/cache/adb/magisk"
        )
        return paths.any { File(it).exists() }
    }

    private fun checkMagisk(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "magisk"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("magisk")
        } catch (_: Exception) {
            File("/data/adb/magisk").exists() || File("/data/adb/modules").exists() || File("/sbin/.magisk").exists()
        }
    }

    private fun checkSuperSU(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            process.waitFor() == 0
        } catch (_: Exception) { false }
    }

    private fun checkRootApps(context: Context): Boolean {
        val packages = arrayOf(
            "com.topjohnwu.magisk", "eu.chainfire.supersu", "com.koushikdutta.superuser",
            "com.thirdparty.superuser", "com.noshufou.android.su", "com.devadvance.rootcloak",
            "com.devadvance.rootcloakplus", "com.smedialink.oneclickroot", "com.zhiquion.rootclean"
        )
        return packages.any { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) { false }
        }
    }

    fun detectTamper(context: Context): Boolean {
        cachedTamperStatus?.let { return it }
        val isTampered = try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            if (signatures == null || signatures.isEmpty()) return true
            val sig = signatures[0]
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(sig.toByteArray())
            val currentHash = hash.joinToString("") { "%02x".format(it) }
            val expectedHash = getExpectedSignatureHash(context)
            if (expectedHash.isNotEmpty()) currentHash != expectedHash else false
        } catch (_: Exception) { true }
        cachedTamperStatus = isTampered
        return isTampered
    }

    private fun getExpectedSignatureHash(context: Context): String {
        return try {
            context.getSharedPreferences("security_meta", Context.MODE_PRIVATE).getString("expected_sig_hash", "") ?: ""
        } catch (_: Exception) { "" }
    }

    fun detectEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
            "google_sdk" == Build.PRODUCT ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("vbox86p") ||
            Build.PRODUCT.contains("emulator") ||
            Build.PRODUCT.contains("simulator")
    }

    fun isUsbDebuggingEnabled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
            } else {
                @Suppress("DEPRECATION")
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.ADB_ENABLED, 0) == 1
            }
        } catch (_: Exception) { false }
    }

    fun getProtectionStatus(context: Context): ProtectionStatus {
        val rooted = detectRoot(context)
        val tampered = detectTamper(context)
        val emulator = detectEmulator()
        val adbEnabled = isUsbDebuggingEnabled(context)
        
        return ProtectionStatus(
            permanentlyLocked = false,
            lockReason = null,
            deviceCompromised = rooted || tampered,
            isRooted = rooted,
            emulatorDetected = emulator,
            adbEnabled = adbEnabled
        )
    }

    data class ProtectionStatus(
        val permanentlyLocked: Boolean,
        val lockReason: String?,
        val deviceCompromised: Boolean,
        val isRooted: Boolean,
        val emulatorDetected: Boolean,
        val adbEnabled: Boolean
    )
}
