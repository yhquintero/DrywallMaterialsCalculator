package com.drywall.calculator.utils.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.drywall.common.security.NetworkTimeProvider
import com.drywall.common.security.SecureStorageUtils
import com.drywall.common.security.DeviceTrialMarker
import com.drywall.common.security.TrialProtectionManager
import com.drywall.common.security.SecurityUtils
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class LicenseInfo(
    @SerializedName("user") val user: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("creationDate") val creationDate: Long,
    @SerializedName("expiryDate") val expiryDate: Long,
    @SerializedName("signature") val signature: String,
    @SerializedName("type") val type: String,
    @SerializedName("issuerKey") val issuerKey: String? = null,
)

data class ConsumedLicense(
    @SerializedName("signature") val signature: String,
    @SerializedName("user") val user: String,
    @SerializedName("expiryDate") val expiryDate: Long,
    @SerializedName("consumptionDate") val consumptionDate: Long,
    @SerializedName("reason") val reason: String
)

sealed class ActivationResult {
    object Success : ActivationResult()
    data class Failure(val message: String, val detail: String? = null) : ActivationResult()
}

private sealed class VerificationResult {
    object Success : VerificationResult()
    data class Failure(val reason: String, val detail: String? = null) : VerificationResult()
}

object LicensingManager {
    private val gson = Gson()
    private val consumedCache = java.util.concurrent.atomic.AtomicReference<List<ConsumedLicense>?>(null)

    private const val PREFS_NAME = "secure_licensing_prefs_v3"
    private const val CONSUMED_VAULT_NAME = "permanent_license_vault_v3"
    private const val TRIAL_VAULT_NAME = "trial_license_vault_v2"
    private const val KEY_LICENSE = "license_data"
    private const val KEY_LAST_USAGE = "last_usage_time"
    private const val KEY_PERMANENT_EXPIRED = "permanent_expired_flag"
    private const val KEY_CONSUMED_SIGNATURES = "consumed_signatures_vault"
    private const val KEY_LAST_SYNC = "last_sync_time_verified"

    // Trial keys
    private const val KEY_TRIAL_USED = "trial_used_permanently"
    private const val KEY_TRIAL_START = "trial_start_time"
    private const val KEY_TRIAL_LAST_VALIDATION = "trial_last_validation"
    private const val KEY_TRIAL_ACTIVE = "trial_currently_active"
    private const val KEY_TRIAL_CONSUMED_RECORD = "trial_consumed_record"
    const val TRIAL_DURATION_MS = 7 * 24 * 60 * 60 * 1000L
    const val TRIAL_SIGNATURE = "TRIAL_WEEKLY_PERMANENT_v1"

    private fun getPublicKey(): String {
        return RemoteKeyProvider.getPublicKey(contextRef?.get() ?: return fallbackKey())
    }

    private var contextRef: java.lang.ref.WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = java.lang.ref.WeakReference(context.applicationContext)
        TrialProtectionManager.init(context)
        TrialProtectionManager.setContext(context)
    }

    private fun fallbackKey(): String {
        Log.e(TAG_LICENSING, "CRÍTICO: No hay clave pública disponible. Se requiere conexión a internet.")
        throw SecurityException("No hay clave pública disponible. Se requiere conexión a internet.")
    }

    private const val TAG_LICENSING = "LicensingManager"

    /**
     * Rompe el vínculo de la licencia profesional pero MANTIENE el estado de la prueba.
     * Esto evita que al borrar una licencia de pago se reactive la prueba gratuita.
     */
    fun removeProfessionalLicense(context: Context): Boolean {
        return try {
            val mainPrefs = getEncryptedPrefs(context) ?: return false
            mainPrefs.edit().apply {
                remove(KEY_LICENSE)
                remove(KEY_LAST_USAGE)
                remove(KEY_PERMANENT_EXPIRED)
            }.apply()
            
            consumedCache.set(null)
            true
        } catch (e: Exception) {
            Log.e(TAG_LICENSING, "Error removing professional license", e)
            false
        }
    }

    /**
     * Realiza un reseteo TOTAL. Solo debe usarse en casos de soporte técnico crítico.
     */
    private fun deepResetAllData(context: Context): Boolean {
        return try {
            val mainPrefs = getEncryptedPrefs(context)
            val trialVault = getTrialVaultPrefs(context)
            val permanentVault = getVaultPrefs(context)

            mainPrefs?.edit()?.clear()?.apply()
            trialVault?.edit()?.clear()?.apply()
            permanentVault?.edit()?.clear()?.apply()

            DeviceTrialMarker.removeTrialMarker(context)

            consumedCache.set(null)
            TrialProtectionManager.refreshStorageIntegrity(context)
            true
        } catch (e: Exception) {
            Log.e(TAG_LICENSING, "Error in deep reset", e)
            false
        }
    }

    fun activateTrial(context: Context): Boolean {
        return try {
            val protection = TrialProtectionManager.getProtectionStatus(context)
            if (protection.isRooted || protection.deviceCompromised) return false
            if (protection.emulatorDetected) return false

            if (TrialProtectionManager.isPermanentlyLocked(context)) return false
            if (hasTrialEverBeenUsed(context)) return false

            val currentTime = getCurrentTimeSafe(context)
            val trialVault = getTrialVaultPrefs(context) ?: return false

            trialVault.edit()
                .putBoolean(KEY_TRIAL_USED, true)
                .putBoolean(KEY_TRIAL_ACTIVE, true)
                .putLong(KEY_TRIAL_START, currentTime)
                .putLong(KEY_TRIAL_LAST_VALIDATION, currentTime)
                .apply()

            val consumedVault = getVaultPrefs(context)
            consumedVault?.edit()?.apply {
                putBoolean(KEY_TRIAL_USED, true)
                putBoolean(KEY_TRIAL_ACTIVE, true)
                putLong(KEY_TRIAL_START, currentTime)
                putLong(KEY_TRIAL_LAST_VALIDATION, currentTime)
            }?.apply()

            DeviceTrialMarker.storeTrialMarker(context, currentTime)

            if (!TrialAdminManager.isDeviceAdminActive(context)) {
                TrialAdminManager.requestDeviceAdminActivation(context)
            }

            TrialProtectionManager.refreshStorageIntegrity(context)
            true
        } catch (_: Exception) { false }
    }

    fun isTrialActive(context: Context): Boolean {
        return try {
            val protection = TrialProtectionManager.getProtectionStatus(context)
            if (protection.isRooted || protection.deviceCompromised) return false
            if (protection.emulatorDetected) return false

            if (TrialProtectionManager.isPermanentlyLocked(context)) return false
            if (!hasTrialEverBeenUsed(context)) return false

            val vault = getTrialVaultPrefs(context) ?: return false
            val trialUsed = vault.getBoolean(KEY_TRIAL_USED, false)
            val trialActive = vault.getBoolean(KEY_TRIAL_ACTIVE, false)
            if (!trialUsed || !trialActive) return false

            val trialStart = vault.getLong(KEY_TRIAL_START, 0L)
            if (trialStart == 0L) return false

            val networkTime = getCurrentTimeSafe(context)
            val systemTime = System.currentTimeMillis()
            val lastValidation = vault.getLong(KEY_TRIAL_LAST_VALIDATION, 0L)

            if (lastValidation > 0 && systemTime < lastValidation - 300000) {
                consumeTrialPermanently(context, "Violación: Reloj Retrocedido")
                return false
            }

            if (networkTime >= trialStart + TRIAL_DURATION_MS) {
                consumeTrialPermanently(context, "Expiración Normal - 7 días completados")
                return false
            }

            vault.edit()
                .putLong(KEY_TRIAL_LAST_VALIDATION, systemTime)
                .apply()

            true
        } catch (_: Exception) { false }
    }

    fun hasTrialEverBeenUsed(context: Context): Boolean {
        return try {
            val vault = getTrialVaultPrefs(context)
            if (vault?.getBoolean(KEY_TRIAL_USED, false) == true) return true

            DeviceTrialMarker.hasTrialMarker(context)
        } catch (_: Exception) { false }
    }

    fun getTrialRemainingTime(context: Context): Long {
        return try {
            if (!isTrialActive(context)) return 0L
            val vault = getTrialVaultPrefs(context) ?: return 0L
            val trialStart = vault.getLong(KEY_TRIAL_START, 0L)
            val currentTime = getCurrentTimeSafe(context)
            val remaining = (trialStart + TRIAL_DURATION_MS) - currentTime
            if (remaining < 0) 0L else remaining
        } catch (_: Exception) { 0L }
    }

    fun getTrialStartTime(context: Context): Long {
        return try {
            val vault = getTrialVaultPrefs(context) ?: return 0L
            vault.getLong(KEY_TRIAL_START, 0L)
        } catch (_: Exception) { 0L }
    }

    fun getTrialEndTime(context: Context): Long {
        val startTime = getTrialStartTime(context)
        return if (startTime > 0) startTime + TRIAL_DURATION_MS else getCurrentTimeSafe(context) + TRIAL_DURATION_MS
    }

    private fun consumeTrialPermanently(context: Context, reason: String) {
        try {
            val vault = getTrialVaultPrefs(context) ?: return
            val startTime = vault.getLong(KEY_TRIAL_START, 0L)
            vault.edit()
                .putBoolean(KEY_TRIAL_ACTIVE, false)
                .putBoolean(KEY_TRIAL_USED, true)
                .apply()

            val consumedVault = getVaultPrefs(context)
            consumedVault?.edit()?.apply {
                putBoolean(KEY_TRIAL_ACTIVE, false)
                putBoolean(KEY_TRIAL_USED, true)
                putLong(KEY_TRIAL_START, startTime)
            }?.apply()

            DeviceTrialMarker.storeTrialMarker(context, startTime)

            val record = ConsumedLicense(
                signature = TRIAL_SIGNATURE,
                user = "PRUEBA GRATIS - 7 días",
                expiryDate = startTime + TRIAL_DURATION_MS,
                consumptionDate = if (startTime > 0) startTime else getCurrentTimeSafe(context),
                reason = reason
            )
            val recordJson = gson.toJson(record)

            val set = vault.getStringSet(KEY_CONSUMED_SIGNATURES, emptySet())?.toMutableSet() ?: mutableSetOf()
            set.add(recordJson)
            vault.edit().putStringSet(KEY_CONSUMED_SIGNATURES, set).apply()

            val set2 = consumedVault?.getStringSet(KEY_CONSUMED_SIGNATURES, emptySet())?.toMutableSet() ?: mutableSetOf()
            set2.add(recordJson)
            consumedVault?.edit()?.putStringSet(KEY_CONSUMED_SIGNATURES, set2)?.apply()

            consumedCache.set(null)
            TrialProtectionManager.refreshStorageIntegrity(context)
        } catch (_: Exception) { }
    }

    fun getConsumedLicenses(context: Context): List<ConsumedLicense> {
        consumedCache.get()?.let { return it }
        return try {
            val prefs = getEncryptedPrefs(context)
            val vault = getVaultPrefs(context)

            val jsonSet1 = prefs?.getStringSet(KEY_CONSUMED_SIGNATURES, emptySet()) ?: emptySet()
            val jsonSet2 = vault?.getStringSet(KEY_CONSUMED_SIGNATURES, emptySet()) ?: emptySet()

            val totalSet = jsonSet1 + jsonSet2
            val list = totalSet.mapNotNull {
                try { gson.fromJson(it, ConsumedLicense::class.java) } catch (e: Exception) { null }
            }.distinctBy { it.signature }.sortedByDescending { it.consumptionDate }
            consumedCache.set(list)
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isLicenseValid(context: Context): Boolean {
        return try {
            // Security gate: block on compromised device
            val protection = TrialProtectionManager.getProtectionStatus(context)
            if (protection.isRooted || protection.deviceCompromised) return false
            if (protection.emulatorDetected) return false

            // First check if trial is active
            if (isTrialActive(context)) return true

            // Then check if professional license is valid
            val sharedPreferences = getEncryptedPrefs(context) ?: return false
            val license = getLicense(context) ?: return false

            val consumed = getConsumedLicenses(context)
            if (consumed.any { it.signature == license.signature }) {
                return false
            }

            if (sharedPreferences.getBoolean(KEY_PERMANENT_EXPIRED, false)) {
                if (!isLicenseViolation(context)) {
                    sharedPreferences.edit().putBoolean(KEY_PERMANENT_EXPIRED, false).apply()
                } else {
                    return false
                }
            }

            val currentTime = getCurrentTimeSafe(context)
            val lastUsage = sharedPreferences.getLong(KEY_LAST_USAGE, 0L)
            val isTimeSynced = NetworkTimeProvider.isSynchronized()

            if (isTimeSynced && lastUsage > 0 && currentTime < lastUsage - 300000) {
                markSignatureAsConsumed(context, license, "Violación: Reloj")
                sharedPreferences.edit().putBoolean(KEY_PERMANENT_EXPIRED, true).apply()
                return false
            }

            if (verifyLicenseWithResult(context, license) !is VerificationResult.Success) return false

            if (license.expiryDate <= currentTime) {
                consumeIfExpired(context)
                return false
            }

            if (currentTime < license.creationDate - 300000) {
                markSignatureAsConsumed(context, license, "Violación: Fecha Inicio")
                sharedPreferences.edit().putBoolean(KEY_PERMANENT_EXPIRED, true).apply()
                return false
            }

            if (isTimeSynced) {
                sharedPreferences.edit().putLong(KEY_LAST_USAGE, currentTime).apply()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isLicenseExpired(context: Context): Boolean {
        return try {
            // If trial is active, it's not expired
            if (isTrialActive(context)) return false

            // Check if trial was consumed (trial expired)
            if (hasTrialEverBeenUsed(context) && !isTrialActive(context)) return true

            // Check professional license
            val sharedPreferences = getEncryptedPrefs(context) ?: return true
            val license = getLicense(context) ?: return true
            if (sharedPreferences.getBoolean(KEY_PERMANENT_EXPIRED, false)) return true
            license.expiryDate <= getCurrentTimeSafe(context)
        } catch (_: Exception) {
            true
        }
    }

    private fun verifyLicenseWithResult(context: Context, license: LicenseInfo): VerificationResult {
        return try {
            val currentDeviceId = SecurityUtils.getDeviceId(context).trim()
            val cleanLicenseDeviceId = normalizeBase64(license.deviceId.trim())
            val cleanLicenseUser = license.user.trim()

            val dataToVerify = "$cleanLicenseUser|$cleanLicenseDeviceId|${license.creationDate}|${license.expiryDate}"

            val verificationKey = if (!license.issuerKey.isNullOrBlank()) license.issuerKey else getPublicKey()

            val isSignatureValid = SecurityUtils.verifySignature(dataToVerify, license.signature, verificationKey)

            if (!isSignatureValid) {
                return VerificationResult.Failure("Firma Inválida", "La firma digital RSA-4096 no pudo ser verificada.")
            }

            val legacyDeviceId = SecurityUtils.getLegacyDeviceId(context).trim()
            val legacyDeviceIdHash = SecurityUtils.getDeviceIdFromLegacy(legacyDeviceId).trim()

            val normalizedCurrentDeviceId = normalizeBase64(currentDeviceId)
            val normalizedLegacyHash = normalizeBase64(legacyDeviceIdHash)

            val isDeviceValid = cleanLicenseDeviceId.equals(normalizedCurrentDeviceId, ignoreCase = true) ||
                    cleanLicenseDeviceId.equals(normalizedLegacyHash, ignoreCase = true) ||
                    license.deviceId.trim().equals(currentDeviceId, ignoreCase = true)

            if (!isDeviceValid) {
                return VerificationResult.Failure("ID de Dispositivo Erróneo")
            }

            VerificationResult.Success
        } catch (_: Exception) {
            VerificationResult.Failure("Error de Verificación")
        }
    }

    private fun normalizeBase64(input: String): String {
        return try {
            val standard = input.replace('-', '+').replace('_', '/').trim()
            val withoutPadding = standard.trimEnd('=')
            val padding = (4 - withoutPadding.length % 4) % 4
            if (padding > 0) withoutPadding.padEnd(withoutPadding.length + padding, '=') else withoutPadding
        } catch (_: Exception) {
            input
        }
    }

    fun isLicenseViolation(context: Context): Boolean {
        return try {
            val license = getLicense(context) ?: return false
            val currentTime = getCurrentTimeSafe(context)
            val sharedPreferences = getEncryptedPrefs(context)
            val lastUsage = sharedPreferences?.getLong(KEY_LAST_USAGE, 0L) ?: 0L

            if (lastUsage > 0 && currentTime < lastUsage - 300000) {
                return true
            }

            if (currentTime < license.creationDate - 300000) {
                return true
            }

            false
        } catch (_: Exception) {
            false
        }
    }

    private fun markSignatureAsConsumed(context: Context, license: LicenseInfo, reason: String) {
        try {
            val prefs = getEncryptedPrefs(context)
            val vault = getVaultPrefs(context)

            val record = ConsumedLicense(license.signature, license.user, license.expiryDate, getCurrentTimeSafe(context), reason)
            val recordJson = gson.toJson(record)

            val set1 = prefs?.getStringSet(KEY_CONSUMED_SIGNATURES, emptySet())?.toMutableSet() ?: mutableSetOf()
            set1.add(recordJson)
            prefs?.edit()?.putStringSet(KEY_CONSUMED_SIGNATURES, set1)?.apply()

            val set2 = vault?.getStringSet(KEY_CONSUMED_SIGNATURES, emptySet())?.toMutableSet() ?: mutableSetOf()
            set2.add(recordJson)
            vault?.edit()?.putStringSet(KEY_CONSUMED_SIGNATURES, set2)?.apply()

            consumedCache.set(null)
        } catch (_: Exception) { }
    }

    private fun consumeIfExpired(context: Context) {
        try {
            if (!isLicenseExpired(context)) return
            val license = getLicense(context) ?: return
            markSignatureAsConsumed(context, license, "Expiración Normal")
            getEncryptedPrefs(context)?.edit()?.putBoolean(KEY_PERMANENT_EXPIRED, true)?.apply()
        } catch (_: Exception) { }
    }

    fun getCurrentTimeSafe(context: Context): Long {
        return if (NetworkTimeProvider.isSynchronized()) NetworkTimeProvider.getSyncedTime() else System.currentTimeMillis()
    }

    fun getLicense(context: Context): LicenseInfo? {
        return try {
            val json = getEncryptedPrefs(context)?.getString(KEY_LICENSE, null)
            json?.let { gson.fromJson(it, LicenseInfo::class.java) }
        } catch (_: Exception) {
            null
        }
    }

    fun saveLicense(context: Context, licenseJson: String): ActivationResult {
        return try {
            if (licenseJson.length > 10000) return ActivationResult.Failure("Input demasiado grande")

            val jsonStart = licenseJson.indexOf("{")
            val jsonEnd = licenseJson.lastIndexOf("}")
            if ((jsonStart == -1) || (jsonEnd == -1)) return ActivationResult.Failure("Formato inválido")

            val cleanJson = licenseJson.substring(jsonStart, jsonEnd + 1)
            if (cleanJson.length > 10000) return ActivationResult.Failure("Input demasiado grande")

            val license = gson.fromJson(cleanJson, LicenseInfo::class.java) ?: return ActivationResult.Failure("Error de lectura")

            if (license.user.length > 500 || license.signature.length > 2000) {
                return ActivationResult.Failure("Campos con valores inválidos")
            }

            val sharedPreferences = getEncryptedPrefs(context) ?: return ActivationResult.Failure("Error de almacenamiento seguro")
            val consumed = getConsumedLicenses(context)
            if (consumed.any { it.signature == license.signature }) {
                return ActivationResult.Failure("Licencia Consumida")
            }

            val verification = verifyLicenseWithResult(context, license)
            if (verification is VerificationResult.Success) {
                val currentTime = getCurrentTimeSafe(context)
                if (license.expiryDate + 300000 <= currentTime) {
                    return ActivationResult.Failure("Licencia Expirada")
                }

                if (currentTime < license.creationDate - 300000) {
                    return ActivationResult.Failure("Reloj Desincronizado", "La fecha de este dispositivo es anterior a la creación de la licencia. Ajuste su reloj.")
                }

                sharedPreferences.edit()
                    .putString(KEY_LICENSE, cleanJson)
                    .putLong(KEY_LAST_USAGE, currentTime)
                    .putBoolean(KEY_PERMANENT_EXPIRED, false)
                    .putBoolean(KEY_PROFESSIONAL_LICENSE_ACTIVATED, true)
                    .apply()

                // When a valid license is activated, consume trial permanently and log to history
                consumeTrialPermanentlyOnLicenseActivation(context)

                consumedCache.set(null)
                ActivationResult.Success
            } else {
                val failure = verification as VerificationResult.Failure
                ActivationResult.Failure(failure.reason, failure.detail)
            }
        } catch (e: Exception) {
            ActivationResult.Failure("Error Interno", e.message)
        }
    }

    private fun consumeTrialPermanentlyOnLicenseActivation(context: Context) {
        try {
            val vault = getTrialVaultPrefs(context) ?: return
            val wasActive = vault.getBoolean(KEY_TRIAL_ACTIVE, false)
            val startTime = vault.getLong(KEY_TRIAL_START, 0L)

            // Persist professional license flag
            val consumedVault = getVaultPrefs(context)
            consumedVault?.edit()?.apply {
                putBoolean(KEY_PROFESSIONAL_LICENSE_ACTIVATED, true)
                putBoolean(KEY_TRIAL_USED, true) // SELLO DE SEGURIDAD: Bloquear prueba permanentemente
            }?.apply()
            
            vault.edit().apply {
                putBoolean(KEY_PROFESSIONAL_LICENSE_ACTIVATED, true)
                putBoolean(KEY_TRIAL_USED, true) // SELLO DE SEGURIDAD: Bloquear prueba permanentemente
            }.apply()

            // Log trial record to history ONLY if it was genuinely active
            val trialAlreadyInHistory = getConsumedLicenses(context).any { it.signature == TRIAL_SIGNATURE }
            if (wasActive && !trialAlreadyInHistory) {
                val record = ConsumedLicense(
                    signature = TRIAL_SIGNATURE,
                    user = "PRUEBA GRATIS - 7 días",
                    expiryDate = if (startTime > 0) startTime + TRIAL_DURATION_MS else getCurrentTimeSafe(context),
                    consumptionDate = if (startTime > 0) startTime else getCurrentTimeSafe(context),
                    reason = "Consumida al activar licencia profesional"
                )
                val recordJson = gson.toJson(record)

                val set = vault.getStringSet(KEY_CONSUMED_SIGNATURES, emptySet())?.toMutableSet() ?: mutableSetOf()
                set.add(recordJson)
                vault.edit().putStringSet(KEY_CONSUMED_SIGNATURES, set).apply()

                val set2 = consumedVault?.getStringSet(KEY_CONSUMED_SIGNATURES, emptySet())?.toMutableSet() ?: mutableSetOf()
                set2.add(recordJson)
                consumedVault?.edit()?.putStringSet(KEY_CONSUMED_SIGNATURES, set2)?.apply()
            }

            // Refresh storage integrity
            TrialProtectionManager.refreshStorageIntegrity(context)
            consumedCache.set(null)
        } catch (_: Exception) { }
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences? {
        return try {
            SecureStorageUtils.getEncryptedPrefs(context, PREFS_NAME)
        } catch (e: Exception) {
            Log.e(TAG_LICENSING, "Error al obtener preferencias cifradas", e)
            null
        }
    }

    private fun getVaultPrefs(context: Context): SharedPreferences? {
        return try {
            SecureStorageUtils.getEncryptedPrefs(context, CONSUMED_VAULT_NAME)
        } catch (_: Exception) { null }
    }

    private fun getTrialVaultPrefs(context: Context): SharedPreferences? {
        return try {
            SecureStorageUtils.getEncryptedPrefs(context, TRIAL_VAULT_NAME)
        } catch (_: Exception) { null }
    }

    fun syncTrialVault(context: Context) {
        try {
            val vault = getTrialVaultPrefs(context) ?: return
            val consumedVault = getVaultPrefs(context) ?: return

            val markerStartTime = DeviceTrialMarker.getTrialStartTimeFromMarker(context)
            val consumedStartTime = consumedVault.getLong(KEY_TRIAL_START, 0L)
            val trialStartTime = vault.getLong(KEY_TRIAL_START, 0L)
            
            val earliestStartTime = listOf(markerStartTime, consumedStartTime, trialStartTime)
                .filter { it > 0 }.minOrNull() ?: 0L

            val hasMarker = DeviceTrialMarker.hasTrialMarker(context)
            val consumedUsed = consumedVault.getBoolean(KEY_TRIAL_USED, false)
            val trialUsed = vault.getBoolean(KEY_TRIAL_USED, false)
            
            val anyUsed = hasMarker || consumedUsed || trialUsed

            if (anyUsed) {
                val consumedActive = consumedVault.getBoolean(KEY_TRIAL_ACTIVE, false)
                val trialActive = vault.getBoolean(KEY_TRIAL_ACTIVE, false)
                
                // Si el tiempo de expiración ya pasó según el earliestStartTime, no puede estar activo
                val currentTime = getCurrentTimeSafe(context)
                val isExpiredByTime = earliestStartTime > 0 && currentTime >= earliestStartTime + TRIAL_DURATION_MS
                
                // MEJORA: Si sabemos que se usó pero no tenemos estado local (reinstalación),
                // verificamos si debería estar activo basado en el tiempo y el historial.
                val wasActiveKnown = consumedActive || trialActive
                val history = getConsumedLicenses(context)
                val alreadyInHistory = history.any { it.signature == TRIAL_SIGNATURE }
                
                val shouldBeActive = if (!isExpiredByTime && !alreadyInHistory) {
                    // Si no ha expirado y no está en el historial de consumidos,
                    // y sabemos que se usó alguna vez (anyUsed), lo rehabilitamos.
                    true 
                } else {
                    wasActiveKnown && !isExpiredByTime
                }

                vault.edit().apply {
                    putBoolean(KEY_TRIAL_USED, true)
                    putBoolean(KEY_TRIAL_ACTIVE, shouldBeActive)
                    if (earliestStartTime > 0) putLong(KEY_TRIAL_START, earliestStartTime)
                }.apply()

                consumedVault.edit().apply {
                    putBoolean(KEY_TRIAL_USED, true)
                    putBoolean(KEY_TRIAL_ACTIVE, shouldBeActive)
                    if (earliestStartTime > 0) putLong(KEY_TRIAL_START, earliestStartTime)
                }.apply()
                
                // Si el marker no existe pero sabemos que se usó, restaurarlo
                if (!hasMarker && earliestStartTime > 0) {
                    DeviceTrialMarker.storeTrialMarker(context, earliestStartTime)
                }

                // ASEGURAR REGISTRO EN HISTORIAL SI EXPIRO
                if (isExpiredByTime) {
                    ensureTrialInHistory(context, earliestStartTime, "Expiración detectada en sincronización")
                }
            }
        } catch (_: Exception) { }
    }

    private fun ensureTrialInHistory(context: Context, startTime: Long, reason: String) {
        try {
            val history = getConsumedLicenses(context)
            if (history.none { it.signature == TRIAL_SIGNATURE }) {
                val vault = getTrialVaultPrefs(context) ?: return
                val consumedVault = getVaultPrefs(context) ?: return
                
                val record = ConsumedLicense(
                    signature = TRIAL_SIGNATURE,
                    user = "PRUEBA GRATIS - 7 días",
                    expiryDate = startTime + TRIAL_DURATION_MS,
                    consumptionDate = if (startTime > 0) startTime else getCurrentTimeSafe(context),
                    reason = reason
                )
                val recordJson = gson.toJson(record)

                val set = vault.getStringSet(KEY_CONSUMED_SIGNATURES, emptySet())?.toMutableSet() ?: mutableSetOf()
                set.add(recordJson)
                vault.edit().putStringSet(KEY_CONSUMED_SIGNATURES, set).apply()

                val set2 = consumedVault.getStringSet(KEY_CONSUMED_SIGNATURES, emptySet())?.toMutableSet() ?: mutableSetOf()
                set2.add(recordJson)
                consumedVault.edit().putStringSet(KEY_CONSUMED_SIGNATURES, set2).apply()
                
                consumedCache.set(null)
            }
        } catch (_: Exception) { }
    }

    // Public wrapper for clearProfessionalLicense
    fun clearLicense(context: Context): Boolean {
        return removeProfessionalLicense(context)
    }

    // Check if clock was manipulated (backwards)
    fun isClockManipulated(context: Context): Boolean {
        return isLicenseViolation(context)
    }

    // Check if recent sync is available (within 7 days)
    fun isRecentSyncAvailable(context: Context): Boolean {
        return try {
            val prefs = getEncryptedPrefs(context) ?: return false
            val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
            if (lastSync == 0L) return false
            getCurrentTimeSafe(context) - lastSync < 7 * 24 * 60 * 60 * 1000L
        } catch (_: Exception) { false }
    }

    // Update last sync time
    fun updateLastSyncTime(context: Context) {
        try {
            getEncryptedPrefs(context)?.edit()?.putLong(KEY_LAST_SYNC, getCurrentTimeSafe(context))?.apply()
        } catch (_: Exception) { }
    }

    // Check if trial was violated (clock manipulation or security)
    fun isTrialViolation(context: Context): Boolean {
        return isLicenseViolation(context)
    }

    // Remove a specific consumed license record by signature
    fun removeConsumedLicense(context: Context, signature: String): Boolean {
        return try {
            val prefs = getEncryptedPrefs(context)
            val vault = getVaultPrefs(context)

            val trimmedSig = signature.trim()

            var removed = false

            val set1 = prefs?.getStringSet(KEY_CONSUMED_SIGNATURES, emptySet())?.toMutableSet() ?: mutableSetOf()
            val before1 = set1.size
            set1.removeAll { json ->
                try {
                    val record = gson.fromJson(json, ConsumedLicense::class.java)
                    record.signature.trim() == trimmedSig
                } catch (_: Exception) {
                    json.contains(trimmedSig)
                }
            }
            if (set1.size != before1) {
                prefs?.edit()?.putStringSet(KEY_CONSUMED_SIGNATURES, set1)?.commit()
                removed = true
            }

            val set2 = vault?.getStringSet(KEY_CONSUMED_SIGNATURES, emptySet())?.toMutableSet() ?: mutableSetOf()
            val before2 = set2.size
            set2.removeAll { json ->
                try {
                    val record = gson.fromJson(json, ConsumedLicense::class.java)
                    record.signature.trim() == trimmedSig
                } catch (_: Exception) {
                    json.contains(trimmedSig)
                }
            }
            if (set2.size != before2) {
                vault?.edit()?.putStringSet(KEY_CONSUMED_SIGNATURES, set2)?.commit()
                removed = true
            }

            consumedCache.set(null)
            removed
        } catch (_: Exception) { false }
    }

    // Debug function to reset trial state (Soft reset - doesn't touch Device Marker)
    fun softResetTrial(context: Context): Boolean {
        return try {
            val mainPrefs = getEncryptedPrefs(context)
            val vault = getTrialVaultPrefs(context)
            val permanentVault = getVaultPrefs(context)

            mainPrefs?.edit()?.apply {
                remove(KEY_PROFESSIONAL_LICENSE_ACTIVATED)
                remove(KEY_LAST_USAGE)
                remove(KEY_PERMANENT_EXPIRED)
            }?.apply()

            vault?.edit()?.apply {
                remove(KEY_TRIAL_USED)
                remove(KEY_TRIAL_START)
                remove(KEY_TRIAL_ACTIVE)
                remove(KEY_TRIAL_LAST_VALIDATION)
                remove(KEY_PROFESSIONAL_LICENSE_ACTIVATED)
            }?.apply()

            permanentVault?.edit()?.apply {
                remove(KEY_TRIAL_USED)
                remove(KEY_TRIAL_START)
                remove(KEY_TRIAL_ACTIVE)
                remove(KEY_PROFESSIONAL_LICENSE_ACTIVATED)
            }?.apply()

            // NOTA: No borramos DeviceTrialMarker.removeTrialMarker(context)
            // Esto asegura que la App no pueda auto-limpiarse para re-usar la prueba.

            removeConsumedLicense(context, TRIAL_SIGNATURE)

            consumedCache.set(null)
            TrialProtectionManager.refreshStorageIntegrity(context)
            true
        } catch (e: Exception) {
            Log.e(TAG_LICENSING, "Error soft resetting trial", e)
            false
        }
    }

    // Public function to reset trial state (Safely)
    fun resetTrial(context: Context): Boolean {
        return softResetTrial(context)
    }

    // Full reset: clears ALL trial state including DeviceTrialMarker, allowing re-activation
    fun fullResetTrial(context: Context): Boolean {
        return try {
            softResetTrial(context)
            DeviceTrialMarker.removeTrialMarker(context)
            consumedCache.set(null)
            true
        } catch (e: Exception) {
            Log.e(TAG_LICENSING, "Error during full trial reset", e)
            false
        }
    }
}

private const val KEY_PROFESSIONAL_LICENSE_ACTIVATED = "professional_license_ever_activated"