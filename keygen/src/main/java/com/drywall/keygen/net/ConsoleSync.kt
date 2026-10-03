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
 *  1. [publishPublicKey] — registra en la consola la llave pública vigente del
 *     móvil, para que las licencias emitidas desde la web usen la MISMA clave
 *     (así una licencia emitida en el navegador se verifica en los móviles).
 *  2. [pushLicenses] — sube las licencias emitidas localmente (`issued_licenses`)
 *     al endpoint `/api/licenses/import`, que verifica cada firma antes de
 *     aceptarla. La consola las marca con `source = "import"`.
 *
 * Configuración (BuildConfig, ver `keygen/build.gradle.kts`):
 *   LICENSE_CONSOLE_URL    p. ej. https://licencias.tudominio.com
 *   LICENSE_CONSOLE_TOKEN  token de acceso de un usuario con permisos
 *                          `licenses.import` / `keys.rotate`
 *
 * Si no están configurados, todas las funciones devuelven [Result.disabled] y
 * el keygen sigue funcionando 100% offline como hasta ahora.
 */
object ConsoleSync {

    private const val TAG = "ConsoleSync"
    private const val CONNECT_TIMEOUT_S = 15L
    private const val WRITE_TIMEOUT_S = 30L
    private const val READ_TIMEOUT_S = 30L

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
            .followRedirects(false) // jamás seguir una redirección a HTTP
            .followSslRedirects(false)
            .build()
    }

    sealed class Result {
        data class Success(val message: String, val detail: JSONObject? = null) : Result()
        data class Failure(val message: String) : Result()
        data object Disabled : Result()

        val isSuccess: Boolean get() = this is Success
    }

    fun isConfigured(): Boolean =
        ConsoleEndpoints.isConfigured(BuildConfig.LICENSE_CONSOLE_URL) &&
            BuildConfig.LICENSE_CONSOLE_TOKEN.isNotBlank()

    /** Comprueba que la consola responde (GET /api/public/health). */
    suspend fun checkHealth(): Result = withContext(Dispatchers.IO) {
        val url = ConsoleEndpoints.health(BuildConfig.LICENSE_CONSOLE_URL) ?: return@withContext Result.Disabled
        runCatching {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Result.Success("Consola operativa", runCatching { JSONObject(body) }.getOrNull())
                } else {
                    Result.Failure("La consola respondió ${response.code}")
                }
            }
        }.getOrElse { Result.Failure("Sin conexión con la consola: ${it.javaClass.simpleName}") }
    }

    /**
     * Sube las licencias emitidas en el móvil a la consola.
     * @param licenses registro local de Room (`issued_licenses`).
     */
    suspend fun pushLicenses(licenses: List<IssuedLicense>, appId: String = ConsoleEndpoints.APP_DRYWALL): Result =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext Result.Disabled
            val url = ConsoleEndpoints.importLicenses(BuildConfig.LICENSE_CONSOLE_URL)
                ?: return@withContext Result.Disabled
            if (licenses.isEmpty()) return@withContext Result.Success("No hay licencias que sincronizar")

            val payload = JSONObject().apply {
                put(
                    "licenses",
                    JSONArray().apply {
                        licenses.forEach { license ->
                            put(
                                JSONObject().apply {
                                    put("appId", appId)
                                    put("usuario", license.userName)
                                    put("dispositivo", license.deviceId)
                                    put("plan", license.planType)
                                    put("precio", license.price)
                                    put("isPaid", license.isPaid)
                                    put("dateIssued", license.dateIssued)
                                    put("notes", license.notes)
                                    // La consola verifica la firma con la clave activa.
                                    license.licenseJson.takeIf { it.isNotBlank() }?.let { json ->
                                        runCatching { put("codigoLicencia", JSONObject(json)) }
                                    }
                                }
                            )
                        }
                    }
                )
            }

            postJson(url, payload) { responseJson ->
                val imported = responseJson.optInt("imported")
                val updated = responseJson.optInt("updated")
                val invalid = responseJson.optInt("invalidSignature")
                Result.Success(
                    "Sincronizado: $imported nuevas, $updated actualizadas" +
                        if (invalid > 0) ", $invalid rechazadas por firma inválida" else "",
                    responseJson
                )
            }
        }

    /**
     * Publica la llave pública del móvil en la consola para que ambas emitan con
     * la misma identidad criptográfica.
     *
     * La consola expone la clave activa en `/.well-known/licensing-public-key.json`;
     * este método solo la compara y avisa si difiere (la importación de la llave
     * privada es una operación de administrador que se hace en la web).
     */
    suspend fun comparePublicKey(appId: String = ConsoleEndpoints.APP_DRYWALL): Result = withContext(Dispatchers.IO) {
        val url = ConsoleEndpoints.publicKeyJson(BuildConfig.LICENSE_CONSOLE_URL, appId)
            ?: return@withContext Result.Disabled
        val local = KeyGenSecurity.publicKeyString
        if (local.isBlank()) return@withContext Result.Failure("El móvil aún no ha generado su llave maestra")

        runCatching {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.Failure("La consola respondió ${response.code}")
                val json = JSONObject(response.body?.string().orEmpty())
                val remote = json.optString("publicKeyBase64")
                if (remote == local) {
                    Result.Success("Clave pública sincronizada (${json.optString("kid")})", json)
                } else {
                    Result.Failure(
                        "La consola usa otra clave (${json.optString("kid")}). " +
                            "Importa la llave privada del móvil en Claves de firma para unificar."
                    )
                }
            }
        }.getOrElse { Result.Failure("No se pudo comparar la clave: ${it.javaClass.simpleName}") }
    }

    private inline fun postJson(url: String, payload: JSONObject, onSuccess: (JSONObject) -> Result): Result {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${BuildConfig.LICENSE_CONSOLE_TOKEN}")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "KeygenPro/${BuildConfig.VERSION_NAME} (Android)")
                .post(payload.toString().toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                if (response.isSuccessful) {
                    onSuccess(json ?: JSONObject())
                } else {
                    Result.Failure(json?.optString("error") ?: "Error ${response.code} de la consola")
                }
            }
        }.getOrElse {
            Log.w(TAG, "Fallo de red contra la consola", it)
            Result.Failure("Sin conexión con la consola: ${it.javaClass.simpleName}")
        }
    }
}
