package com.drywall.common.security

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object SecurityUtils {

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
        val digest = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP).trim()
    }

    fun verifySignature(data: String, signatureBase64: String, publicKeyBase64: String): Boolean {
        val cleanKey = publicKeyBase64.replace(Regex("[^A-Za-z0-9+/=]"), "")
        val cleanSignature = signatureBase64.replace(Regex("[^A-Za-z0-9+/=]"), "")
        val signatureBytes = Base64.decode(cleanSignature, Base64.DEFAULT)
        val publicKey = getPublicKey(cleanKey)
        val dataBytes = data.toByteArray(Charsets.UTF_8)

        val algorithms = listOf("SHA256withRSAandMGF1", "SHA256withRSA")
        for (algo in algorithms) {
            try {
                val sig = Signature.getInstance(algo)
                sig.initVerify(publicKey)
                sig.update(dataBytes)
                if (sig.verify(signatureBytes)) return true
            } catch (_: Exception) { }
        }
        Log.e("SecurityUtils", "Firma inválida con todos los algoritmos probados")
        return false
    }

    private fun getPublicKey(base64PublicKey: String): PublicKey {
        val keyBytes = Base64.decode(base64PublicKey, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(spec)
    }

    fun getLegacyDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)?.trim()?.lowercase() ?: "UNKNOWN"
    }

    fun getDeviceIdFromLegacy(legacyDeviceId: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(legacyDeviceId.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(digest, Base64.NO_WRAP).trim()
        } catch (_: Exception) {
            "UNKNOWN"
        }
    }
}
