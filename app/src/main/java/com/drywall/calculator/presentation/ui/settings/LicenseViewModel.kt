package com.drywall.calculator.presentation.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.utils.PdfGenerator
import com.drywall.calculator.utils.security.ActivationResult
import com.drywall.calculator.utils.security.ConsumedLicense
import com.drywall.calculator.utils.security.LicenseInfo
import com.drywall.calculator.utils.security.LicensingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LicenseViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val pdfGenerator: PdfGenerator
) : ViewModel() {

    private val _licenseState = MutableStateFlow<LicenseState>(LicenseState.Loading)
    val licenseState: StateFlow<LicenseState> = _licenseState

    private val _consumedHistory = MutableStateFlow<List<ConsumedLicense>>(emptyList())
    val consumedHistory: StateFlow<List<ConsumedLicense>> = _consumedHistory

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _isTrialActive = MutableStateFlow(false)
    val isTrialActive: StateFlow<Boolean> = _isTrialActive

    private val _trialRemainingMs = MutableStateFlow(0L)
    val trialRemainingMs: StateFlow<Long> = _trialRemainingMs

    init {
        loadLicense()
    }

    fun loadLicense() {
        viewModelScope.launch {
            // Sync trial vaults
            LicensingManager.syncTrialVault(context)

            val license = LicensingManager.getLicense(context)
            val isValid = LicensingManager.isLicenseValid(context)
            val trialActive = LicensingManager.isTrialActive(context)

            _licenseState.value = if (license != null) {
                LicenseState.Loaded(license, isValid)
            } else {
                LicenseState.Empty
            }
            _isTrialActive.value = trialActive
            if (trialActive) {
                _trialRemainingMs.value = LicensingManager.getTrialRemainingTime(context)
            }

            // Refresh consumed history AFTER trial check (isTrialActive may consume expired trial)
            val history = LicensingManager.getConsumedLicenses(context).toMutableList()

            // Si el trial se usó pero NO está en el historial persistente, agregarlo virtualmente
            // Esto ayuda si por alguna razón el SharedPreferences se borró pero el DeviceMarker persistió
            val trialUsed = LicensingManager.hasTrialEverBeenUsed(context)
            val trialAlreadyInHistory = history.any { it.signature == LicensingManager.TRIAL_SIGNATURE }
            
            if (trialUsed && !trialAlreadyInHistory) {
                val trialStart = LicensingManager.getTrialStartTime(context)
                if (trialStart > 0) {
                    val trialEnd = trialStart + LicensingManager.TRIAL_DURATION_MS
                    val isNowActive = trialActive
                    val virtualRecord = ConsumedLicense(
                        signature = LicensingManager.TRIAL_SIGNATURE,
                        user = "PRUEBA GRATIS - 7 días",
                        expiryDate = trialEnd,
                        consumptionDate = trialStart, // Usamos la fecha de inicio como fecha de uso
                        reason = if (isNowActive) "Activa" else "Expirada"
                    )
                    history.add(virtualRecord)
                }
            } else if (trialAlreadyInHistory && trialActive) {
                // Si ya está en el historial pero detectamos que está activa, actualizar el "reason" visualmente
                val index = history.indexOfFirst { it.signature == LicensingManager.TRIAL_SIGNATURE }
                if (index != -1) {
                    val old = history[index]
                    history[index] = old.copy(reason = "Activa")
                }
            }

            // If there's a current license that's expired but not yet in consumed, add it
            if (license != null && !isValid) {
                val alreadyInHistory = history.any { it.signature == license.signature }
                if (!alreadyInHistory) {
                    val now = LicensingManager.getCurrentTimeSafe(context)
                    val expiredRecord = ConsumedLicense(
                        signature = license.signature,
                        user = license.user,
                        expiryDate = license.expiryDate,
                        consumptionDate = now,
                        reason = "Expirada"
                    )
                    history.add(0, expiredRecord)
                }
            }

            _consumedHistory.value = history.sortedByDescending { it.consumptionDate }
        }
    }

    fun removeLicense() {
        viewModelScope.launch {
            val success = LicensingManager.clearLicense(context)
            if (success) {
                _licenseState.value = LicenseState.Empty
                showMessage("Licencia eliminada correctamente")
            } else {
                showMessage("Error al eliminar la licencia")
            }
        }
    }

    fun deleteHistoryRecord(signature: String) {
        viewModelScope.launch {
            val success = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                LicensingManager.removeConsumedLicense(context, signature)
            }
            if (success) {
                _consumedHistory.value = LicensingManager.getConsumedLicenses(context)
                showMessage("Registro eliminado")
            } else {
                showMessage("Error al eliminar registro")
            }
        }
    }

    fun saveNewLicense(licenseJson: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (val result = LicensingManager.saveLicense(context, licenseJson)) {
                is ActivationResult.Success -> {
                    loadLicense()
                    showMessage("Licencia guardada correctamente")
                    onSuccess()
                }
                is ActivationResult.Failure -> {
                    showMessage(result.message)
                }
            }
        }
    }

    fun exportHistoryToPdf(onResult: (java.io.File?) -> Unit) {
        viewModelScope.launch {
            try {
                val profile = db.companyDao().getCompanyProfile().first()
                val companyName = profile?.businessName ?: "Calculadora Drywall"
                val logoPath = profile?.logoUri

                val currentLicense = LicensingManager.getLicense(context)
                val isValid = LicensingManager.isLicenseValid(context)
                val history = _consumedHistory.value

                val file = pdfGenerator.generateLicenseHistoryReport(
                    context, companyName, logoPath, currentLicense, isValid, history
                )
                onResult(file)
            } catch (e: Exception) {
                android.util.Log.e("LicenseVM", "Error exporting history", e)
                onResult(null)
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun resetTrial() {
        viewModelScope.launch {
            val success = LicensingManager.resetTrial(context)
            if (success) {
                loadLicense()
                showMessage("Prueba reseteada. Ya puede activarla de nuevo.")
            } else {
                showMessage("Error al resetear la prueba")
            }
        }
    }

    private fun showMessage(msg: String) {
        _message.value = msg
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            clearMessage()
        }
    }
}

sealed class LicenseState {
    object Loading : LicenseState()
    object Empty : LicenseState()
    data class Loaded(val license: LicenseInfo, val isValid: Boolean) : LicenseState()
}