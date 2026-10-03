package com.drywall.common.net

/**
 * Endpoints HTTPS de la **Consola de Licencias** (módulo `server/` del repositorio).
 *
 * La consola es la fuente de verdad para:
 *  • la clave pública de firma de cada app (`/.well-known/…`),
 *  • el estado de revocación de una licencia,
 *  • las tasas de cambio publicadas,
 *  • y el registro central de licencias emitidas (también desde el móvil).
 *
 * `baseUrl` se inyecta en tiempo de compilación mediante el campo BuildConfig
 * `LICENSE_CONSOLE_URL` (ver `app/build.gradle.kts` y `keygen/build.gradle.kts`).
 * Si está vacío, las funciones que dependen de la red devuelven `null` y la app
 * sigue operando 100% offline con la clave empaquetada.
 *
 * TODO el tráfico exige HTTPS: `network_security_config.xml` ya bloquea el
 * tráfico en claro (`cleartextTrafficPermitted="false"`).
 */
object ConsoleEndpoints {

    /** Identificador de app que entiende la consola. */
    const val APP_DRYWALL = "drywall_calculator"
    const val APP_KEYGEN = "keygen_pro"

    /** Normaliza la URL base: sin barra final y solo https. */
    fun normalize(rawBaseUrl: String?): String? {
        val trimmed = rawBaseUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        return trimmed.trimEnd('/')
    }

    fun publicKeyPem(baseUrl: String?, appId: String = APP_DRYWALL): String? =
        normalize(baseUrl)?.let { "$it/.well-known/licensing-public-key-v2.pem?app=$appId" }

    fun publicKeyJson(baseUrl: String?, appId: String = APP_DRYWALL): String? =
        normalize(baseUrl)?.let { "$it/.well-known/licensing-public-key.json?app=$appId" }

    fun appsJson(baseUrl: String?): String? =
        normalize(baseUrl)?.let { "$it/.well-known/apps.json" }

    fun health(baseUrl: String?): String? =
        normalize(baseUrl)?.let { "$it/api/public/health" }

    fun rates(baseUrl: String?): String? =
        normalize(baseUrl)?.let { "$it/api/public/rates" }

    fun revocationStatus(baseUrl: String?, signature: String, appId: String = APP_DRYWALL): String? =
        normalize(baseUrl)?.let {
            "$it/api/public/revocation-status?app=$appId&signature=${java.net.URLEncoder.encode(signature, "UTF-8")}"
        }

    fun validate(baseUrl: String?): String? =
        normalize(baseUrl)?.let { "$it/api/public/validate" }

    /** Sincronización autenticada (requiere token de la consola). */
    fun importLicenses(baseUrl: String?): String? =
        normalize(baseUrl)?.let { "$it/api/licenses/import" }

    fun login(baseUrl: String?): String? =
        normalize(baseUrl)?.let { "$it/api/auth/login" }

    fun isConfigured(baseUrl: String?): Boolean = normalize(baseUrl) != null
}
