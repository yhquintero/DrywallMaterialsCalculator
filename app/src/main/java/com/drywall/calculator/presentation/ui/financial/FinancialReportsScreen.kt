package com.drywall.calculator.presentation.ui.financial

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.drywall.calculator.data.local.entity.CuentaContable
import com.drywall.calculator.data.local.entity.FondoInversion
import com.drywall.calculator.utils.PdfUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialReportsScreen(
    viewModel: FinancialReportsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToCuentas: () -> Unit = {},
    onNavigateToMovimientos: () -> Unit = {},
    onNavigateToFondos: () -> Unit = {},
    onNavigateToIncomeStatement: () -> Unit = {},
    onNavigateToCompany: () -> Unit = {}
) {
    val context = LocalContext.current
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val pdfFile by viewModel.pdfFile.collectAsState()
    val companyProfile by viewModel.companyProfile.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showCompanyAlert by remember { mutableStateOf(false) }

    val checkCompanyAndExport: ((Context) -> Unit) -> Unit = { exportAction ->
        if (companyProfile?.businessName.isNullOrBlank()) {
            showCompanyAlert = true
        } else {
            exportAction(context)
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(message = it)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(pdfFile) {
        pdfFile?.let {
            PdfUtils.viewPdf(context, it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Reporte Financiero") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PeriodSelector(
                    selectedMonth = selectedMonth,
                    selectedYear = selectedYear,
                    onMonthChange = { viewModel.updateMonth(it) },
                    onYearChange = { viewModel.updateYear(it) }
                )
            }

            item {
                Text(
                    "Exportar Reportes a PDF",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Selecciona el período y genera cada reporte",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                ExportButton(
                    title = "Balance de Comprobación",
                    subtitle = "Cuentas T con saldos Debe/Haber, AFT y Depreciación",
                    icon = Icons.Default.Balance,
                    color = Color(0xFF1A237E),
                    enabled = !isLoading,
                    onClick = { checkCompanyAndExport { viewModel.exportBalanceComprobacion(it) } }
                )
            }

            item {
                ExportButton(
                    title = "Estado de Resultado (PDF)",
                    subtitle = "Exportar ventas, costos, gastos y utilidad neta",
                    icon = Icons.Default.PictureAsPdf,
                    color = Color(0xFF2E7D32),
                    enabled = !isLoading,
                    onClick = { checkCompanyAndExport { viewModel.exportEstadoResultado(it) } }
                )
            }

            item {
                ExportButton(
                    title = "Fondo de Inversión",
                    subtitle = "Inversiones por proyecto: materiales, mano de obra, etc.",
                    icon = Icons.Default.PrecisionManufacturing,
                    color = Color(0xFFE65100),
                    enabled = !isLoading,
                    onClick = { checkCompanyAndExport { viewModel.exportFondoInversion(it) } }
                )
            }

            item {
                ExportButton(
                    title = "Balance General",
                    subtitle = "Activos, pasivos y patrimonio",
                    icon = Icons.Default.AccountBalanceWallet,
                    color = Color(0xFF6A1B9A),
                    enabled = !isLoading,
                    onClick = { checkCompanyAndExport { viewModel.exportBalanceGeneral(it) } }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "Gestión de Datos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                NavigationCard(
                    title = "Estado de Resultado",
                    subtitle = "Capturar ventas, costos y gastos operativos",
                    icon = Icons.Default.TrendingUp,
                    onClick = onNavigateToIncomeStatement
                )
            }

            item {
                NavigationCard(
                    title = "Catálogo de Cuentas",
                    subtitle = "Administrar cuentas contables",
                    icon = Icons.Default.MenuBook,
                    onClick = onNavigateToCuentas
                )
            }

            item {
                NavigationCard(
                    title = "Registro de Movimientos",
                    subtitle = "Registrar movimientos Debe/Haber",
                    icon = Icons.Default.ReceiptLong,
                    onClick = onNavigateToMovimientos
                )
            }

            item {
                NavigationCard(
                    title = "Fondos de Inversión",
                    subtitle = "Registrar inversiones por proyecto",
                    icon = Icons.Default.Construction,
                    onClick = onNavigateToFondos
                )
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    if (showCompanyAlert) {
        AlertDialog(
            onDismissRequest = { showCompanyAlert = false },
            icon = { Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Datos de Empresa Requeridos") },
            text = { 
                Text("Para generar reportes profesionales con el nombre de su entidad y logotipo, primero debe completar la configuración de su Empresa o Perfil Profesional.") 
            },
            confirmButton = {
                Button(onClick = { 
                    showCompanyAlert = false
                    onNavigateToCompany()
                }) {
                    Text("Configurar Ahora")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompanyAlert = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(
    selectedMonth: Int,
    selectedYear: Int,
    onMonthChange: (Int) -> Unit,
    onYearChange: (Int) -> Unit
) {
    val months = listOf("", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Período",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var monthExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = monthExpanded,
                    onExpandedChange = { monthExpanded = !monthExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = months[selectedMonth],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mes") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = monthExpanded,
                        onDismissRequest = { monthExpanded = false }
                    ) {
                        months.drop(1).forEachIndexed { index, month ->
                            DropdownMenuItem(
                                text = { Text(month) },
                                onClick = {
                                    onMonthChange(index + 1)
                                    monthExpanded = false
                                }
                            )
                        }
                    }
                }

                var yearExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = yearExpanded,
                    onExpandedChange = { yearExpanded = !yearExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedYear.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Año") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = yearExpanded,
                        onDismissRequest = { yearExpanded = false }
                    ) {
                        for (year in 2020..2030) {
                            DropdownMenuItem(
                                text = { Text(year.toString()) },
                                onClick = {
                                    onYearChange(year)
                                    yearExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        onClick = { if (enabled) onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = "Exportar PDF",
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun NavigationCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
