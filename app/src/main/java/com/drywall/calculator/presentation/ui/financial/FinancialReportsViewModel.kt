package com.drywall.calculator.presentation.ui.financial

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.CuentaContable
import com.drywall.calculator.data.local.entity.FondoInversion
import com.drywall.calculator.data.local.entity.IncomeStatement
import com.drywall.calculator.data.local.entity.MovimientoContable
import com.drywall.calculator.data.repository.CuentaContableRepository
import com.drywall.calculator.data.repository.FondoInversionRepository
import com.drywall.calculator.data.repository.MovimientoContableRepository
import com.drywall.calculator.data.repository.CompanyRepository
import com.drywall.calculator.data.repository.IncomeStatementRepository
import com.drywall.calculator.utils.FinancialPdfGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class FinancialReportsViewModel @Inject constructor(
    private val cuentaRepository: CuentaContableRepository,
    private val movimientoRepository: MovimientoContableRepository,
    private val fondoRepository: FondoInversionRepository,
    private val companyRepository: CompanyRepository,
    private val incomeStatementRepository: IncomeStatementRepository
) : ViewModel() {

    val cuentas = cuentaRepository.getAllCuentas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val movimientos = movimientoRepository.getAllMovimientos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val fondosInversion = fondoRepository.getAllFondos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val companyProfile = companyRepository.getCompanyProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    val selectedMonth = _selectedMonth.asStateFlow()

    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear = _selectedYear.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbarMessage.asStateFlow()

    private val _pdfFile = MutableStateFlow<File?>(null)
    val pdfFile = _pdfFile.asStateFlow()

    private val _cuentaFormState = MutableStateFlow(CuentaContableFormState())
    val cuentaFormState = _cuentaFormState.asStateFlow()

    private val _fondoFormState = MutableStateFlow(FondoInversionFormState())
    val fondoFormState = _fondoFormState.asStateFlow()

    private val _movimientoFormState = MutableStateFlow(MovimientoContableFormState())
    val movimientoFormState = _movimientoFormState.asStateFlow()

    init {
        viewModelScope.launch {
            cuentaRepository.seedCatalogoBase()
        }
    }

    fun updateMonth(month: Int) { _selectedMonth.value = month }
    fun updateYear(year: Int) { _selectedYear.value = year }

    fun exportBalanceComprobacion(context: Context) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val profile = companyProfile.value
                val cuentasList = cuentas.value
                val file = FinancialPdfGenerator.generateBalanceComprobacion(
                    context, cuentasList, _selectedMonth.value, _selectedYear.value,
                    profile?.businessName ?: "Mi Empresa", profile?.logoUri
                )
                _pdfFile.value = file
                _snackbarMessage.value = "Balance de Comprobación generado"
            } catch (e: Exception) {
                _snackbarMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportEstadoResultado(context: Context) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val profile = companyProfile.value
                val cal = Calendar.getInstance()
                cal.set(_selectedYear.value, _selectedMonth.value - 1, 1, 0, 0, 0)
                val startDate = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val endDate = cal.timeInMillis

                val statements = incomeStatementRepository.getStatementsByDateRange(startDate, endDate).first()
                val statement = statements.firstOrNull() ?: IncomeStatement(
                    periodType = IncomeStatement.PERIOD_MENSUAL, periodLabel = "",
                    startDate = startDate, endDate = endDate
                )
                val file = FinancialPdfGenerator.generateEstadoResultado(
                    context, statement,
                    profile?.businessName ?: "Mi Empresa", profile?.logoUri
                )
                _pdfFile.value = file
                _snackbarMessage.value = "Estado de Resultado generado"
            } catch (e: Exception) {
                _snackbarMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportFondoInversion(context: Context) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val profile = companyProfile.value
                val fondosList = fondosInversion.value.filter {
                    it.mes == _selectedMonth.value && it.anio == _selectedYear.value
                }
                val file = FinancialPdfGenerator.generateFondoInversion(
                    context, fondosList, _selectedMonth.value, _selectedYear.value,
                    profile?.businessName ?: "Mi Empresa", profile?.logoUri
                )
                _pdfFile.value = file
                _snackbarMessage.value = "Fondo de Inversión generado"
            } catch (e: Exception) {
                _snackbarMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportBalanceGeneral(context: Context) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val profile = companyProfile.value
                val cuentasList = cuentas.value
                val file = FinancialPdfGenerator.generateBalanceGeneral(
                    context, cuentasList, _selectedMonth.value, _selectedYear.value,
                    profile?.businessName ?: "Mi Empresa", profile?.logoUri
                )
                _pdfFile.value = file
                _snackbarMessage.value = "Balance General generado"
            } catch (e: Exception) {
                _snackbarMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateCuentaForm(state: CuentaContableFormState) { _cuentaFormState.value = state }
    fun updateFondoForm(state: FondoInversionFormState) { _fondoFormState.value = state }
    fun updateMovimientoForm(state: MovimientoContableFormState) { _movimientoFormState.value = state }

    fun saveCuenta() {
        viewModelScope.launch {
            val state = _cuentaFormState.value
            val cuenta = CuentaContable(
                id = state.id.ifBlank { java.util.UUID.randomUUID().toString() },
                codigo = state.codigo, nombre = state.nombre, tipo = state.tipo,
                subtipo = state.subtipo, saldoDebe = state.saldoDebe, saldoHaber = state.saldoHaber,
                aft = state.aft, depreciacionAcum = state.depreciacionAcum, descripcion = state.descripcion
            )
            cuentaRepository.saveCuenta(cuenta)
            _cuentaFormState.value = CuentaContableFormState()
            _snackbarMessage.value = "Cuenta guardada"
        }
    }

    fun deleteCuenta(id: String) {
        viewModelScope.launch {
            cuentaRepository.deleteCuentaById(id)
            _snackbarMessage.value = "Cuenta eliminada"
        }
    }

    fun saveMovimiento() {
        viewModelScope.launch {
            val state = _movimientoFormState.value
            val (mes, anio) = MovimientoContable.fromFecha(state.fecha)
            val movimiento = MovimientoContable(
                cuentaContableId = state.cuentaContableId, descripcion = state.descripcion,
                montoDebe = state.montoDebe, montoHaber = state.montoHaber,
                referencia = state.referencia, mes = mes, anio = anio
            )
            movimientoRepository.saveMovimiento(movimiento)
            _movimientoFormState.value = MovimientoContableFormState()
            _snackbarMessage.value = "Movimiento registrado"
        }
    }

    fun saveFondo() {
        viewModelScope.launch {
            val state = _fondoFormState.value
            val total = state.inversionMateriales + state.inversionManoObra +
                    state.inversionTransporte + state.inversionEquipos + state.inversionOtros
            val fondo = FondoInversion(
                id = state.id.ifBlank { java.util.UUID.randomUUID().toString() },
                proyectoId = state.proyectoId, proyectoNombre = state.proyectoNombre,
                inversionMateriales = state.inversionMateriales,
                inversionManoObra = state.inversionManoObra,
                inversionTransporte = state.inversionTransporte,
                inversionEquipos = state.inversionEquipos,
                inversionOtros = state.inversionOtros,
                totalInversion = total, moneda = state.moneda,
                mes = _selectedMonth.value, anio = _selectedYear.value,
                notas = state.notas
            )
            fondoRepository.saveFondo(fondo)
            _fondoFormState.value = FondoInversionFormState()
            _snackbarMessage.value = "Inversión registrada"
        }
    }

    fun deleteFondo(id: String) {
        viewModelScope.launch {
            fondoRepository.deleteFondoById(id)
            _snackbarMessage.value = "Registro eliminado"
        }
    }

    fun deleteMovimiento(id: String) {
        viewModelScope.launch {
            movimientoRepository.deleteMovimientoById(id)
            _snackbarMessage.value = "Movimiento eliminado"
        }
    }

    fun clearSnackbar() { _snackbarMessage.value = null }
}

data class CuentaContableFormState(
    val id: String = "",
    val codigo: String = "",
    val nombre: String = "",
    val tipo: String = CuentaContable.TIPO_ACTIVO,
    val subtipo: String = "",
    val saldoDebe: Double = 0.0,
    val saldoHaber: Double = 0.0,
    val aft: Double = 0.0,
    val depreciacionAcum: Double = 0.0,
    val descripcion: String = ""
)

data class MovimientoContableFormState(
    val id: String = "",
    val cuentaContableId: String = "",
    val fecha: Long = System.currentTimeMillis(),
    val descripcion: String = "",
    val montoDebe: Double = 0.0,
    val montoHaber: Double = 0.0,
    val referencia: String = ""
)

data class FondoInversionFormState(
    val id: String = "",
    val proyectoId: String = "",
    val proyectoNombre: String = "",
    val inversionMateriales: Double = 0.0,
    val inversionManoObra: Double = 0.0,
    val inversionTransporte: Double = 0.0,
    val inversionEquipos: Double = 0.0,
    val inversionOtros: Double = 0.0,
    val moneda: String = "USD",
    val notas: String = ""
)
