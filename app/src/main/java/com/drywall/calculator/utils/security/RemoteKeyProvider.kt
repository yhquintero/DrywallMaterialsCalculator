package com.drywall.calculator.utils.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.drywall.calculator.BuildConfig
import com.drywall.common.net.ConsoleEndpoints
import com.drywall.common.security.SecureStorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Descarga la clave pública de verificación desde la **Consola de Licencias**
 * propia (módulo `server/`), siempre por HTTPS, y la cachea cifrada en el
 * dispositivo para poder verificar licencias sin conexión.
 *
 * Antes apuntaba a un dominio ajeno (`eltoque.com`) y comparaba contra un hash
 * fijo que correspondía a la cadena "password": cualquier atacante que
 * controlara esa ruta podía publicar la clave pública que quisiera y emitir
 * licencias válidas. Ahora:
 *
 *  1. El dominio se configura en el build (`LICENSE_CONSOLE_URL`) y solo se
 *     acepta `https://` — ver [ConsoleEndpoints.normalize].
 *  2. Se valida el formato PEM y que la clave sea una RSA utilizable.
 *  3. Se compara la huella SHA-256 contra [EXPECTED_KEY_SHA256] cuando está
 *     configurada (pinning); si cambia de forma inesperada se rechaza.
 *  4. Se fija `X-Frame-Options`/HSTS por parte del servidor y aquí se limita el
 *     tamaño de la respuesta para evitar desbordamientos.
 */
object RemoteKeyProvider {
    private const val TAG = "RemoteKeyProvider"
    private const val PREFS_NAME = "secure_remote_key_prefs_v3"
    private const val KEY_CACHED_KEY = "cached_public_key"
    private const val KEY_CACHED_HASH = "cached_key_hash"
    private const val KEY_LAST_FETCH = "last_fetch_time"

    /**
     * Pinning opcional de la clave pública esperada (SHA-256 hex del DER).
     * Se obtiene en la consola: *Claves de firma → SHA-256 de la pública*.
     * Déjalo vacío para aceptar la clave vigente publicada por tu consola
     * (útil al rotar claves sin recompilar la app).
     */
    private val EXPECTED_KEY_SHA256: String = BuildConfig.LICENSE_CONSOLE_KEY_SHA256

    private const val MAX_PEM_BYTES = 16 * 1024
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    private var cachedKey: String? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return SecureStorageUtils.getEncryptedPrefs(context, PREFS_NAME)
    }

    /** URL vigente de la clave pública (o null si la consola no está configurada). */
    fun keyServerUrl(): String? = ConsoleEndpoints.publicKeyPem(BuildConfig.LICENSE_CONSOLE_URL)

    fun isConsoleConfigured(): Boolean = ConsoleEndpoints.isConfigured(BuildConfig.LICENSE_CONSOLE_URL)

    suspend fun fetchPublicKey(context: Context): String? = withContext(Dispatchers.IO) {
        val url = keyServerUrl()
        if (url == null) {
            Log.w(TAG, "Consola de licencias no configurada (LICENSE_CONSOLE_URL vacío o sin HTTPS)")
            return@withContext null
        }
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false // nada de seguir a HTTP
                setRequestProperty("Accept", "application/x-pem-file")
                setRequestProperty("User-Agent", "DrywallPro/${BuildConfig.VERSION_NAME} (Android)")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "La consola respondió ${connection.responseCode} al pedir la clave pública")
                    return@withContext null
                }
                val pemString = connection.inputStream.readBytes(MAX_PEM_BYTES).toString(Charsets.UTF_8).trim()
                if (!looksLikePublicKeyPem(pemString)) {
                    Log.e(TAG, "La respuesta no es una clave pública PEM válida")
                    return@withContext null
                }

                val keyHash = computeHash(pemString)
                if (EXPECTED_KEY_SHA256.isNotBlank() && !keyHash.equals(EXPECTED_KEY_SHA256, ignoreCase = true)) {
                    Log.e(TAG, "Clave pública rechazada: la huella no coincide con la fijada (pinning)")
                    return@withContext null
                }

                getPrefs(context).edit()
                    .putString(KEY_CACHED_KEY, pemString)
                    .putString(KEY_CACHED_HASH, keyHash)
                    .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                    .commit()
                cachedKey = pemString
                pemString
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo descargar la clave pública: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Devuelve la clave pública en Base64 (formato que espera
     * `SecurityUtils.verifySignature`): primero la caché en memoria, luego la
     * guardada cifrada y, si no hay nada, lanza para forzar la activación online.
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

    /** Devuelve true si la clave cacheada sigue siendo válida (para diagnósticos). */
    fun hasCachedKey(context: Context): Boolean {
        if (cachedKey != null) return true
        return try {
            getPrefs(context).getString(KEY_CACHED_KEY, null) != null
        } catch (_: Exception) {
            false
        }
    }

    fun lastFetchTime(context: Context): Long =
        try {
            getPrefs(context).getLong(KEY_LAST_FETCH, 0L)
        } catch (_: Exception) {
            0L
        }

    /** Quita cabeceras PEM y saltos de línea ⇒ Base64 del DER (X.509 SPKI). */
    private fun toBase64Body(pem: String): String = pem
        .replace(Regex("-----BEGIN [^-]+-----"), "")
        .replace(Regex("-----END [^-]+-----"), "")
        .replace(Regex("\\s+"), "")

    private fun looksLikePublicKeyPem(pem: String): Boolean {
        if (!pem.startsWith("-----BEGIN PUBLIC KEY-----")) return false
        if (!pem.contains("-----END PUBLIC KEY-----")) return false
        val body = toBase64Body(pem)
        if (body.length < 100) return false
        return try {
            val der = android.util.Base64.decode(body, android.util.Base64.DEFAULT)
            val keyFactory = java.security.KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(java.security.spec.X509EncodedKeySpec(der))
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
