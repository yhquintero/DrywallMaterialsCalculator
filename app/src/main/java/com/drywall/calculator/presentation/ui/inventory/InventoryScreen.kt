package com.drywall.calculator.presentation.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Print
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.drywall.calculator.data.local.entity.Material
import com.drywall.calculator.data.repository.InventoryRepository
import com.drywall.calculator.presentation.ui.components.ValidatedTextField
import androidx.compose.ui.platform.LocalConfiguration

import androidx.compose.ui.platform.LocalContext
import com.drywall.calculator.presentation.ui.pdf.PdfViewModel
import com.drywall.calculator.utils.PdfUtils
import com.drywall.calculator.utils.PdfGenerator

/** Opciones de exportación del reporte de inventario. */
enum class InventoryExportFilter(val label: String) {
    CON_EXISTENCIA("Con existencia"),
    AGOTADOS("Agotados"),
    SUBTOTAL_DESC("Subtotal: mayor a menor"),
    SUBTOTAL_ASC("Subtotal: menor a mayor"),
    UNIDADES_DESC("Unidades: mayor a menor"),
    UNIDADES_ASC("Unidades: menor a mayor")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    navController: NavController, 
    viewModel: InventoryViewModel = hiltViewModel(),
    pdfViewModel: PdfViewModel = hiltViewModel()
) {
    val materials by viewModel.materials.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val alerts by viewModel.lowStockAlerts.collectAsState()
    val profile by pdfViewModel.companyProfile.collectAsState()
    val context = LocalContext.current
    val generatedPdf by pdfViewModel.pdfFile.collectAsState(initial = null)
    val showStockCard by viewModel.showStockCard.collectAsState()
    val selectedMaterialForCard by viewModel.selectedMaterialForCard.collectAsState()
    val stockCardTransactions by viewModel.stockCardTransactions.collectAsState()
    val deleteResult by viewModel.deleteResult.collectAsState()
    val consumeResult by viewModel.consumeResult.collectAsState()
    val updateQuantityResult by viewModel.updateQuantityResult.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
    
    var showDialog by remember { mutableStateOf(false) }
    var showLogoAlert by remember { mutableStateOf(false) }
    var showZeroStockAlert by remember { mutableStateOf(false) }
    var showQuickUseModal by remember { mutableStateOf(false) }
    var quickUseMaterial by remember { mutableStateOf<Material?>(null) }
    var quickUseQty by remember { mutableStateOf("") }
    var quickUseReason by remember { mutableStateOf("") }
    var quickUseError by remember { mutableStateOf("") }
    var selectedMaterial by remember { mutableStateOf<Material?>(null) }
    var quantity by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var valeDate by remember { mutableStateOf("") }
    var valeDateError by remember { mutableStateOf(false) }
    var isEntry by remember { mutableStateOf(true) }
    val dayDateFormat = remember(locale) { java.text.SimpleDateFormat("dd/MM/yyyy", locale) }
    var pendingViewPdf by remember { mutableStateOf(false) }
    var showPageSizeDialog by remember { mutableStateOf(false) }
    var selectedPageSize by remember { mutableStateOf(PdfGenerator.PageSize.A4) }
    var selectedExportFilter by remember { mutableStateOf(InventoryExportFilter.CON_EXISTENCIA) }

    LaunchedEffect(generatedPdf) {
        if (generatedPdf != null && pendingViewPdf) {
            pendingViewPdf = false
            PdfUtils.viewPdf(context, generatedPdf!!)
        }
    }

    LaunchedEffect(showDialog) {
        if (showDialog && valeDate.isBlank()) {
            valeDate = dayDateFormat.format(java.util.Date())
        }
    }

    if (showLogoAlert) {
        AlertDialog(
            onDismissRequest = { showLogoAlert = false },
            title = { Text("Datos Faltantes") },
            text = { Text("Se debe poner primeramente el Logotipo u los Datos Fundamentales de la Empresa en la sección 'Empresa' para generar reportes profesionales.") },
            confirmButton = {
                Button(onClick = { 
                    showLogoAlert = false
                    navController.navigate("company")
                }) { Text("Ir a Empresa") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoAlert = false }) { Text("Cerrar") }
            }
        )
    }

    if (showZeroStockAlert) {
        AlertDialog(
            onDismissRequest = { showZeroStockAlert = false },
            title = { Text("Sin Stock Disponible", color = MaterialTheme.colorScheme.error) },
            text = { Text("No se puede realizar una salida porque el stock actual es 0. Primero debe registrar una entrada de material.") },
            confirmButton = {
                Button(onClick = { showZeroStockAlert = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Entendido")
                }
            }
        )
    }

    consumeResult?.let { result ->
        if (result is InventoryRepository.ConsumeResult.Error) {
            AlertDialog(
                onDismissRequest = { viewModel.clearConsumeResult() },
                title = { Text("Error de Stock", color = MaterialTheme.colorScheme.error) },
                text = { Text(result.message) },
                confirmButton = {
                    Button(onClick = { viewModel.clearConsumeResult() }) {
                        Text("Entendido")
                    }
                }
            )
        }
    }

    if (showQuickUseModal && quickUseMaterial != null) {
        AlertDialog(
            onDismissRequest = { showQuickUseModal = false; quickUseQty = ""; quickUseReason = ""; quickUseError = "" },
            title = { Text("Utilizar Material: ${quickUseMaterial?.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stock actual: ${String.format(locale, "%,.2f", quickUseMaterial?.quantity ?: 0.0)} ${quickUseMaterial?.unitType}",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = quickUseQty,
                        onValueChange = { 
                            val clean = it.replace("\n", "").replace("\r", "")
                            if (clean.isEmpty() || clean.toDoubleOrNull() != null) quickUseQty = clean
                            quickUseError = ""
                        },
                        label = { Text("Cantidad a utilizar") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = quickUseReason,
                        onValueChange = { quickUseReason = it },
                        label = { Text("Motivo / Obra / Proyecto") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (quickUseError.isNotEmpty()) {
                        Text(quickUseError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val q = quickUseQty.toDoubleOrNull() ?: 0.0
                    if (q <= 0) {
                        quickUseError = "La cantidad debe ser mayor a 0"
                        return@Button
                    }
                    val stock = quickUseMaterial?.quantity ?: 0.0
                    if (q > stock) {
                        quickUseError = "No hay stock suficiente. Disponible: ${String.format(locale, "%,.2f", stock)}"
                        return@Button
                    }
                    if (quickUseReason.isBlank()) {
                        quickUseError = "Debe indicar el motivo de uso"
                        return@Button
                    }
                    viewModel.consumeStock(quickUseMaterial!!.id, q, quickUseReason)
                    showQuickUseModal = false
                    quickUseQty = ""
                    quickUseReason = ""
                    quickUseError = ""
                }) { Text("Confirmar Uso") }
            },
            dismissButton = {
                TextButton(onClick = { showQuickUseModal = false; quickUseQty = ""; quickUseReason = ""; quickUseError = "" }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Header Fijo
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it.replace("\n", "").replace("\r", "")) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Buscar material...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                        
                        
                        IconButton(onClick = { showPageSizeDialog = true }) {
                            Icon(Icons.Default.Print, contentDescription = "Configurar Hoja y Exportar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (showPageSizeDialog) {
                AlertDialog(
                    onDismissRequest = { showPageSizeDialog = false },
                    title = { Text("Exportar Reporte de Inventario") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            var pageSizeExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = pageSizeExpanded,
                                onExpandedChange = { pageSizeExpanded = !pageSizeExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedPageSize.label,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Tamaño de hoja") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pageSizeExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = pageSizeExpanded,
                                    onDismissRequest = { pageSizeExpanded = false }
                                ) {
                                    PdfGenerator.PageSize.entries.forEach { ps ->
                                        DropdownMenuItem(
                                            text = { Text(ps.label) },
                                            onClick = { selectedPageSize = ps; pageSizeExpanded = false }
                                        )
                                    }
                                }
                            }

                            var filterExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = filterExpanded,
                                onExpandedChange = { filterExpanded = !filterExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedExportFilter.label,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Opciones de exportación") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = filterExpanded,
                                    onDismissRequest = { filterExpanded = false }
                                ) {
                                    InventoryExportFilter.entries.forEach { f ->
                                        DropdownMenuItem(
                                            text = { Text(f.label) },
                                            onClick = { selectedExportFilter = f; filterExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showPageSizeDialog = false
                            if (profile?.logoUri == null || profile?.businessName.isNullOrBlank()) {
                                showLogoAlert = true
                            } else {
                                pendingViewPdf = true
                                val filtered = when (selectedExportFilter) {
                                    InventoryExportFilter.CON_EXISTENCIA -> materials.filter { it.quantity > 0 }
                                    InventoryExportFilter.AGOTADOS -> materials.filter { it.quantity <= 0 }
                                    else -> materials
                                }
                                val sortedMaterials = when (selectedExportFilter) {
                                    InventoryExportFilter.SUBTOTAL_DESC -> filtered.sortedByDescending { it.quantity * it.purchasePrice }
                                    InventoryExportFilter.SUBTOTAL_ASC -> filtered.sortedBy { it.quantity * it.purchasePrice }
                                    InventoryExportFilter.UNIDADES_DESC -> filtered.sortedByDescending { it.quantity }
                                    InventoryExportFilter.UNIDADES_ASC -> filtered.sortedBy { it.quantity }
                                    else -> filtered.sortedWith(compareByDescending<Material> { it.quantity > 0 }.thenBy { it.name })
                                }
                                val items = sortedMaterials.map { m ->
                                    PdfGenerator.InvoiceItem(
                                        name = m.name, 
                                        qtyPerM2 = 0.0, 
                                        totalQty = m.quantity, 
                                        unitPrice = m.purchasePrice, 
                                        totalPrice = m.quantity * m.purchasePrice, 
                                        unitType = m.unitType,
                                        groupName = if (m.quantity > 0) "Con Existencia" else "Agotados"
                                    )
                                }
                                pdfViewModel.generateQuotation(context, "REPORTE DE INVENTARIO", "Control Interno", 0.0, items, items.sumOf { it.totalPrice }, pageSize = selectedPageSize)
                            }
                        }) { Text("Generar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPageSizeDialog = false }) { Text("Cancelar") }
                    }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
            ) {
                if (alerts.isNotEmpty() && searchQuery.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Alertas de Stock Bajo", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                                alerts.take(3).forEach { alert ->
                                    Text(alert, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                                if (alerts.size > 3) {
                                    Text("... y ${alerts.size - 3} más", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                
                items(materials) { material ->
                    var quickQty by remember { mutableStateOf("") }
                    
                    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val precision = com.drywall.calculator.presentation.theme.LocalDecimalPrecision.current
                                    Text(material.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Stock: ${String.format(LocalConfiguration.current.locales[0], "%,.${precision}f", material.quantity)} ${material.unitType}",
                                        color = if (material.quantity <= 1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                
                                Row {
                                    FilledIconButton(
                                        onClick = { viewModel.openStockCard(material) },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                    ) {
                                        Icon(Icons.Default.TableChart, contentDescription = "Tarjeta de Estiba", tint = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    FilledIconButton(
                                        onClick = {
                                            selectedMaterial = material
                                            isEntry = true
                                            showDialog = true
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Entrada")
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    FilledIconButton(
                                        onClick = {
                                            selectedMaterial = material
                                            isEntry = false
                                            showDialog = true
                                        },
                                        enabled = material.quantity > 0,
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = Color.White,
                                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Salida")
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ValidatedTextField(
                                    value = quickQty,
                                    onValueChange = { 
                                        val clean = it.replace("\n", "").replace("\r", "")
                                        if (clean.isEmpty() || clean.toDoubleOrNull() != null) quickQty = clean 
                                    },
                                    label = "Cant. a utilizar",
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                Button(
                                    onClick = {
                                        val q = quickQty.toDoubleOrNull() ?: 0.0
                                        if (q > 0 && q <= material.quantity) {
                                            quickUseMaterial = material
                                            quickUseQty = quickQty
                                            showQuickUseModal = true
                                        }
                                    },
                                    enabled = quickQty.isNotEmpty() && (quickQty.toDoubleOrNull() ?: 0.0) > 0 && (quickQty.toDoubleOrNull() ?: 0.0) <= material.quantity,
                                    modifier = Modifier.height(40.dp),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text("Utilizar", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog && selectedMaterial != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (isEntry) "Vale de Entrada: ${selectedMaterial?.name}" else "Vale de Salida: ${selectedMaterial?.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.drywall.calculator.presentation.ui.components.DateField(
                        value = valeDate,
                        onValueChange = {
                            valeDate = it
                            valeDateError = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isError = valeDateError
                    )
                    ValidatedTextField(
                        value = quantity,
                        onValueChange = { 
                            val clean = it.replace("\n", "").replace("\r", "")
                            if (clean.isEmpty() || clean.toDoubleOrNull() != null) quantity = clean 
                        },
                        label = "Cantidad",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    ValidatedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = "Motivo / Obra / Proveedor",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!isEntry) {
                        Text(
                            "Stock máximo disponible: ${selectedMaterial?.quantity}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = quantity.toDoubleOrNull() ?: 0.0
                        val parsedDate = if (valeDate.isBlank()) java.util.Date() else try {
                            java.text.SimpleDateFormat("dd/MM/yyyy", locale).apply { isLenient = false }.parse(valeDate)
                        } catch (e: Exception) { null }
                        if (parsedDate == null) {
                            valeDateError = true
                            return@Button
                        }
                        if (qty > 0) {
                            if (isEntry) {
                                viewModel.addStock(selectedMaterial!!.id, qty, reason, parsedDate)
                            } else {
                                if (qty <= selectedMaterial!!.quantity) {
                                    viewModel.consumeStock(selectedMaterial!!.id, qty, reason, parsedDate)
                                } else {
                                    // Potentially show an error message
                                }
                            }
                            showDialog = false
                            quantity = ""
                            reason = ""
                            valeDate = ""
                            valeDateError = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isEntry) Color(0xFFD32F2F) else Color(0xFF1976D2),
                        contentColor = Color.White
                    )
                ) { Text("Confirmar") }
            },
            dismissButton = {
                Button(
                    onClick = { showDialog = false; valeDate = ""; valeDateError = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White)
                ) { Text("Cancelar") }
            }
        )
    }

    if (showStockCard && selectedMaterialForCard != null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            StockCardScreen(
                material = selectedMaterialForCard!!,
                transactions = stockCardTransactions,
                onBack = { viewModel.closeStockCard() },
                onDeleteTransaction = { txId, matId -> viewModel.deleteTransaction(txId, matId) },
                onUpdateReason = { txId, newReason -> viewModel.updateTransactionReason(txId, newReason) },
                onUpdateQuantity = { txId, matId, newQty -> viewModel.updateTransactionQuantity(txId, matId, newQty) },
                onAddEntry = { qty, reason, date -> viewModel.addStock(selectedMaterialForCard!!.id, qty, reason, date) },
                onConsumeStock = { qty, reason, date -> viewModel.consumeStock(selectedMaterialForCard!!.id, qty, reason, date) },
                deleteResult = deleteResult,
                onDeleteResultDismiss = { viewModel.clearDeleteResult() },
                updateQuantityResult = updateQuantityResult,
                onUpdateQuantityResultDismiss = { viewModel.clearUpdateQuantityResult() }
            )
        }
    }
}
