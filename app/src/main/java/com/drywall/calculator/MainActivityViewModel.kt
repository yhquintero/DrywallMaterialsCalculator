package com.drywall.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.AppConfig
import com.drywall.calculator.data.repository.AppConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import com.drywall.calculator.utils.security.LicensingManager
import com.drywall.common.security.TrialProtectionManager
import android.content.Context
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val repository: AppConfigRepository,
    private val unitTypeRepository: com.drywall.calculator.data.repository.UnitTypeRepository
) : ViewModel() {

    private val _isShortcutDialogOpen = MutableStateFlow(value = false)
    val isShortcutDialogOpen = _isShortcutDialogOpen.asStateFlow()

    /**
     * Cambia el estado de visibilidad del diálogo de accesos directos.
     *
     * @param open Verdadero para mostrar el diálogo, falso para ocultarlo.
     */
    fun setShortcutDialogOpen(open: Boolean) {
        _isShortcutDialogOpen.value = open
    }

    val config = repository.getConfig().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppConfig()
    )

    val customUnitTypes = unitTypeRepository.getAllUnitTypes().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Agrega un nuevo tipo de unidad personalizada.
     *
     * @param name Nombre de la unidad a agregar.
     */
    fun addCustomUnit(name: String) {
        viewModelScope.launch {
            unitTypeRepository.insertUnitType(com.drywall.calculator.data.local.entity.UnitType(name))
        }
    }

    /**
     * Elimina un tipo de unidad personalizada existente.
     *
     * @param unit El objeto UnitType que se desea eliminar.
     */
    fun deleteCustomUnit(unit: com.drywall.calculator.data.local.entity.UnitType) {
        viewModelScope.launch {
            unitTypeRepository.deleteUnitType(unit)
        }
    }

    private val _licenseValid = MutableStateFlow<Boolean?>(null)
    val licenseValid = _licenseValid.asStateFlow()

    private val _licenseViolation = MutableStateFlow(false)
    val licenseViolation = _licenseViolation.asStateFlow()

    private val _isTrialAvailable = MutableStateFlow(false)
    val isTrialAvailable = _isTrialAvailable.asStateFlow()

    private val _isTrialAlreadyUsed = MutableStateFlow(false)
    val isTrialAlreadyUsed = _isTrialAlreadyUsed.asStateFlow()

    private val _isTrialActive = MutableStateFlow(false)
    val isTrialActive = _isTrialActive.asStateFlow()

    private val _trialRemainingMs = MutableStateFlow(0L)
    val trialRemainingMs = _trialRemainingMs.asStateFlow()

    private val _isPermanentlyLocked = MutableStateFlow(false)
    val isPermanentlyLocked = _isPermanentlyLocked.asStateFlow()

    private val _lockReason = MutableStateFlow<String?>(null)
    val lockReason = _lockReason.asStateFlow()

    /**
     * Realiza la verificación de la licencia y el estado del período de prueba.
     * Sincroniza la bóveda de pruebas y verifica posibles violaciones de seguridad.
     *
     * @param context Contexto de la aplicación para acceder a recursos y preferencias.
     */
    fun checkLicense(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // Security gate: check device integrity first
            val protection = TrialProtectionManager.getProtectionStatus(context)
            if (protection.isRooted || protection.deviceCompromised) {
                _isPermanentlyLocked.value = true
                _lockReason.value = "Dispositivo comprometido: ${if (protection.isRooted) "Root detectado" else "Integridad alterada"}"
                _licenseValid.value = false
                return@launch
            }
            if (protection.emulatorDetected) {
                _isPermanentlyLocked.value = true
                _lockReason.value = "Emulador detectado"
                _licenseValid.value = false
                return@launch
            }

            if (TrialProtectionManager.isPermanentlyLocked(context)) {
                _isPermanentlyLocked.value = true
                _lockReason.value = TrialProtectionManager.getLockReason(context)
                _licenseValid.value = false
                return@launch
            }

            LicensingManager.syncTrialVault(context)

            val isTrialViolation = LicensingManager.isTrialViolation(context)
            val isViolation = LicensingManager.isLicenseViolation(context)

            if (isViolation || isTrialViolation) {
                _licenseViolation.value = true
                _licenseValid.value = false
            } else {
                val isValid = LicensingManager.isLicenseValid(context)
                val trialActive = LicensingManager.isTrialActive(context)
                val trialUsed = LicensingManager.hasTrialEverBeenUsed(context)
                
                // Una prueba está "disponible" solo si nunca se ha usado y el dispositivo no está bloqueado
                val trialAvailable = !trialUsed && !isValid

                // Una prueba se considera "ya usada" si se usó en el pasado, no está activa ahora y no hay licencia válida
                val trialAlreadyUsed = trialUsed && !trialActive && !isValid

                _licenseValid.value = isValid || trialActive
                _isTrialAvailable.value = trialAvailable
                _isTrialAlreadyUsed.value = trialAlreadyUsed
                _isTrialActive.value = trialActive

                if (trialActive) {
                    _trialRemainingMs.value = LicensingManager.getTrialRemainingTime(context)
                }
            }
        }
    }

    /**
     * Activa el período de prueba para el dispositivo actual.
     *
     * @param context Contexto de la aplicación.
     */
    fun activateTrial(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = LicensingManager.activateTrial(context)
            if (success) {
                _isTrialAvailable.value = false
                _isTrialAlreadyUsed.value = false
                _isTrialActive.value = true
                _licenseValid.value = true
                _trialRemainingMs.value = LicensingManager.getTrialRemainingTime(context)
            }
        }
    }

    /**
     * Refresca el estado actual del período de prueba.
     * Si la prueba ha expirado, vuelve a verificar la validez de la licencia profesional.
     *
     * @param context Contexto de la aplicación.
     */
    fun refreshTrialState(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val trialActive = LicensingManager.isTrialActive(context)
            _isTrialActive.value = trialActive
            if (trialActive) {
                _trialRemainingMs.value = LicensingManager.getTrialRemainingTime(context)
            } else {
                // Trial expired, recheck license
                val isValid = LicensingManager.isLicenseValid(context)
                _licenseValid.value = isValid
                _trialRemainingMs.value = 0L
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Check if we need to force update decimal precision to 2 (User request)
            // We use first() to avoid an infinite loop if collect is used inside saveConfig
            repository.getConfig().first()?.let { current ->
                if (current.decimalPrecision == 4) {
                    repository.saveConfig(current.copy(decimalPrecision = 2))
                }
            }
        }
    }

    /**
     * Actualiza la lista de rutas de accesos directos seleccionados por el usuario.
     *
     * @param shortcuts Lista de strings con las rutas de los accesos directos.
     */
    fun updateShortcuts(shortcuts: List<String>) {
        viewModelScope.launch {
            val current = config.value ?: AppConfig()
            repository.saveConfig(current.copy(selectedShortcuts = shortcuts))
        }
    }

    /**
     * Alterna entre el modo oscuro, modo claro o seguimiento del sistema.
     *
     * @param isDark Verdadero para modo oscuro, falso para claro, null para seguir el sistema.
     */
    fun toggleDarkMode(isDark: Boolean?) {
        viewModelScope.launch {
            val current = config.value ?: AppConfig()
            repository.saveConfig(current.copy(isDarkMode = isDark))
        }
    }

    /**
     * Actualiza la precisión decimal utilizada para los cálculos en toda la aplicación.
     *
     * @param precision Número de decimales a mostrar (ej. 2 o 4).
     */
    fun updateDecimalPrecision(precision: Int) {
        viewModelScope.launch {
            val current = config.value ?: AppConfig()
            repository.saveConfig(current.copy(decimalPrecision = precision))
        }
    }
}
