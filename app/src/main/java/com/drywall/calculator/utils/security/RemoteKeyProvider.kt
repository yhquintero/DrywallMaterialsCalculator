package com.drywall.calculator.utils.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.drywall.common.security.SecureStorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.security.MessageDigest
import java.util.Base64

object RemoteKeyProvider {
    private const val TAG = "RemoteKeyProvider"
    private const val PREFS_NAME = "secure_remote_key_prefs_v2"
    private const val KEY_CACHED_KEY = "cached_public_key"
    private const val KEY_CACHED_HASH = "cached_key_hash"
    private const val KEY_LAST_FETCH = "last_fetch_time"

    private val EXPECTED_KEY_HASH = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"

    private const val KEY_SERVER_URL = "https://eltoque.com/.well-known/licensing-public-key-v2.pem"

    private var cachedKey: String? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return SecureStorageUtils.getEncryptedPrefs(context, PREFS_NAME)
    }

    suspend fun fetchPublicKey(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(KEY_SERVER_URL)
            val pemString = url.readBytes().toString(Charsets.UTF_8)
            val keyHash = computeHash(pemString)
            
            if (keyHash == EXPECTED_KEY_HASH) {
                getPrefs(context).edit()
                    .putString(KEY_CACHED_KEY, pemString)
                    .putString(KEY_CACHED_HASH, keyHash)
                    .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                    .commit()
                cachedKey = pemString
                pemString
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun getPublicKey(context: Context): String {
        cachedKey?.let { return it }
        try {
            val prefs = getPrefs(context)
            prefs.getString(KEY_CACHED_KEY, null)?.let { cached ->
                cachedKey = cached
                return cached
            }
        } catch (_: Exception) { }
        return getHardcodedFallbackKey()
    }

    private fun computeHash(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun getHardcodedFallbackKey(): String {
        Log.e(TAG, "CRÍTICO: No hay clave pública disponible en caché ni en servidor. " +
            "La verificación de licencias no puede continuar sin conexión a internet.")
        throw SecurityException("No hay clave pública disponible. Se requiere conexión a internet para la primera activación.")
    }
}
