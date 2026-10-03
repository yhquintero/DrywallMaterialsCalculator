package com.drywall.keygen.net

import android.util.Log
import com.drywall.common.net.ConsoleEndpoints
import com.drywall.keygen.BuildConfig
import com.drywall.keygen.data.IssuedLicense
import com.drywall.keygen.security.KeyGenSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sincroniza el keygen Android con la **Consola de Licencias** web.
 *
 * Dos operaciones, ambas por HTTPS y con token:
 *  1. [comparePublicKey] — compara la llave pública vigente del móvil con la
 *     activa en la consola, para saber si ambas emiten con la MISMA identidad
 *     criptográfica (y por tanto una licencia web se activa en los móviles).
 *  2. [pushLicenses] — sube las licencias emitidas localmente (`issued_licenses`)
 *     a `/api/licenses/import`, que verifica cada firma antes de aceptarla.
 *
 * Configuración (BuildConfig, ver `keygen/build.gradle.kts`):
 *   LICENSE_CONSOLE_URL    p. ej. https://licencias.tudominio.com
 *   LICENSE_CONSOLE_TOKEN  token de un usuario con permisos licenses.import
 *
 * Si no están configurados, todas las funciones devuelven [SyncResult.Disabled]
 * y el keygen sigue funcionando 100 % offline como hasta ahora.
 */
object ConsoleSync {

    private const val TAG = "ConsoleSync"
    private const val CONNECT_TIMEOUT_S = 15L
    private const val WRITE_TIMEOUT_S = 30L
    private const val READ_TIMEOUT_S = 30L

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
            .followRedirects(false) // jamás seguir una redirección a HTTP
            .followSslRedirects(false)
            .build()
    }

    /** Resultado de una operación contra la consola. */
    sealed class SyncResult {
        class Success(val message: String, val detail: JSONObject?) : SyncResult()
        class Failure(val message: String) : SyncResult()
        class Disabled : SyncResult()
    }

    fun isConfigured(): Boolean {
        return ConsoleEndpoints.isConfigured(BuildConfig.LICENSE_CONSOLE_URL) &&
            BuildConfig.LICENSE_CONSOLE_TOKEN.isNotBlank()
    }

    /** Comprueba que la consola responde (`GET /api/public/health`). */
    suspend fun checkHealth(): SyncResult = withContext(Dispatchers.IO) {
        val url = ConsoleEndpoints.health(BuildConfig.LICENSE_CONSOLE_URL)
        if (url == null) {
            SyncResult.Disabled()
        } else {
            get(url) { response, body ->
                if (response.isSuccessful) {
                    SyncResult.Success("Consola operativa", parseOrNull(body))
                } else {
                    SyncResult.Failure("La consola respondió ${response.code}")
                }
            }
        }
    }

    /**
     * Sube a la consola las licencias emitidas en el móvil.
     * La consola verifica cada firma con la clave activa antes de aceptarla.
     */
    suspend fun pushLicenses(
        licenses: List<IssuedLicense>,
        appId: String = ConsoleEndpoints.APP_DRYWALL
    ): SyncResult = withContext(Dispatchers.IO) {
        val url = ConsoleEndpoints.importLicenses(BuildConfig.LICENSE_CONSOLE_URL)
        if (url == null || !isConfigured()) {
            SyncResult.Disabled()
        } else if (licenses.isEmpty()) {
            SyncResult.Success("No hay licencias que sincronizar", null)
        } else {
            val payload = JSONObject()
            payload.put("licenses", buildLicensesArray(licenses, appId))
            post(url, payload) { response, body ->
                val json = parseOrNull(body)
                if (response.isSuccessful && json != null) {
                    val imported = json.optInt("imported")
                    val updated = json.optInt("updated")
                    val invalid = json.optInt("invalidSignature")
                    val suffix = if (invalid > 0) ", $invalid rechazadas por firma inválida" else ""
                    SyncResult.Success("Sincronizado: $imported nuevas, $updated actualizadas$suffix", json)
                } else {
                    SyncResult.Failure(json?.optString("error") ?: "Error ${response.code} de la consola")
                }
            }
        }
    }

    /**
     * Compara la llave pública del móvil con la activa en la consola.
     *
     * La importación de la llave privada es una operación de administrador que
     * se hace en la web (*Claves de firma → Importar llave del keygen Android*);
     * aquí solo se informa de si ambas identidades ya coinciden.
     */
    suspend fun comparePublicKey(
        appId: String = ConsoleEndpoints.APP_DRYWALL
    ): SyncResult = withContext(Dispatchers.IO) {
        val url = ConsoleEndpoints.publicKeyJson(BuildConfig.LICENSE_CONSOLE_URL, appId)
        val local = KeyGenSecurity.publicKeyString
        if (url == null) {
            SyncResult.Disabled()
        } else if (local.isBlank()) {
            SyncResult.Failure("El móvil aún no ha generado su llave maestra")
        } else {
            get(url) { response, body ->
                val json = parseOrNull(body)
                if (!response.isSuccessful || json == null) {
                    SyncResult.Failure("La consola respondió ${response.code}")
                } else {
                    val remote = json.optString("publicKeyBase64")
                    val kid = json.optString("kid")
                    if (remote == local) {
                        SyncResult.Success("Clave pública sincronizada ($kid)", json)
                    } else {
                        SyncResult.Failure(
                            "La consola usa otra clave ($kid). Importa la llave privada del " +
                                "móvil en Claves de firma para unificar ambas identidades."
                        )
                    }
                }
            }
        }
    }

    private fun buildLicensesArray(licenses: List<IssuedLicense>, appId: String): JSONArray {
        val array = JSONArray()
        for (license in licenses) {
            val item = JSONObject()
            item.put("appId", appId)
            item.put("usuario", license.userName)
            item.put("dispositivo", license.deviceId)
            item.put("plan", license.planType)
            item.put("precio", license.price)
            item.put("isPaid", license.isPaid)
            item.put("dateIssued", license.dateIssued)
            item.put("notes", license.notes)
            // La consola verifica la firma con la clave activa antes de aceptar.
            if (license.licenseJson.isNotBlank()) {
                val licenseJson = parseOrNull(license.licenseJson)
                if (licenseJson != null) item.put("codigoLicencia", licenseJson)
            }
            array.put(item)
        }
        return array
    }

    private fun parseOrNull(body: String): JSONObject? {
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            null
        }
    }

    private fun get(
        url: String,
        handler: (okhttp3.Response, String) -> SyncResult
    ): SyncResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", userAgent())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                handler(response, readBody(response))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo de red contra la consola", e)
            SyncResult.Failure("Sin conexión con la consola: ${e.javaClass.simpleName}")
        }
    }

    private fun post(
        url: String,
        payload: JSONObject,
        handler: (okhttp3.Response, String) -> SyncResult
    ): SyncResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${BuildConfig.LICENSE_CONSOLE_TOKEN}")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", userAgent())
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                handler(response, readBody(response))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo de red contra la consola", e)
            SyncResult.Failure("Sin conexión con la consola: ${e.javaClass.simpleName}")
        }
    }

    /**
     * En OkHttp 5.x `Response.body` ya no es nulo, así que se llama `string()`
     * directamente (mismo patrón que `NetworkTimeProvider` y `CurrencyScraper`).
     */
    private fun readBody(response: okhttp3.Response): String {
        return try {
            response.body.string()
        } catch (e: Exception) {
            ""
        }
    }

    private fun userAgent(): String {
        return "KeygenPro/" + BuildConfig.VERSION_NAME + " (Android)"
    }
}
