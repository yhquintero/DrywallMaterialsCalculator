package com.drywall.calculator.presentation.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.repository.*
import com.drywall.common.utils.ErrorTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ErrorLogViewModel @Inject constructor(
    private val companyRepo: CompanyRepository,
    private val clientRepo: ClientRepository,
    private val projectRepo: ProjectRepository,
    private val materialRepo: MaterialRepository,
    private val laborRepo: LaborPriceRepository,
    private val configRepo: AppConfigRepository,
    private val providerRepo: ProviderRepository,
    private val diaryRepo: WorkDiaryRepository,
    private val bankRepo: BankAccountRepository,
    private val taxRepo: TaxRepository,
    private val measureRepo: MaterialMeasurementRepository
) : ViewModel() {

    private val _logs = MutableStateFlow<String>("")
    val logs = _logs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private val _diagnosticFile = MutableStateFlow<File?>(null)
    val diagnosticFile = _diagnosticFile.asStateFlow()

    sealed class UiEvent {
        data class ShowAlert(val title: String, val message: String) : UiEvent()
        data class ShowSnackbar(val message: String) : UiEvent()
    }

    fun loadExistingLogs(context: Context) {
        _logs.value = ErrorTracker.getAllLogs(context)
    }

    fun runFullSystemScan(context: Context) {
        viewModelScope.launch {
            _isScanning.value = true
            val report = StringBuilder()
            report.append("=== INFORME DE DIAGNÓSTICO INTEGRAL PARA GEMINI AI ===\n")
            report.append("Generado: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            report.append("App: Calculadora Drywall v${com.drywall.calculator.BuildConfig.VERSION_NAME}\n\n")

            // 1. Verificar Logs de Crash previos
            if (ErrorTracker.hasLogs(context)) {
                report.append("--- LOGS DE ERRORES DETECTADOS ---\n")
                report.append(ErrorTracker.getAllLogs(context))
                report.append("\n")
            } else {
                report.append("[OK] No se encontraron logs de errores previos en disco.\n\n")
            }

            // 2. Escaneo de Módulos (Estado de la Base de Datos)
            report.append("--- ESTADO DE MÓDULOS (BASE DE DATOS) ---\n")
            try {
                // Clientes
                val clients = clientRepo.getAllClients().first().size
                report.append("- Clientes: $clients registros\n")
                
                // Proyectos
                val projects = projectRepo.getAllProjects().first().size
                report.append("- Obras/Proyectos: $projects registros\n")
                
                // Materiales
                val materials = materialRepo.getAllMaterials().first().size
                report.append("- Catálogo Materiales: $materials registros\n")

                // Proveedores
                val providers = providerRepo.getAllProviders().first().size
                report.append("- Proveedores: $providers registros\n")

                // Bitácora
                val diary = diaryRepo.getAll().first().size
                report.append("- Bitácora de Obra: $diary entradas\n")

                // Bancos
                val banks = bankRepo.getAllAccounts().first().size
                report.append("- Cuentas Bancarias: $banks registradas\n")
                
                // Configuraciones
                val config = configRepo.getConfig().first()
                report.append("- Config App: ${if (config != null) "OK" else "ERROR"}\n")
                
                val labor = laborRepo.getLaborPrices().first()
                report.append("- Mano de Obra: ${if (labor != null) "Configurado" else "Pendiente"}\n")

                val taxes = taxRepo.getTaxSetting().first()
                report.append("- Impuestos: ${if (taxes != null) "Configurado (${taxes.percentage}%)" else "Pendiente"}\n")

                val measures = measureRepo.getMeasurements().first()
                val unitSystem = measures.firstOrNull()?.unitSystem ?: "Pendiente"
                report.append("- Medidas Estándar: ${if (measures.isNotEmpty()) "OK ($unitSystem)" else "Pendiente"}\n")

                val company = companyRepo.getCompanyProfile().first()
                report.append("- Perfil Empresa: ${if (company != null) "OK" else "No configurado"}\n")
                
                report.append("[OK] Verificación de integridad de base de datos finalizada.\n")
            } catch (e: Exception) {
                report.append("[ERROR CRÍTICO] Fallo en el escaneo de integridad: ${e.message}\n")
                ErrorTracker.logError(context, "Diagnóstico", "Error en escaneo de integridad de módulos", e)
            }
            report.append("\n")

            // 3. Info de Sistema
            report.append("--- INFORMACIÓN DE DISPOSITIVO ---\n")
            report.append("Modelo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
            report.append("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
            report.append("Kernel: ${System.getProperty("os.version")}\n")
            report.append("====================================================\n")

            val finalReport = report.toString()
            _logs.value = finalReport

            // Siempre generar el archivo si el escaneo termina
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                // Usamos la extensión .log para máxima compatibilidad con lectores de código y Gemini
                val fileName = if (finalReport.contains("[ERROR]") || finalReport.contains("CRASH_")) 
                                "ERROR_REPORT_$timestamp.log" else "SYSTEM_SCAN_$timestamp.log"
                
                val file = File(context.cacheDir, fileName)
                file.writeText(finalReport)
                _diagnosticFile.value = file.canonicalFile
                
                if (finalReport.contains("[ERROR]") || finalReport.contains("CRASH_")) {
                    _eventFlow.emit(UiEvent.ShowSnackbar("Errores detectados. Informe .log preparado para Gemini AI."))
                } else {
                    _eventFlow.emit(UiEvent.ShowSnackbar("Escaneo finalizado. Informe .log listo para exportar."))
                }
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
