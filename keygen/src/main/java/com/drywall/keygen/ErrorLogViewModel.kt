package com.drywall.keygen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.common.utils.ErrorTracker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ErrorLogViewModel : ViewModel() {

    private val _logs = MutableStateFlow<String>("")
    val logs = _logs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private val _diagnosticFile = MutableStateFlow<File?>(null)
    val diagnosticFile = _diagnosticFile.asStateFlow()

    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
    }

    fun loadExistingLogs(context: Context) {
        _logs.value = ErrorTracker.getAllLogs(context)
    }

    fun runFullSystemScan(context: Context) {
        viewModelScope.launch {
            _isScanning.value = true
            val report = StringBuilder()
            report.append("=== INFORME DE DIAGNÓSTICO INTEGRAL KEYGEN PRO ===\n")
            report.append("Generado: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            report.append("App: Keygen Pro v${BuildConfig.VERSION_NAME}\n\n")

            // 1. Verificar Logs de Crash previos
            if (ErrorTracker.hasLogs(context)) {
                report.append("--- LOGS DE ERRORES DETECTADOS ---\n")
                report.append(ErrorTracker.getAllLogs(context))
                report.append("\n")
            } else {
                report.append("[OK] No se encontraron logs de errores previos en disco.\n\n")
            }

            // 2. Info de Sistema
            report.append("--- INFORMACIÓN DE DISPOSITIVO ---\n")
            report.append("Modelo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
            report.append("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
            report.append("Kernel: ${System.getProperty("os.version")}\n")
            report.append("====================================================\n")

            val finalReport = report.toString()
            _logs.value = finalReport

            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                val fileName = "KEYGEN_SCAN_$timestamp.log"
                
                val file = File(context.cacheDir, fileName)
                file.writeText(finalReport)
                _diagnosticFile.value = file.canonicalFile
                
                _eventFlow.emit(UiEvent.ShowSnackbar("Escaneo finalizado. Informe listo para exportar."))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowSnackbar("Fallo al generar archivo .log: ${e.message}"))
            }
            
            _isScanning.value = false
        }
    }

    fun clearLogs(context: Context) {
        ErrorTracker.clearLogs(context)
        _logs.value = ""
        _diagnosticFile.value = null
        viewModelScope.launch {
            _eventFlow.emit(UiEvent.ShowSnackbar("Historial de logs limpiado."))
        }
    }
}
