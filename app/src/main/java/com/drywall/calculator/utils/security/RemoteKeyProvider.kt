package com.drywall.calculator.utils.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.drywall.calculator.BuildConfig
import com.drywall.common.net.ConsoleEndpoints
import com.drywall.common.security.SecureStorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec

/**
 * Descarga la clave pública de verificación desde la **Consola de Licencias**
 * propia (módulo `server/`) y la cachea cifrada en el dispositivo para poder
 * verificar licencias sin conexión.
 *
 * Antes apuntaba a un dominio ajeno (`eltoque.com`) y la comparaba contra un hash
 * fijo que resultaba ser el SHA-256 de la cadena "password": cualquiera que
 * controlara esa ruta podía publicar la clave pública que quisiera y emitir
 * licencias válidas para esta app. Ahora:
 *
 *  1. El dominio sale del build (`LICENSE_CONSOLE_URL`) y [ConsoleEndpoints]
 *     solo acepta `https://`; `network_security_config.xml` ya prohíbe el
 *     tráfico en claro.
 *  2. Se comprueba que la respuesta sea una clave pública RSA con estructura
 *     X.509 válida antes de aceptarla.
 *  3. Si se configura `LICENSE_CONSOLE_KEY_SHA256` se fija la huella esperada
 *     (pinning) y cualquier desvío se rechaza.
 *
 * Si la consola no está configurada, [getPublicKey] lanza para forzar la
 * activación online: nunca se acepta una licencia sin clave verificable.
 */
object RemoteKeyProvider {
    private const val TAG = "RemoteKeyProvider"
    private const val PREFS_NAME = "secure_remote_key_prefs_v3"
    private const val KEY_CACHED_KEY = "cached_public_key"
    private const val KEY_CACHED_HASH = "cached_key_hash"
    private const val KEY_LAST_FETCH = "last_fetch_time"

    /**
     * Pinning opcional: SHA-256 (hex) de la clave pública esperada. Se lee en la
     * consola → *Claves de firma*. Vacío ⇒ se acepta la clave vigente publicada
     * por tu consola, lo que permite rotar sin recompilar la app.
     */
    private val expectedKeyHash: String = BuildConfig.LICENSE_CONSOLE_KEY_SHA256

    private const val MAX_PEM_BYTES = 16 * 1024
    private const val MIN_BODY_LENGTH = 100

    private var cachedKey: String? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return SecureStorageUtils.getEncryptedPrefs(context, PREFS_NAME)
    }

    /** URL vigente de la clave pública, o null si la consola no está configurada. */
    fun keyServerUrl(): String? = ConsoleEndpoints.publicKeyPem(BuildConfig.LICENSE_CONSOLE_URL)

    fun isConsoleConfigured(): Boolean = ConsoleEndpoints.isConfigured(BuildConfig.LICENSE_CONSOLE_URL)

    /** Descarga y valida la clave pública; la deja cacheada cifrada. */
    suspend fun fetchPublicKey(context: Context): String? = withContext(Dispatchers.IO) {
        val endpoint = keyServerUrl()
        if (endpoint == null) {
            Log.w(TAG, "Consola de licencias no configurada (LICENSE_CONSOLE_URL vacío o sin HTTPS)")
            null
        } else {
            try {
                val pemString = URL(endpoint).readBytes(MAX_PEM_BYTES).toString(Charsets.UTF_8).trim()
                val keyHash = computeHash(pemString)
                val pinned = expectedKeyHash.isBlank() ||
                    keyHash.equals(expectedKeyHash, ignoreCase = true)

                if (pinned && isValidPublicKeyPem(pemString)) {
                    getPrefs(context).edit()
                        .putString(KEY_CACHED_KEY, pemString)
                        .putString(KEY_CACHED_HASH, keyHash)
                        .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                        .commit()
                    cachedKey = pemString
                    pemString
                } else {
                    Log.e(
                        TAG,
                        if (pinned) "La respuesta no es una clave pública RSA válida"
                        else "Clave pública rechazada: la huella no coincide con la fijada"
                    )
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo descargar la clave pública: ${e.javaClass.simpleName}")
                null
            }
        }
    }

    /**
     * Clave pública en Base64 (formato que espera `SecurityUtils.verifySignature`):
     * primero la caché en memoria, luego la guardada cifrada. Si no hay nada,
     * lanza para forzar la activación online.
     */
    fun getPublicKey(context: Context): String {
        cachedKey?.let { return toBase64Body(it) }
        try {
            val prefs = getPrefs(context)
            prefs.getString(KEY_CACHED_KEY, null)?.let { cached ->
                cachedKey = cached
                return toBase64Body(cached)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo la clave cacheada: ${e.javaClass.simpleName}")
        }
        return getHardcodedFallbackKey()
    }

    /** true si hay clave cacheada (para diagnósticos y pantallas de estado). */
    fun hasCachedKey(context: Context): Boolean {
        if (cachedKey != null) return true
        return try {
            getPrefs(context).getString(KEY_CACHED_KEY, null) != null
        } catch (e: Exception) {
            false
        }
    }

    /** Cuándo se descargó por última vez la clave (0 si nunca). */
    fun lastFetchTime(context: Context): Long {
        return try {
            getPrefs(context).getLong(KEY_LAST_FETCH, 0L)
        } catch (e: Exception) {
            0L
        }
    }

    /** Quita cabeceras PEM y saltos de línea ⇒ Base64 del DER (X.509 SPKI). */
    private fun toBase64Body(pem: String): String {
        return pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
            .trim()
    }

    /** Comprueba estructura PEM y que la clave RSA se pueda reconstruir. */
    private fun isValidPublicKeyPem(pem: String): Boolean {
        if (!pem.startsWith("-----BEGIN PUBLIC KEY-----")) return false
        if (!pem.contains("-----END PUBLIC KEY-----")) return false
        val body = toBase64Body(pem)
        if (body.length < MIN_BODY_LENGTH) return false
        return try {
            val der = Base64.decode(body, Base64.NO_WRAP)
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
            publicKey.encoded.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "PEM con estructura inválida: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun computeHash(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun getHardcodedFallbackKey(): String {
        Log.e(
            TAG,
            "CRÍTICO: no hay clave pública en caché ni consola configurada. " +
                "La verificación de licencias no puede continuar sin conexión a internet."
        )
        throw SecurityException(
            "No hay clave pública disponible. Se requiere conexión a internet para la primera activación."
        )
    }
}
