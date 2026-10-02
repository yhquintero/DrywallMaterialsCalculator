package com.drywall.calculator.presentation.ui.incomestatement

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.drywall.calculator.data.local.entity.IncomeStatement
import com.drywall.calculator.data.local.entity.PeriodType
import com.drywall.calculator.presentation.ui.components.DateField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeStatementScreen(
    viewModel: IncomeStatementViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val savedStatements by viewModel.savedStatements.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf<IncomeStatement?>(null) }
    var showPeriodSelector by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(message = it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isEditing = formState.id.isNotBlank()
                Button(
                    onClick = {
                        if (isEditing) viewModel.updateStatement() else viewModel.saveStatement()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEditing) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isEditing) Icons.Default.Edit else Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isEditing) "Actualizar" else "Guardar", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }

                val context = LocalContext.current
                OutlinedButton(
                    onClick = { viewModel.exportToPdf(context) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PDF", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Captura") },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Historial") },
                    icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Comparativa") },
                    icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (activeTab) {
                0 -> CaptureTab(
                    formState = formState,
                    validationResult = validationResult,
                    viewModel = viewModel,
                    onPeriodSelectorClick = { showPeriodSelector = true }
                )
                1 -> HistoryTab(
                    savedStatements = savedStatements,
                    onLoadStatement = { viewModel.loadStatement(it) },
                    onEditStatement = { statement ->
                        viewModel.loadStatement(statement)
                        activeTab = 0
                    },
                    onDeleteStatement = { showDeleteDialog = it }
                )
                2 -> ComparisonTab(
                    viewModel = viewModel,
                    currentStatement = buildStatementFromForm(formState)
                )
            }
        }
    }

    if (showPeriodSelector) {
        PeriodSelectorDialog(
            currentType = formState.periodType,
            onSelect = { type ->
                viewModel.updatePeriodType(type)
                showPeriodSelector = false
            },
            onDismiss = { showPeriodSelector = false }
        )
    }

    showDeleteDialog?.let { statement ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Eliminar registro") },
            text = {
                Text("¿Eliminar el estado de resultado del período \"${statement.periodLabel}\"?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStatement(statement.id)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun CaptureTab(
    formState: IncomeStatementFormState,
    validationResult: IncomeStatement.ValidationResult?,
    viewModel: IncomeStatementViewModel,
    onPeriodSelectorClick: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            PeriodSection(
                formState = formState,
                sdf = sdf,
                onPeriodSelectorClick = onPeriodSelectorClick,
                viewModel = viewModel
            )
        }

        item {
            InputSection(
                formState = formState,
                viewModel = viewModel
            )
        }

        item {
            TaxSection(
                formState = formState,
                viewModel = viewModel
            )
        }

        item {
            ResultsCascade(formState = formState)
        }

        item {
            KpiCards(formState = formState)
        }

        if (validationResult != null && (validationResult.errors.isNotEmpty() || validationResult.warnings.isNotEmpty())) {
            item {
                ValidationSection(validationResult = validationResult)
            }
        }

        item {
            NotesSection(
                notas = formState.notas,
                onNotasChange = { viewModel.updateNotas(it) }
            )
        }
    }
}

@Composable
private fun PeriodSection(
    formState: IncomeStatementFormState,
    sdf: SimpleDateFormat,
    onPeriodSelectorClick: () -> Unit,
    viewModel: IncomeStatementViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Período de Análisis",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            OutlinedCard(
                onClick = onPeriodSelectorClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            IncomeStatement.PERIOD_LABELS[formState.periodType] ?: formState.periodType,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${sdf.format(Date(formState.startDate))} - ${sdf.format(Date(formState.endDate))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            if (formState.periodType == IncomeStatement.PERIOD_PERSONALIZADO) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateField(
                        value = sdf.format(Date(formState.startDate)),
                        onValueChange = { text ->
                            try {
                                val date = sdf.parse(text)
                                date?.let { viewModel.updateStartDate(it.time) }
                            } catch (_: Exception) { }
                        },
                        label = "Inicio",
                        modifier = Modifier.weight(1f)
                    )
                    DateField(
                        value = sdf.format(Date(formState.endDate)),
                        onValueChange = { text ->
                            try {
                                val date = sdf.parse(text)
                                date?.let { viewModel.updateEndDate(it.time) }
                            } catch (_: Exception) { }
                        },
                        label = "Fin",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Moneda:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                listOf("USD", "EUR", "CUP", "MLC").forEach { currency ->
                    val isSelected = formState.moneda == currency
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.updateMoneda(currency) },
                        label = { Text(currency, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InputSection(
    formState: IncomeStatementFormState,
    viewModel: IncomeStatementViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Datos Financieros",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            CurrencyInputField(
                label = "(+) Ventas",
                description = "Ingresos totales por ventas",
                value = formState.ventas,
                onValueChange = { viewModel.updateVentas(it) },
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                accentColor = MaterialTheme.colorScheme.primary
            )

            CascadeArrow()

            CurrencyInputField(
                label = "(-) Costos de Ventas",
                description = "Costo directo de los bienes vendidos",
                value = formState.costosVentas,
                onValueChange = { viewModel.updateCostosVentas(it) },
                icon = Icons.Default.ShoppingCart,
                accentColor = MaterialTheme.colorScheme.error
            )

            CascadeArrow()

            CurrencyInputField(
                label = "(-) Gastos Operativos",
                description = "Alquiler, salarios, servicios, etc.",
                value = formState.gastosOperativos,
                onValueChange = { viewModel.updateGastosOperativos(it) },
                icon = Icons.Default.BusinessCenter,
                accentColor = MaterialTheme.colorScheme.error
            )

            CascadeArrow()

            CurrencyInputField(
                label = "(-) Gastos Financieros",
                description = "Intereses, comisiones bancarias, etc.",
                value = formState.gastosFinancieros,
                onValueChange = { viewModel.updateGastosFinancieros(it) },
                icon = Icons.Default.AccountBalance,
                accentColor = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun TaxSection(
    formState: IncomeStatementFormState,
    viewModel: IncomeStatementViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Impuestos",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (formState.impuestoCalculadoAutomatico) "Auto (%)" else "Manual",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = formState.impuestoCalculadoAutomatico,
                    onCheckedChange = { viewModel.updateImpuestoAutomatico(it) }
                )
            }
            Spacer(Modifier.height(8.dp))

            if (formState.impuestoCalculadoAutomatico) {
                OutlinedTextField(
                    value = if (formState.impuestoPorcentaje > 0) formState.impuestoPorcentaje.toString() else "",
                    onValueChange = { text ->
                        val clean = text.replace("\n", "").replace("\r", "")
                        if (clean.isEmpty()) {
                            viewModel.updateTasaImpuestos(0.0)
                        } else {
                            clean.toDoubleOrNull()?.let { viewModel.updateTasaImpuestos(it) }
                        }
                    },
                    label = { Text("Tasa de impuesto (%)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = { Text("%", modifier = Modifier.padding(end = 12.dp)) }
                )
                if (formState.impuestoPorcentaje > 0 && formState.utilidadAntesImpuestos > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Impuesto estimado: ${IncomeStatementUtils.formatCurrency(formState.impuestos, formState.moneda)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                OutlinedTextField(
                    value = if (formState.impuestos > 0) formState.impuestos.toString() else "",
                    onValueChange = { text ->
                        val clean = text.replace("\n", "").replace("\r", "")
                        if (clean.isEmpty()) {
                            viewModel.updateImpuestos(0.0)
                        } else {
                            clean.toDoubleOrNull()?.let { viewModel.updateImpuestos(it) }
                        }
                    },
                    label = { Text("Impuesto (monto fijo)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }
    }
}

@Composable
private fun ResultsCascade(formState: IncomeStatementFormState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Estado de Resultado",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            CascadeResultRow(
                label = "Ventas",
                value = formState.ventas,
                currency = formState.moneda,
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                color = MaterialTheme.colorScheme.primary,
                isPositive = true
            )
            CascadeConnector()
            CascadeResultRow(
                label = "Costos de Ventas",
                value = -formState.costosVentas,
                currency = formState.moneda,
                icon = Icons.Default.Remove,
                color = MaterialTheme.colorScheme.error,
                isNegative = true
            )
            CascadeConnector()
            ResultTotalRow(
                label = "Utilidad Bruta",
                value = formState.utilidadBruta,
                currency = formState.moneda,
                description = "Ventas - Costos de Ventas",
                isPositive = formState.utilidadBruta >= 0
            )
            CascadeConnector()

            Spacer(Modifier.height(4.dp))
            CascadeResultRow(
                label = "Gastos Operativos",
                value = -formState.gastosOperativos,
                currency = formState.moneda,
                icon = Icons.Default.Remove,
                color = MaterialTheme.colorScheme.error,
                isNegative = true
            )
            CascadeConnector()
            ResultTotalRow(
                label = "Utilidad Operativa",
                value = formState.utilidadOperativa,
                currency = formState.moneda,
                description = "Utilidad Bruta - Gastos Operativos",
                isPositive = formState.utilidadOperativa >= 0
            )
            CascadeConnector()

            Spacer(Modifier.height(4.dp))
            CascadeResultRow(
                label = "Gastos Financieros",
                value = -formState.gastosFinancieros,
                currency = formState.moneda,
                icon = Icons.Default.Remove,
                color = MaterialTheme.colorScheme.error,
                isNegative = true
            )
            CascadeConnector()
            ResultTotalRow(
                label = "Utilidad antes de Impuestos",
                value = formState.utilidadAntesImpuestos,
                currency = formState.moneda,
                description = "Utilidad Operativa - Gastos Financieros",
                isPositive = formState.utilidadAntesImpuestos >= 0
            )
            CascadeConnector()

            Spacer(Modifier.height(4.dp))
            CascadeResultRow(
                label = "Impuestos",
                value = -formState.impuestos,
                currency = formState.moneda,
                icon = Icons.Default.Remove,
                color = MaterialTheme.colorScheme.error,
                isNegative = true
            )
            CascadeConnector()

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )

            ResultTotalRow(
                label = "UTILIDAD NETA",
                value = formState.utilidadNeta,
                currency = formState.moneda,
                description = "Utilidad antes de Impuestos - Impuestos",
                isPositive = formState.utilidadNeta >= 0,
                isFinal = true
            )
        }
    }
}

@Composable
private fun KpiCards(formState: IncomeStatementFormState) {
    val grossMargin = IncomeStatementUtils.calculateGrossMargin(formState.ventas, formState.utilidadBruta)
    val operatingMargin = IncomeStatementUtils.calculateOperatingMargin(formState.ventas, formState.utilidadOperativa)
    val netMargin = IncomeStatementUtils.calculateNetMargin(formState.ventas, formState.utilidadNeta)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Indicadores Clave",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard(
                    title = "Margen Bruto",
                    value = grossMargin,
                    color = if (grossMargin >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Margen Operativo",
                    value = operatingMargin,
                    color = if (operatingMargin >= 0) Color(0xFF1565C0) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Margen Neto",
                    value = netMargin,
                    color = if (netMargin >= 0) Color(0xFF6A1B9A) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }

            if (formState.ventas > 0) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiCard(
                        title = "Costo/Ventas",
                        value = if (formState.ventas > 0) (formState.costosVentas / formState.ventas) * 100 else 0.0,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Gastos/Utilidad Bruta",
                        value = if (formState.utilidadBruta > 0) (formState.gastosOperativos / formState.utilidadBruta) * 100 else 0.0,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Punto Eq. (est.)",
                        value = if (netMargin > 0 && formState.ventas > 0) {
                            val costosFijos = formState.gastosOperativos + formState.gastosFinancieros
                            val contribucionMargin = if (formState.ventas > 0) (formState.ventas - formState.costosVentas) / formState.ventas else 0.0
                            if (contribucionMargin > 0) (costosFijos / contribucionMargin) else 0.0
                        } else 0.0,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        isCurrency = true,
                        currency = formState.moneda
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidationSection(validationResult: IncomeStatement.ValidationResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (validationResult.errors.isNotEmpty())
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (validationResult.errors.isNotEmpty()) {
                validationResult.errors.forEach { error ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (validationResult.warnings.isNotEmpty()) {
                validationResult.warnings.forEach { warning ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesSection(
    notas: String,
    onNotasChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Notas",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notas,
                onValueChange = { onNotasChange(it.replace("\n", "").replace("\r", "")) },
                label = { Text("Observaciones del período") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )
        }
    }
}

@Composable
private fun HistoryTab(
    savedStatements: List<IncomeStatement>,
    onLoadStatement: (IncomeStatement) -> Unit,
    onEditStatement: (IncomeStatement) -> Unit,
    onDeleteStatement: (IncomeStatement) -> Unit
) {
    if (savedStatements.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Sin registros",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Guarda tu primer estado de resultado\npara verlo aquí",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(savedStatements, key = { it.id }) { statement ->
                HistoryCard(
                    statement = statement,
                    onLoad = { onLoadStatement(statement) },
                    onEdit = { onEditStatement(statement) },
                    onDelete = { onDeleteStatement(statement) }
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(
    statement: IncomeStatement,
    onLoad: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isPositive = statement.utilidadNeta >= 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onLoad
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        statement.periodLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        IncomeStatement.PERIOD_LABELS[statement.periodType] ?: statement.periodType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                HistoryMiniItem("Ventas", statement.ventas, statement.moneda, Modifier.weight(1f))
                HistoryMiniItem("Bruta", statement.utilidadBruta, statement.moneda, Modifier.weight(1f))
                HistoryMiniItem("Neta", statement.utilidadNeta, statement.moneda, Modifier.weight(1f), highlight = true, isPositive = isPositive)
            }

            if (statement.notas.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    statement.notas,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HistoryMiniItem(
    label: String,
    value: Double,
    currency: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    isPositive: Boolean = true
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            IncomeStatementUtils.formatCurrency(value, currency),
            style = if (highlight) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = when {
                highlight && isPositive -> Color(0xFF2E7D32)
                highlight && !isPositive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComparisonTab(
    viewModel: IncomeStatementViewModel,
    currentStatement: IncomeStatement
) {
    var selectedType by remember { mutableStateOf(IncomeStatement.PERIOD_MENSUAL) }
    val comparisonData by viewModel.comparisonData.collectAsState()
    val sdf = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }

    LaunchedEffect(selectedType) {
        viewModel.loadComparisonData(selectedType, 6)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Comparativa entre Períodos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Selecciona el tipo de período para comparar registros históricos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val types = listOf(
                    IncomeStatement.PERIOD_MENSUAL to "Mes",
                    IncomeStatement.PERIOD_TRIMESTRAL to "Trim",
                    IncomeStatement.PERIOD_ANUAL to "Año"
                )
                types.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, types.size),
                        onClick = { selectedType = type },
                        selected = selectedType == type,
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        if (currentStatement.ventas > 0) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Datos actuales (sin guardar)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                ComparisonRow(
                    periodLabel = "Actual",
                    ventas = currentStatement.ventas,
                    utilidadBruta = currentStatement.utilidadBruta,
                    utilidadOperativa = currentStatement.utilidadOperativa,
                    utilidadNeta = currentStatement.utilidadNeta,
                    currency = currentStatement.moneda,
                    isCurrent = true
                )
            }
        }

        if (comparisonData.isEmpty()) {
            item {
                Spacer(Modifier.height(32.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Sin datos históricos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(comparisonData) { statement ->
                ComparisonRow(
                    periodLabel = statement.periodLabel,
                    ventas = statement.ventas,
                    utilidadBruta = statement.utilidadBruta,
                    utilidadOperativa = statement.utilidadOperativa,
                    utilidadNeta = statement.utilidadNeta,
                    currency = statement.moneda
                )
            }
        }

        if (comparisonData.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                ComparisonSummaryHeader()
                comparisonData.forEach { statement ->
                    ComparisonSummaryRow(statement = statement)
                }
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    periodLabel: String,
    ventas: Double,
    utilidadBruta: Double,
    utilidadOperativa: Double,
    utilidadNeta: Double,
    currency: String,
    isCurrent: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isCurrent) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                periodLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ComparisonMiniItem("Ventas", ventas, currency, Modifier.weight(1f))
                ComparisonMiniItem("Bruta", utilidadBruta, currency, Modifier.weight(1f))
                ComparisonMiniItem("Op.", utilidadOperativa, currency, Modifier.weight(1f))
                ComparisonMiniItem(
                    "Neta", utilidadNeta, currency, Modifier.weight(1f),
                    color = if (utilidadNeta >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ComparisonMiniItem(
    label: String,
    value: Double,
    currency: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            IncomeStatementUtils.formatCurrency(value, currency),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun ComparisonSummaryHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Resumen Comparativo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ComparisonSummaryRow(statement: IncomeStatement) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            statement.periodLabel,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            "MgNet: ${IncomeStatementUtils.formatPercentage(IncomeStatementUtils.calculateNetMargin(statement.ventas, statement.utilidadNeta))}",
            style = MaterialTheme.typography.bodySmall,
            color = if (statement.utilidadNeta >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PeriodSelectorDialog(
    currentType: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Seleccionar Período") },
        text = {
            Column {
                IncomeStatement.PERIOD_TYPES.forEach { type ->
                    val isSelected = type == currentType
                    val description = PeriodType.fromString(type).description
                    Surface(
                        onClick = { onSelect(type) },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    IncomeStatement.PERIOD_LABELS[type] ?: type,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun CurrencyInputField(
    label: String,
    description: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    icon: ImageVector,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    OutlinedTextField(
        value = if (value > 0) value.toString() else "",
        onValueChange = { text ->
            val clean = text.replace("\n", "").replace("\r", "")
            if (clean.isEmpty()) {
                onValueChange(0.0)
            } else {
                clean.toDoubleOrNull()?.let { onValueChange(it) }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        placeholder = { Text("0.00") }
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun CascadeArrow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CascadeResultRow(
    label: String,
    value: Double,
    currency: String,
    icon: ImageVector,
    color: Color,
    isPositive: Boolean = false,
    isNegative: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            IncomeStatementUtils.formatCurrency(value, currency),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun CascadeConnector() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp)
            .height(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun ResultTotalRow(
    label: String,
    value: Double,
    currency: String,
    description: String,
    isPositive: Boolean,
    isFinal: Boolean = false
) {
    val bgColor = when {
        isFinal && isPositive -> Color(0xFF2E7D32).copy(alpha = 0.1f)
        isFinal && !isPositive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val textColor = when {
        isFinal && isPositive -> Color(0xFF2E7D32)
        isFinal && !isPositive -> MaterialTheme.colorScheme.error
        isPositive -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bgColor,
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = if (isFinal) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isFinal) FontWeight.ExtraBold else FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    IncomeStatementUtils.formatCurrency(value, currency),
                    style = if (isFinal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isFinal) FontWeight.ExtraBold else FontWeight.Bold,
                    color = textColor
                )
            }
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: Double,
    color: Color,
    modifier: Modifier = Modifier,
    isCurrency: Boolean = false,
    currency: String = "USD"
) {
    Card(
        modifier = modifier.animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isCurrency) IncomeStatementUtils.formatCurrency(value, currency)
                else IncomeStatementUtils.formatPercentage(value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun buildStatementFromForm(state: IncomeStatementFormState): IncomeStatement {
    return IncomeStatement(
        periodType = state.periodType,
        startDate = state.startDate,
        endDate = state.endDate,
        periodLabel = state.periodLabel,
        ventas = state.ventas,
        costosVentas = state.costosVentas,
        gastosOperativos = state.gastosOperativos,
        gastosFinancieros = state.gastosFinancieros,
        impuestos = state.impuestos,
        impuestoPorcentaje = state.impuestoPorcentaje,
        impuestoCalculadoAutomatico = state.impuestoCalculadoAutomatico,
        moneda = state.moneda
    ).recalcular()
}
