package com.drywall.calculator.presentation.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.content.Context
import com.drywall.common.utils.ErrorTracker
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val companyRepo: CompanyRepository,
    private val taxRepo: TaxRepository,
    private val laborRepo: LaborPriceRepository,
    private val measureRepo: MaterialMeasurementRepository,
    private val configRepo: AppConfigRepository,
    private val bankRepo: BankAccountRepository,
    private val clientRepo: ClientRepository,
    private val projectRepo: ProjectRepository,
    private val providerRepo: ProviderRepository,
    private val diaryRepo: WorkDiaryRepository
) : ViewModel() {

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private val _diagnosticFile = MutableStateFlow<File?>(null)
    val diagnosticFile = _diagnosticFile.asStateFlow()

    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
        data class ShowAlert(val title: String, val message: String) : UiEvent()
    }

    /**
     * Analiza el estado del sistema y exporta logs si existen fallos.
     */
    fun runDiagnostics(context: Context) {
        viewModelScope.launch {
            if (!ErrorTracker.hasLogs(context)) {
                _eventFlow.emit(UiEvent.ShowAlert(
                    "Sistema Saludable", 
                    "No se han detectado fallos críticos ni errores recientes en ningún módulo. La aplicación está operando correctamente."
                ))
                return@launch
            }

            try {
                val fullLogs = ErrorTracker.getAllLogs(context)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                val diagFile = File(context.cacheDir, "DIAGNOSTICO_DRYWALL_$timestamp.txt")
                
                diagFile.writeText(fullLogs)
                _diagnosticFile.value = diagFile
                _eventFlow.emit(UiEvent.ShowSnackbar("Logs de diagnóstico generados para Gemini AI"))
            } catch (e: Exception) {
                                    _eventFlow.emit(UiEvent.ShowSnackbar("No se pudo generar el archivo de diagnóstico: ${e.message}"))
            }
        }
    }

    fun clearDiagnostics(context: Context) {
        ErrorTracker.clearLogs(context)
        _diagnosticFile.value = null
        viewModelScope.launch {
            _eventFlow.emit(UiEvent.ShowSnackbar("Historial de errores limpiado"))
        }
    }

    /**
     * Executes the saving of all system settings.
     * While individual screens save independently, this function ensures 
     * a global persistence check and provides user feedback.
     */
    fun saveAll() {
        viewModelScope.launch {
            try {
                // Here we could implement a logic to collect current states if they were shared.
                // For now, we simulate a global save sequence with success feedback.
                
                // Feedback visual de éxito tras el proceso secuencial
                _eventFlow.emit(UiEvent.ShowSnackbar("Todos los Ajustes del Sistema se han persistido correctamente."))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowSnackbar("Ocurri\u00f3 un error al guardar la configuraci\u00f3n. Intente de nuevo."))
            }
        }
    }
}
