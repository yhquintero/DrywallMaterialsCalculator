package com.drywall.calculator.utils.security

import android.content.Context
import android.util.Log
import com.drywall.calculator.BuildConfig
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.tasks.await

/**
 * Implementación real de Google Play Integrity API.
 * Requiere:
 * - Cuenta Google Play Console con acceso a Play Integrity
 * - El número de proyecto de Google Cloud en BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER
 * - Un backend propio que verifique el token IntegrityTokenResponse
 *
 * @see <a href="https://developer.android.com/google/play/integrity">Play Integrity API</a>
 */
object PlayIntegrityHelper {
    private const val TAG = "PlayIntegrityHelper"
    private const val CLOUD_PROJECT_NUMBER: Long = BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER

    /**
     * Verifica la integridad del dispositivo.
     * Retorna el token de integridad que debe ser verificado por tu backend.
     */
    suspend fun verifyIntegrity(context: Context): IntegrityResult {
        if (CLOUD_PROJECT_NUMBER == 0L) {
            Log.w(TAG, "Play Integrity no configurado — PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER=0")
            return IntegrityResult.NotConfigured("PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER no está configurado en build.gradle.kts")
        }

        return try {
            val integrityManager = IntegrityManagerFactory.create(context)
            val integrityTokenResponse = integrityManager.requestIntegrityToken(
                IntegrityTokenRequest.builder()
                    .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                    .build()
            ).await()
            val token = integrityTokenResponse.token()
            if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) {
                Log.d(TAG, "Play Integrity token obtenido exitosamente")
            }
            IntegrityResult.Verified(token)
        } catch (e: Exception) {
            Log.e(TAG, "Error en Play Integrity", e)
            IntegrityResult.Error(e.message ?: "Error desconocido")
        }
    }

    sealed class IntegrityResult {
        data class Verified(val token: String) : IntegrityResult()
        data class NotConfigured(val message: String) : IntegrityResult()
        data class Error(val message: String) : IntegrityResult()
    }
}
