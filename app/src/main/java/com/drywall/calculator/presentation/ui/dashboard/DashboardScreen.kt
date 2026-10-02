package com.drywall.calculator.presentation.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.drywall.calculator.data.local.entity.CurrencyHistory
import com.drywall.calculator.data.local.entity.Material
import com.drywall.calculator.data.local.entity.Project
import com.drywall.calculator.data.local.entity.Client
import com.drywall.calculator.data.local.entity.Provider
import com.drywall.calculator.presentation.ui.currency.CurrencyViewModel
import com.drywall.calculator.presentation.ui.materials.MaterialsViewModel
import com.drywall.calculator.presentation.ui.projects.ProjectsViewModel
import com.drywall.calculator.presentation.ui.providers.ProvidersViewModel
import com.drywall.calculator.presentation.ui.medidas.MedidasViewModel
import com.drywall.calculator.presentation.ui.currency.getCurrencyColor
import com.drywall.calculator.presentation.ui.currency.LegendItem
import com.drywall.common.ui.components.ChartDataPoint
import com.drywall.common.ui.components.EvolutionChart
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.drywall.calculator.utils.PdfUtils
import com.drywall.calculator.utils.PdfGenerator
import com.drywall.calculator.presentation.ui.settings.LicenseViewModel
import com.drywall.calculator.presentation.ui.inventory.InventoryViewModel
import com.drywall.calculator.utils.security.ConsumedLicense
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    projectsViewModel: ProjectsViewModel = hiltViewModel(),
    materialsViewModel: MaterialsViewModel = hiltViewModel(),
    providersViewModel: ProvidersViewModel = hiltViewModel(),
    currencyViewModel: CurrencyViewModel = hiltViewModel(),
    licenseViewModel: LicenseViewModel = hiltViewModel(),
    inventoryViewModel: InventoryViewModel = hiltViewModel()
) {
    val projects by projectsViewModel.projects.collectAsState(initial = emptyList())
    val materials by materialsViewModel.materials.collectAsState(initial = emptyList())
    val clients by projectsViewModel.allClients.collectAsState(initial = emptyList())
    val providers by providersViewModel.providers.collectAsState(initial = emptyList())
    val currencyHistory by currencyViewModel.history.collectAsState()
    val isScraping by currencyViewModel.isScraping.collectAsState()
    val lastScraped by currencyViewModel.lastScrapedTimestamp.collectAsState()
    
    val consumedHistory by licenseViewModel.consumedHistory.collectAsState()
    val stockCardTransactions by inventoryViewModel.stockCardTransactions.collectAsState()

    LaunchedEffect(Unit) {
        currencyViewModel.fetchRates()
    }

    DashboardScreenContent(
        projects = projects,
        materials = materials,
        clients = clients,
        providers = providers,
        currencyHistory = currencyHistory,
        isScraping = isScraping,
        lastScraped = lastScraped,
        consumedHistory = consumedHistory,
        stockCardTransactions = stockCardTransactions,
        onRefreshRates = { currencyViewModel.fetchRates() },
        onNavigate = { navController.navigate(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreenContent(
    projects: List<Project>,
    materials: List<Material>,
    clients: List<Client>,
    providers: List<Provider>,
    currencyHistory: List<CurrencyHistory>,
    isScraping: Boolean,
    lastScraped: String,
    consumedHistory: List<ConsumedLicense>,
    stockCardTransactions: List<com.drywall.calculator.data.local.entity.InventoryTransaction>,
    onRefreshRates: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val currentTime = System.currentTimeMillis()
    
    val lowStockMaterials = remember(materials) { materials.filter { it.quantity < 10 } }
    val criticalStockMaterials = remember(materials) { materials.filter { it.quantity < 5 } }
    val daysUntilLicenseExpiry = remember(consumedHistory) {
        if (consumedHistory.isNotEmpty()) {
            val latestLicense = consumedHistory.lastOrNull()
            if (latestLicense != null && latestLicense.expiryDate > 1000000000000L) {
                val diff = latestLicense.expiryDate - System.currentTimeMillis()
                TimeUnit.MILLISECONDS.toDays(diff)
            } else -1L
        } else -1L
    }
    val pendingOrders = remember(stockCardTransactions) { stockCardTransactions.filter { it.quantityChange < 0 } }

    var showPdfExportDialog by remember { mutableStateOf(false) }
    var showLicenseHistoryDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Dashboard Principal",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // --- RESUMEN RÁPIDO ---
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "RESUMEN DE ACTIVIDAD", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryCard(title = "Clientes", value = clients.size.toString(), icon = Icons.Default.People, containerColor = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f).clickable { onNavigate("clients") })
                        SummaryCard(title = "Obras Activas", value = projects.size.toString(), icon = Icons.Default.Architecture, containerColor = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.weight(1f).clickable { onNavigate("projects") })
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryCard(title = "Materiales", value = materials.size.toString(), icon = Icons.Default.Inventory2, containerColor = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.weight(1f).clickable { onNavigate("materials") })
                        SummaryCard(title = "Proveedores", value = providers.size.toString(), icon = Icons.Default.ContactPage, containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f), modifier = Modifier.weight(1f).clickable { onNavigate("providers") })
                    }
                }
            }
        }

        // --- ALERTAS URGENTES ---
        if (criticalStockMaterials.isNotEmpty() || daysUntilLicenseExpiry in 0..3) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Alertas", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "ALERTAS URGENTES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (daysUntilLicenseExpiry in 0..3) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = when { daysUntilLicenseExpiry == 0L -> "Licencia vence HOY"; daysUntilLicenseExpiry < 0 -> "Licencia VENCIDA"; else -> "Licencia vence en $daysUntilLicenseExpiry días" }, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (criticalStockMaterials.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "${criticalStockMaterials.size} material(es) con stock crítico (<5)", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // --- EVOLUCIÓN DE TASAS ---
        item {
            var selectedRange by remember { mutableStateOf("1S") }
            val ranges = listOf("1D", "1S", "1M", "3M", "1A")
            ElevatedCard(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timeline, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("EVOLUCIÓN DE DIVISAS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (lastScraped.isNotEmpty()) Text("Sincronizado: $lastScraped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (isScraping) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = onRefreshRates) { Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary) }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ranges.forEach { range ->
                            FilterChip(selected = selectedRange == range, onClick = { selectedRange = range }, label = { Text(range, fontSize = 10.sp) }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium)
                        }
                    }
                    val chartData = remember(currencyHistory, selectedRange) {
                        val filterTime = when (selectedRange) { "1D" -> currentTime - 86400000L; "1S" -> currentTime - 604800000L; "1M" -> currentTime - 2592000000L; "3M" -> currentTime - 7776000000L; "1A" -> currentTime - 31536000000L; else -> 0L }
                        currencyHistory.filter { it.timestamp >= filterTime }.sortedBy { it.timestamp }.map { ChartDataPoint(it.timestamp, mapOf("USD" to it.usdRate, "EUR" to it.eurRate, "MLC" to it.mlcRate, "CAD" to it.cadRate, "MEX" to it.mexRate, "ZELLE" to it.zelleRate, "CLA" to it.claRate)) }
                    }
                    EvolutionChart(history = chartData, currencyColors = mapOf("USD" to Color(0xFF2196F3), "EUR" to Color(0xFFE91E63), "MLC" to Color(0xFF4CAF50), "CAD" to Color(0xFFFF9800), "MEX" to Color(0xFF9C27B0), "ZELLE" to Color(0xFF00BCD4), "CLA" to Color(0xFF795548)), modifier = Modifier.fillMaxWidth().height(200.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { LegendItem(getCurrencyColor("USD"), "USD"); LegendItem(getCurrencyColor("EUR"), "EUR"); LegendItem(getCurrencyColor("MLC"), "MLC"); LegendItem(getCurrencyColor("CAD"), "CAD") }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { LegendItem(getCurrencyColor("MEX"), "MEX"); LegendItem(getCurrencyColor("ZELLE"), "ZELLE"); LegendItem(getCurrencyColor("CLA"), "CLA") }
                    }
                }
            }
        }

        // --- ESTADO OPERATIVO ---
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "ESTADO OPERATIVO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Balance de Almacén", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (materials.isNotEmpty()) MaterialStockBarChart(materials = materials.take(5), modifier = Modifier.fillMaxWidth().height(160.dp))
                    else Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { Text("Sin datos de inventario") }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Indicadores Clave", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IndicatorChip(label = "Stock Bajo", count = lowStockMaterials.size, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        IndicatorChip(label = "Críticos", count = criticalStockMaterials.size, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.weight(1f))
                        IndicatorChip(label = "Salidas", count = pendingOrders.size, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // --- ACTIVIDAD RECIENTE ---
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "ACTIVIDAD RECIENTE (OBRAS)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (projects.isNotEmpty()) {
                        projects.takeLast(3).reversed().forEach { project ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Architecture, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = project.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(text = project.clientName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Text(text = "No hay obras registradas", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // --- ACCIONES RÁPIDAS ---
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "ACCIONES RÁPIDAS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton(icon = Icons.Default.Assessment, label = "Reportes\nFinancieros", onClick = { onNavigate("financial_reports") }, modifier = Modifier.weight(1f))
                        ActionButton(icon = Icons.Default.Add, label = "Nueva\nObra", onClick = { onNavigate("projects") }, modifier = Modifier.weight(1f))
                        ActionButton(icon = Icons.Default.History, label = "Ver\nLicencia", onClick = { showLicenseHistoryDialog = true }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }

    if (showPdfExportDialog) {
        PdfExportDashboardDialog(
            materials = materials,
            projects = projects,
            clients = clients,
            consumedHistory = consumedHistory,
            onDismiss = { showPdfExportDialog = false }
        )
    }

    if (showLicenseHistoryDialog) {
        LicenseHistoryDashboardDialog(
            consumedHistory = consumedHistory,
            onDismiss = { showLicenseHistoryDialog = false }
        )
    }
}

@Composable
fun SummaryCard(title: String, value: String, icon: ImageVector, containerColor: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = containerColor), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(title, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun IndicatorChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.clickable(onClick = onClick), color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun MaterialStockBarChart(materials: List<Material>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val barWidth = size.width / (materials.size * 2f)
        val maxStock = (materials.maxOfOrNull { it.quantity } ?: 100.0).toFloat().coerceAtLeast(10f)
        materials.forEachIndexed { index, material ->
            val barHeight = (material.quantity.toFloat() / maxStock) * size.height
            val x = (index * 2f + 0.5f) * barWidth
            drawRect(color = if (material.quantity < 10) Color(0xFFBA1A1A) else primaryColor, topLeft = Offset(x, size.height - barHeight), size = Size(barWidth, barHeight))
        }
        drawLine(color = Color.Gray.copy(alpha = 0.5f), start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
    }
}

@Composable
fun PdfExportDashboardDialog(
    materials: List<Material>,
    projects: List<Project>,
    clients: List<Client>,
    consumedHistory: List<ConsumedLicense>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar Resumen PDF") },
        text = {
            Column {
                Text("Selecciona el tipo de reporte:")
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { PdfUtils.viewPdf(context, PdfGenerator.generateInventorySummary(context, materials)); onDismiss() }, modifier = Modifier.weight(1f)) { Text("Inventario") }
                    TextButton(onClick = { PdfUtils.viewPdf(context, PdfGenerator.generateProjectsSummary(context, projects)); onDismiss() }, modifier = Modifier.weight(1f)) { Text("Obras") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { PdfUtils.viewPdf(context, PdfGenerator.generateClientsSummary(context, clients)); onDismiss() }, modifier = Modifier.weight(1f)) { Text("Clientes") }
                    TextButton(onClick = { PdfUtils.viewPdf(context, PdfGenerator.generateConsumedLicenseSummary(context, consumedHistory)); onDismiss() }, modifier = Modifier.weight(1f)) { Text("Licencias") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun LicenseHistoryDashboardDialog(
    consumedHistory: List<ConsumedLicense>,
    onDismiss: () -> Unit
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historial de Licencias") },
        text = {
            Column {
                if (consumedHistory.isEmpty()) {
                    Text("No hay historial de licencias")
                } else {
                    consumedHistory.reversed().take(5).forEach { license ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = license.user, fontWeight = FontWeight.SemiBold)
                                Text(text = "Expira: ${if (license.expiryDate > 1000000000000L) SimpleDateFormat("dd/MM/yyyy", locale).format(Date(license.expiryDate)) else "N/A"}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
