package com.drywall.calculator.presentation.ui.incomestatement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.IncomeStatement
import com.drywall.calculator.data.repository.IncomeStatementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IncomeStatementViewModel @Inject constructor(
    private val repository: IncomeStatementRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(IncomeStatementFormState())
    val formState = _formState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _savedStatements = MutableStateFlow<List<IncomeStatement>>(emptyList())
    val savedStatements = _savedStatements.asStateFlow()

    private val _validationResult = MutableStateFlow<IncomeStatement.ValidationResult?>(null)
    val validationResult = _validationResult.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbarMessage
        .map { it }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _showSaveConfirmation = MutableStateFlow(false)
    val showSaveConfirmation = _showSaveConfirmation.asStateFlow()

    private val _pendingSaveStatement = MutableStateFlow<IncomeStatement?>(null)
    val pendingSaveStatement = _pendingSaveStatement.asStateFlow()

    private val _statementsByType = MutableStateFlow<List<IncomeStatement>>(emptyList())
    val statementsByType = _statementsByType.asStateFlow()

    private val _comparisonData = MutableStateFlow<List<IncomeStatement>>(emptyList())
    val comparisonData = _comparisonData.asStateFlow()

    init {
        loadSavedStatements()
    }

    fun updatePeriodType(periodType: String) {
        _formState.update { it.copy(periodType = periodType) }
        val range = IncomeStatementUtils.getDefaultDateRange(periodType)
        _formState.update { it.copy(startDate = range.first, endDate = range.second) }
        updatePeriodLabel()
    }

    fun updateStartDate(timestamp: Long) {
        _formState.update { it.copy(startDate = timestamp) }
        updatePeriodLabel()
    }

    fun updateEndDate(timestamp: Long) {
        _formState.update { it.copy(endDate = timestamp) }
        updatePeriodLabel()
    }

    fun updatePeriodLabel() {
        _formState.update { state ->
            val label = IncomeStatementUtils.generatePeriodLabel(
                state.periodType,
                state.startDate,
                state.endDate
            )
            state.copy(periodLabel = label)
        }
    }

    fun updateVentas(value: Double) {
        _formState.update { it.copy(ventas = value) }
        recalculateAndValidate()
    }

    fun updateCostosVentas(value: Double) {
        _formState.update { it.copy(costosVentas = value) }
        recalculateAndValidate()
    }

    fun updateGastosOperativos(value: Double) {
        _formState.update { it.copy(gastosOperativos = value) }
        recalculateAndValidate()
    }

    fun updateGastosFinancieros(value: Double) {
        _formState.update { it.copy(gastosFinancieros = value) }
        recalculateAndValidate()
    }

    fun updateImpuestos(value: Double) {
        _formState.update { it.copy(impuestos = value, impuestoCalculadoAutomatico = false) }
        recalculateAndValidate()
    }

    fun updateTasaImpuestos(value: Double) {
        _formState.update { it.copy(impuestoPorcentaje = value, impuestoCalculadoAutomatico = true) }
        recalculateAndValidate()
    }

    fun updateImpuestoAutomatico(auto: Boolean) {
        _formState.update { it.copy(impuestoCalculadoAutomatico = auto) }
        recalculateAndValidate()
    }

    fun updateProyectoId(proyectoId: String, proyectoNombre: String = "") {
        _formState.update { it.copy(proyectoId = proyectoId, proyectoNombre = proyectoNombre) }
    }

    fun updateNotas(notas: String) {
        _formState.update { it.copy(notas = notas) }
    }

    fun updateMoneda(moneda: String) {
        _formState.update { it.copy(moneda = moneda) }
    }

    private fun recalculateAndValidate() {
        val statement = buildStatementFromForm().recalcular()
        _validationResult.value = statement.validar()

        _formState.update { state ->
            state.copy(
                utilidadBruta = statement.utilidadBruta,
                utilidadOperativa = statement.utilidadOperativa,
                utilidadAntesImpuestos = statement.utilidadAntesImpuestos,
                utilidadNeta = statement.utilidadNeta,
                impuestos = statement.impuestos
            )
        }
    }

    private fun buildStatementFromForm(): IncomeStatement {
        val state = _formState.value
        return IncomeStatement(
            periodType = state.periodType,
            startDate = state.startDate,
            endDate = state.endDate,
            periodLabel = state.periodLabel,
            proyectoId = state.proyectoId,
            proyectoNombre = state.proyectoNombre,
            ventas = state.ventas,
            costosVentas = state.costosVentas,
            gastosOperativos = state.gastosOperativos,
            gastosFinancieros = state.gastosFinancieros,
            impuestos = state.impuestos,
            impuestoPorcentaje = state.impuestoPorcentaje,
            impuestoCalculadoAutomatico = state.impuestoCalculadoAutomatico,
            utilidadBruta = state.utilidadBruta,
            utilidadOperativa = state.utilidadOperativa,
            utilidadAntesImpuestos = state.utilidadAntesImpuestos,
            utilidadNeta = state.utilidadNeta,
            notas = state.notas,
            moneda = state.moneda
        )
    }

    fun saveStatement() {
        val statement = buildStatementFromForm().recalcular()
        val validation = statement.validar()

        if (!validation.isValid) {
            _snackbarMessage.value = "Error: ${validation.errors.joinToString(", ")}"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.saveStatement(statement)
                _snackbarMessage.value = "Estado de resultado guardado correctamente"
                _formState.update { it.copy(id = statement.id) }
                loadSavedStatements()
            } catch (e: Exception) {
                _snackbarMessage.value = "Error al guardar: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateStatement() {
        val currentState = _formState.value
        if (currentState.id.isBlank()) {
            saveStatement()
            return
        }

        val statement = buildStatementFromForm().recalcular()
        val validation = statement.validar()

        if (!validation.isValid) {
            _snackbarMessage.value = "Error: ${validation.errors.joinToString(", ")}"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.updateStatement(statement.copy(id = currentState.id))
                _snackbarMessage.value = "Estado de resultado actualizado correctamente"
                _formState.update { it.copy(id = "") }
                loadSavedStatements()
            } catch (e: Exception) {
                _snackbarMessage.value = "Error al actualizar: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadStatement(statement: IncomeStatement) {
        _formState.value = IncomeStatementFormState(
            id = statement.id,
            periodType = statement.periodType,
            startDate = statement.startDate,
            endDate = statement.endDate,
            periodLabel = statement.periodLabel,
            proyectoId = statement.proyectoId,
            proyectoNombre = statement.proyectoNombre,
            ventas = statement.ventas,
            costosVentas = statement.costosVentas,
            gastosOperativos = statement.gastosOperativos,
            gastosFinancieros = statement.gastosFinancieros,
            impuestos = statement.impuestos,
            impuestoPorcentaje = statement.impuestoPorcentaje,
            impuestoCalculadoAutomatico = statement.impuestoCalculadoAutomatico,
            utilidadBruta = statement.utilidadBruta,
            utilidadOperativa = statement.utilidadOperativa,
            utilidadAntesImpuestos = statement.utilidadAntesImpuestos,
            utilidadNeta = statement.utilidadNeta,
            notas = statement.notas,
            moneda = statement.moneda
        )
        _validationResult.value = statement.validar()
    }

    fun newStatement() {
        _formState.value = IncomeStatementFormState()
        _validationResult.value = null
    }

    fun deleteStatement(id: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.deleteStatementById(id)
                _snackbarMessage.value = "Registro eliminado"
                loadSavedStatements()
            } catch (e: Exception) {
                _snackbarMessage.value = "Error al eliminar: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadSavedStatements() {
        viewModelScope.launch {
            val statements = repository.getAllStatements().first()
            _savedStatements.value = statements
        }
    }

    fun loadStatementsByType(periodType: String) {
        viewModelScope.launch {
            val statements = repository.getStatementsByPeriodType(periodType).first()
            _statementsByType.value = statements
        }
    }

    fun loadComparisonData(periodType: String, limit: Int = 12) {
        viewModelScope.launch {
            val statements = repository.getLatestStatements(periodType, limit)
            _comparisonData.value = statements
        }
    }

    suspend fun getAggregatedForBalanceSheet(startDate: Long, endDate: Long) {
        repository.getAggregatedForBalanceSheet(startDate, endDate)
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun exportToPdf(context: android.content.Context) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val statement = buildStatementFromForm().recalcular()
                val file = com.drywall.calculator.utils.PdfGenerator.generateIncomeStatementPdf(context, statement)
                
                // Abrir el PDF generado
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                _snackbarMessage.value = "PDF generado correctamente"
            } catch (e: Exception) {
                _snackbarMessage.value = "Error al generar PDF: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

data class IncomeStatementFormState(
    val id: String = "",
    val periodType: String = IncomeStatement.PERIOD_MENSUAL,
    val startDate: Long = IncomeStatementUtils.getMonthStart(),
    val endDate: Long = IncomeStatementUtils.getMonthEnd(),
    val periodLabel: String = "",
    val proyectoId: String = "",
    val proyectoNombre: String = "",
    val ventas: Double = 0.0,
    val costosVentas: Double = 0.0,
    val gastosOperativos: Double = 0.0,
    val gastosFinancieros: Double = 0.0,
    val impuestos: Double = 0.0,
    val impuestoPorcentaje: Double = 0.0,
    val impuestoCalculadoAutomatico: Boolean = true,
    val utilidadBruta: Double = 0.0,
    val utilidadOperativa: Double = 0.0,
    val utilidadAntesImpuestos: Double = 0.0,
    val utilidadNeta: Double = 0.0,
    val notas: String = "",
    val moneda: String = "USD"
)
