package com.drywall.calculator.presentation.ui.inventory

import android.graphics.Canvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import com.drywall.calculator.data.local.entity.InventoryTransaction
import com.drywall.calculator.data.local.entity.Material
import com.drywall.calculator.data.repository.InventoryRepository
import com.drywall.calculator.utils.PdfUtils
import com.drywall.calculator.presentation.ui.components.ValidatedTextField
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import android.graphics.Color as AndroidColor

enum class PageSize(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", 595, 842),
    CARTA("Carta", 612, 792),
    OFICIO("Oficio", 612, 1008),
    LEGAL("Legal", 612, 1008)
}

internal val StockCardBlue = Color(0xFF1976D2)
internal val StockCardRed = Color(0xFFD32F2F)

/** Filtro de rango de tiempo para exportación de vales. */
enum class BalancePeriod(val label: String, val addField: Int, val addAmount: Int) {
    DIARIO("Balance Diario", java.util.Calendar.DAY_OF_YEAR, -1),
    SEMANAL("Balance Semanal", java.util.Calendar.WEEK_OF_YEAR, -1),
    MENSUAL("Balance Mensual", java.util.Calendar.MONTH, -1),
    TRIMESTRAL("Balance Trimestral", java.util.Calendar.MONTH, -3),
    SEMESTRAL("Balance Semestral", java.util.Calendar.MONTH, -6),
    ANUAL("Balance Anual", java.util.Calendar.YEAR, -1);

    /** Fecha de inicio del rango, contada hacia atrás desde ahora. */
    fun startDate(now: Date = Date()): Date {
        val cal = java.util.Calendar.getInstance()
        cal.time = now
        cal.add(addField, addAmount)
        return cal.time
    }
}

private fun formatDateDigits(input: String): String {
    val digits = input.filter { it.isDigit() }.take(8)
    val sb = StringBuilder()
    for (i in digits.indices) {
        if (i == 2 || i == 4) sb.append('/')
        sb.append(digits[i])
    }
    return sb.toString()
}

private fun parseDayDate(text: String): Date? {
    return try {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }.parse(text)
    } catch (e: Exception) {
        null
    }
}

/** Devuelve el inicio del día (00:00:00) para comparar solo por fecha. */
private fun dayStart(date: Date): Date {
    val cal = java.util.Calendar.getInstance()
    cal.time = date
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.time
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockCardScreen(
    material: Material,
    transactions: List<InventoryTransaction>,
    onBack: () -> Unit,
    onDeleteTransaction: ((transactionId: String, materialId: String) -> Unit)? = null,
    onUpdateReason: ((transactionId: String, newReason: String) -> Unit)? = null,
    onUpdateQuantity: ((transactionId: String, materialId: String, newQuantity: Double) -> Unit)? = null,
    onAddEntry: ((quantity: Double, reason: String, date: Date) -> Unit)? = null,
    onConsumeStock: ((quantity: Double, reason: String, date: Date) -> Unit)? = null,
    deleteResult: InventoryRepository.DeleteResult? = null,
    onDeleteResultDismiss: (() -> Unit)? = null,
    updateQuantityResult: InventoryRepository.UpdateQuantityResult? = null,
    onUpdateQuantityResultDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("dd/MM/yyyy", locale) }
    var selectedPageSize by remember { mutableStateOf(PageSize.A4) }
    var showPageSizeDialog by remember { mutableStateOf(false) }

    var editingTransaction by remember { mutableStateOf<InventoryTransaction?>(null) }
    var editReason by remember { mutableStateOf("") }
    var editCantidad by remember { mutableStateOf("") }
    var deletingTransaction by remember { mutableStateOf<InventoryTransaction?>(null) }
    var showZeroStockAlert by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf("") }

    var showAddEntryDialog by remember { mutableStateOf(false) }
    var showAddExitDialog by remember { mutableStateOf(false) }
    var newQuantity by remember { mutableStateOf("") }
    var newReason by remember { mutableStateOf("") }
    var newDate by remember { mutableStateOf("") }
    var actionError by remember { mutableStateOf("") }

    val dayDateFormat = remember(locale) { SimpleDateFormat("dd/MM/yyyy", locale) }
    var showExportRangeDialog by remember { mutableStateOf(false) }
    var showCustomRangeDialog by remember { mutableStateOf(false) }
    var customStartDate by remember { mutableStateOf("") }
    var customEndDate by remember { mutableStateOf("") }
    var customRangeError by remember { mutableStateOf("") }

    // Compute running balance chronologically (oldest first)
    val sortedTransactions = remember(transactions) { transactions.sortedBy { it.date.time } }
    var runningBalanceTotal = 0.0
    val rows = remember(sortedTransactions) {
        runningBalanceTotal = 0.0
        sortedTransactions.map { tx ->
            val tipo = if (tx.quantityChange > 0) "Entrada" else "Salida"
            val qty = abs(tx.quantityChange)
            runningBalanceTotal += tx.quantityChange
            StockCardRow(
                id = tx.id,
                fecha = dateFormat.format(tx.date),
                tipo = tipo,
                cantidad = qty,
                motivo = tx.reason,
                saldo = runningBalanceTotal
            )
        }
    }

    val totalEntradas = remember(rows) { rows.filter { it.tipo == "Entrada" }.sumOf { it.cantidad } }
    val totalSalidas = remember(rows) { rows.filter { it.tipo == "Salida" }.sumOf { it.cantidad } }
    
    // Real-time stock based on transactions
    val currentStockValue = if (rows.isNotEmpty()) rows.last().saldo else material.quantity

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

    LaunchedEffect(showAddEntryDialog, showAddExitDialog) {
        if ((showAddEntryDialog || showAddExitDialog) && newDate.isBlank()) {
            newDate = dayDateFormat.format(Date())
        }
    }

    // Add Entry / Exit Dialog
    if (showAddEntryDialog || showAddExitDialog) {
        val isEntryAction = showAddEntryDialog
        AlertDialog(
            onDismissRequest = { 
                showAddEntryDialog = false; showAddExitDialog = false
                newQuantity = ""; newReason = ""; newDate = ""; actionError = "" 
            },
            title = { Text(if (isEntryAction) "Nuevo Vale de Entrada" else "Nuevo Vale de Salida") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isEntryAction) "Agregar entrada de material al inventario" 
                        else "Registrar salida de material del inventario", 
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    com.drywall.calculator.presentation.ui.components.DateField(
                        value = newDate,
                        onValueChange = {
                            newDate = it
                            actionError = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isError = actionError.contains("Fecha", ignoreCase = true) ||
                            actionError.contains("entrada del material", ignoreCase = true)
                    )
                    OutlinedTextField(
                        value = newQuantity,
                        onValueChange = { 
                            val clean = it.replace("\n", "").replace("\r", "")
                            if (clean.isEmpty() || clean.toDoubleOrNull() != null) newQuantity = clean
                            actionError = ""
                        },
                        label = { Text("Cantidad") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = newReason,
                        onValueChange = { newReason = it },
                        label = { Text("Motivo / Referencia") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (actionError.isNotEmpty()) {
                        Text(actionError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Stock actual: ${String.format(locale, "%,.2f", currentStockValue)} ${material.unitType}", 
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    
                    val inputQty = newQuantity.toDoubleOrNull() ?: 0.0
                    val resultStock = if (isEntryAction) currentStockValue + inputQty else currentStockValue - inputQty
                    
                    Text("Nuevo stock será: ${String.format(locale, "%,.2f", resultStock)} ${material.unitType}",
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, 
                        color = if (resultStock < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = newQuantity.toDoubleOrNull()
                        if (qty == null || qty <= 0) {
                            actionError = "La cantidad debe ser mayor a 0"
                            return@Button
                        }
                        if (!isEntryAction && qty > currentStockValue) {
                            actionError = "Stock insuficiente para realizar esta salida"
                            return@Button
                        }
                        val parsedDate = if (newDate.isBlank()) Date() else parseDayDate(newDate)
                        if (parsedDate == null) {
                            actionError = "Fecha inválida. Use dd/mm/aaaa"
                            return@Button
                        }
                        if (!isEntryAction) {
                            val firstEntryDate = sortedTransactions
                                .filter { it.quantityChange > 0 }
                                .minByOrNull { it.date.time }?.date
                            if (firstEntryDate != null && parsedDate.before(dayStart(firstEntryDate))) {
                                actionError = "La fecha de salida no puede ser menor a la fecha de entrada del material (" +
                                    dayDateFormat.format(firstEntryDate) + ")"
                                return@Button
                            }
                        }

                        if (isEntryAction) {
                            onAddEntry?.invoke(qty, newReason.ifBlank { "Vale de Entrada" }, parsedDate)
                        } else {
                            onConsumeStock?.invoke(qty, newReason.ifBlank { "Vale de Salida" }, parsedDate)
                        }

                        showAddEntryDialog = false
                        showAddExitDialog = false
                        newQuantity = ""
                        newReason = ""
                        newDate = ""
                        actionError = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEntryAction) StockCardBlue else StockCardRed,
                        contentColor = Color.White
                    )
                ) { Text("Confirmar") }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showAddEntryDialog = false; showAddExitDialog = false
                        newQuantity = ""; newReason = ""; newDate = ""; actionError = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StockCardRed, contentColor = Color.White)
                ) { Text("Cancelar") }
            }
        )
    }

    // Delete result dialog
    val currentDeleteResult = deleteResult
    if (currentDeleteResult != null) {
        val isError = currentDeleteResult is InventoryRepository.DeleteResult.Error
        AlertDialog(
            onDismissRequest = { onDeleteResultDismiss?.invoke() },
            title = {
                Text(
                    if (isError) "No se puede eliminar" else "Eliminado",
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column {
                    when (currentDeleteResult) {
                        is InventoryRepository.DeleteResult.Success -> Text(currentDeleteResult.message)
                        is InventoryRepository.DeleteResult.Error -> {
                            Text(currentDeleteResult.message)
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Stock actual: ${String.format(locale, "%,.2f", currentDeleteResult.currentStock)} ${material.unitType}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("Valor de entrada a eliminar: ${String.format(locale, "%,.2f", currentDeleteResult.quantityToRevert)} ${material.unitType}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Para eliminar esta entrada, primero debe eliminar las salidas que consumieron este stock.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onDeleteResultDismiss?.invoke() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Entendido")
                }
            }
        )
    }

    // Update quantity result dialog
    val currentUpdateResult = updateQuantityResult
    if (currentUpdateResult != null) {
        val isError = currentUpdateResult is InventoryRepository.UpdateQuantityResult.Error
        AlertDialog(
            onDismissRequest = { onUpdateQuantityResultDismiss?.invoke() },
            title = {
                Text(
                    if (isError) "No se puede actualizar" else "Actualizado",
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column {
                    when (currentUpdateResult) {
                        is InventoryRepository.UpdateQuantityResult.Success -> Text(currentUpdateResult.message)
                        is InventoryRepository.UpdateQuantityResult.Error -> {
                            Text(currentUpdateResult.message)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Stock actual: ${String.format(locale, "%,.2f", currentUpdateResult.currentStock)} ${material.unitType}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onUpdateQuantityResultDismiss?.invoke() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Entendido")
                }
            }
        )
    }

    if (editingTransaction != null) {
        val tx = editingTransaction!!
        val isEntrada = tx.quantityChange > 0
        // Real-time stock preview during editing
        val originalQty = abs(tx.quantityChange)
        val editedQty = editCantidad.toDoubleOrNull() ?: originalQty
        val diff = editedQty - originalQty
        val previewStock = if (isEntrada) {
            currentStockValue + diff
        } else {
            currentStockValue - diff
        }
        
        AlertDialog(
            onDismissRequest = { editingTransaction = null; editReason = ""; editCantidad = ""; editError = "" },
            title = { Text("Editar Transacción") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tipo: ${if (isEntrada) "Entrada" else "Salida"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                        color = if (isEntrada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    OutlinedTextField(
                        value = editCantidad,
                        onValueChange = { 
                            val clean = it.replace("\n", "").replace("\r", "")
                            if (clean.isEmpty() || clean.toDoubleOrNull() != null) editCantidad = clean
                            editError = ""
                        },
                        label = { Text("Cantidad") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = editReason,
                        onValueChange = { editReason = it },
                        label = { Text("Motivo") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (editError.isNotEmpty()) {
                        Text(editError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    // Real-time stock preview
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stock actual: ${String.format(locale, "%,.2f", currentStockValue)} ${material.unitType}", 
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text("Vista previa: ${String.format(locale, "%,.2f", previewStock)} ${material.unitType}", 
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, 
                            color = if (previewStock < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    // Quick add Vale de Entrada/Salida buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showAddEntryDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Agregar", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Entrada", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { showAddExitDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Quitar", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Salida", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newQty = editCantidad.toDoubleOrNull()
                    if (newQty == null || newQty <= 0) {
                        editError = "La cantidad debe ser mayor a 0"
                        return@Button
                    }
                    val diffQty = newQty - originalQty
                    
                    if (isEntrada) {
                        val newStock = material.quantity + diffQty
                        if (newStock < 0) {
                            editError = "No se puede reducir: el stock quedaría negativo"
                            return@Button
                        }
                    } else {
                        if (diffQty > 0) {
                            val newStock = material.quantity - diffQty
                            if (newStock < 0) {
                                editError = "No se puede aumentar la salida: stock insuficiente"
                                return@Button
                            }
                        }
                    }
                    
                    // Update reason
                    onUpdateReason?.invoke(tx.id, editReason)
                    // Update quantity if changed
                    if (newQty != originalQty) {
                        onUpdateQuantity?.invoke(tx.id, material.id, newQty)
                    }
                    editingTransaction = null
                    editReason = ""
                    editCantidad = ""
                    editError = ""
                }, colors = ButtonDefaults.buttonColors(containerColor = StockCardBlue, contentColor = Color.White)) { Text("Guardar") }
            },
            dismissButton = {
                Button(
                    onClick = { editingTransaction = null; editReason = ""; editCantidad = ""; editError = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = StockCardRed, contentColor = Color.White)
                ) { Text("Cancelar") }
            }
        )
    }

    if (deletingTransaction != null) {
        AlertDialog(
            onDismissRequest = { deletingTransaction = null },
            title = { Text("Eliminar Transacción") },
            text = { Text("¿Está seguro de eliminar esta transacción? Se revertirá el stock correspondiente.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTransaction?.invoke(deletingTransaction!!.id, material.id)
                        deletingTransaction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deletingTransaction = null }) { Text("Cancelar") }
            }
        )
    }

    // Real-time stock for header
    val displayStock = if (editingTransaction != null) {
        val tx = editingTransaction!!
        val isEntrada = tx.quantityChange > 0
        val originalQty = abs(tx.quantityChange)
        val editedQty = editCantidad.toDoubleOrNull() ?: originalQty
        val diffVal = editedQty - originalQty
        if (isEntrada) currentStockValue + diffVal else currentStockValue - diffVal
    } else {
        currentStockValue
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopAppBar(
            title = { Text("Tarjeta de Estiba", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showPageSizeDialog = true }) {
                        Icon(Icons.Default.Print, contentDescription = "Tamaño de hoja", tint = MaterialTheme.colorScheme.primary)
                    }
                    
                    if (showPageSizeDialog) {
                        AlertDialog(
                            onDismissRequest = { showPageSizeDialog = false },
                            title = { Text("Tamaño de hoja") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Seleccione el formato de papel para la tarjeta de estiba:")
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
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = pageSizeExpanded)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = pageSizeExpanded,
                                            onDismissRequest = { pageSizeExpanded = false }
                                        ) {
                                            PageSize.entries.forEach { ps ->
                                                DropdownMenuItem(
                                                    text = { Text(ps.label) },
                                                    onClick = {
                                                        selectedPageSize = ps
                                                        pageSizeExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    showPageSizeDialog = false
                                    val file = generateStockCardPdf(
                                        context, material, rows, totalEntradas, totalSalidas,
                                        selectedPageSize, locale
                                    )
                                    PdfUtils.viewPdf(context, file)
                                }) { Text("Generar") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPageSizeDialog = false }) { Text("Cancelar") }
                            }
                        )
                    }
                }
                
                // Entry button
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp,
                    tonalElevation = 4.dp
                ) {
                    IconButton(onClick = { showAddEntryDialog = true }) {
                        Icon(
                            Icons.Default.Add, 
                            contentDescription = "Entrada (+)", 
                            tint = MaterialTheme.colorScheme.onPrimary, 
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Exit button only - enabled when stock > 0
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = if (currentStockValue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                    shadowElevation = 4.dp,
                    tonalElevation = 4.dp
                ) {
                    IconButton(
                        onClick = { if (currentStockValue > 0) showAddExitDialog = true },
                        enabled = currentStockValue > 0
                    ) {
                        Icon(
                            Icons.Default.Remove, 
                            contentDescription = "Salida (-)", 
                            tint = if (currentStockValue > 0) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), 
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(onClick = { showExportRangeDialog = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Exportar por período", tint = MaterialTheme.colorScheme.secondary)
                }

                IconButton(onClick = {
                    val file = generateStockCardPdf(
                        context, material, rows, totalEntradas, totalSalidas,
                        selectedPageSize, locale
                    )
                    PdfUtils.viewPdf(context, file)
                }) {
                    Icon(Icons.Default.Visibility, contentDescription = "Visualizar PDF", tint = MaterialTheme.colorScheme.tertiary)
                }
            }
        )

        if (showExportRangeDialog) {
            AlertDialog(
                onDismissRequest = { showExportRangeDialog = false },
                title = { Text("Exportar Balance por Período") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Seleccione el rango de tiempo. Se exportarán solo los vales dentro de dicho período.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        BalancePeriod.entries.forEach { period ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showExportRangeDialog = false
                                        val start = period.startDate()
                                        val filtered = sortedTransactions.filter { it.date.time >= start.time }
                                        val file = generateStockCardPdfFromTransactions(
                                            context, material, filtered, selectedPageSize, locale,
                                            period.label, dayDateFormat.format(start), dayDateFormat.format(Date())
                                        )
                                        PdfUtils.viewPdf(context, file)
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(period.label, style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showExportRangeDialog = false
                                    customStartDate = ""
                                    customEndDate = ""
                                    customRangeError = ""
                                    showCustomRangeDialog = true
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Rango personalizado…", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    Button(
                        onClick = { showExportRangeDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = StockCardRed, contentColor = Color.White)
                    ) { Text("Cancelar") }
                }
            )
        }

        if (showCustomRangeDialog) {
            AlertDialog(
                onDismissRequest = { showCustomRangeDialog = false },
                title = { Text("Rango personalizado") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Seleccione la fecha inicial y final. Se exportarán los vales dentro del rango.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        com.drywall.calculator.presentation.ui.components.DateField(
                            value = customStartDate,
                            onValueChange = { customStartDate = it; customRangeError = "" },
                            label = "Desde (dd/mm/aaaa)",
                            modifier = Modifier.fillMaxWidth(),
                            isError = customRangeError.isNotEmpty()
                        )
                        com.drywall.calculator.presentation.ui.components.DateField(
                            value = customEndDate,
                            onValueChange = { customEndDate = it; customRangeError = "" },
                            label = "Hasta (dd/mm/aaaa)",
                            modifier = Modifier.fillMaxWidth(),
                            isError = customRangeError.isNotEmpty()
                        )
                        if (customRangeError.isNotEmpty()) {
                            Text(customRangeError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val start = parseDayDate(customStartDate)
                            val end = parseDayDate(customEndDate)
                            when {
                                start == null || end == null -> customRangeError = "Fechas inválidas. Use dd/mm/aaaa"
                                end.before(start) -> customRangeError = "La fecha final no puede ser menor a la inicial"
                                else -> {
                                    val startMillis = dayStart(start).time
                                    val endCal = java.util.Calendar.getInstance().apply {
                                        time = end
                                        set(java.util.Calendar.HOUR_OF_DAY, 23)
                                        set(java.util.Calendar.MINUTE, 59)
                                        set(java.util.Calendar.SECOND, 59)
                                        set(java.util.Calendar.MILLISECOND, 999)
                                    }
                                    val endMillis = endCal.timeInMillis
                                    val filtered = sortedTransactions.filter { it.date.time in startMillis..endMillis }
                                    val file = generateStockCardPdfFromTransactions(
                                        context, material, filtered, selectedPageSize, locale,
                                        "Rango personalizado", dayDateFormat.format(start), dayDateFormat.format(end)
                                    )
                                    showCustomRangeDialog = false
                                    PdfUtils.viewPdf(context, file)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StockCardBlue, contentColor = Color.White)
                    ) { Text("Generar") }
                },
                dismissButton = {
                    Button(
                        onClick = { showCustomRangeDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = StockCardRed, contentColor = Color.White)
                    ) { Text("Cancelar") }
                }
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Material header card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(material.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Stock actual: ${String.format(locale, "%,.2f", displayStock)} ${material.unitType}",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                        color = if (displayStock <= 1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    if (editingTransaction != null) {
                        Text("Vista previa en tiempo real (editando)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontally scrollable table
            val minTableWidth = 660.dp
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier
                            .width(minTableWidth)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Table header
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Id.", modifier = Modifier.width(35.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("Fecha", modifier = Modifier.width(115.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("Entrada", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                            Text("Salida", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Motivo / Referencia", modifier = Modifier.width(160.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Saldo", modifier = Modifier.width(75.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                            if (onDeleteTransaction != null) {
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Acciones", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // All rows
                        rows.forEachIndexed { index, row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}", modifier = Modifier.width(35.dp), style = MaterialTheme.typography.bodySmall)
                                Text(row.fecha, modifier = Modifier.width(115.dp), style = MaterialTheme.typography.bodySmall)
                                if (row.tipo == "Entrada") {
                                    Text(
                                        String.format(locale, "%,.2f", row.cantidad),
                                        modifier = Modifier.width(70.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.End,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("-", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                                } else {
                                    Text("-", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                                    Text(
                                        String.format(locale, "%,.2f", row.cantidad),
                                        modifier = Modifier.width(70.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.End,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                // Centered Reason/Motivo
                                Column(modifier = Modifier.width(160.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        row.motivo,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    String.format(locale, "%,.2f", row.saldo),
                                    modifier = Modifier.width(75.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                    fontWeight = FontWeight.Bold
                                )
                                if (onDeleteTransaction != null) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Row(modifier = Modifier.width(80.dp), horizontalArrangement = Arrangement.Center) {
                                        IconButton(
                                            onClick = {
                                                editingTransaction = sortedTransactions.getOrNull(index)
                                                editReason = row.motivo
                                                editCantidad = String.format(locale, "%,.2f", row.cantidad)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(
                                            onClick = { deletingTransaction = sortedTransactions.getOrNull(index) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }

                // Summary footer
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Entradas:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(String.format(locale, "%,.2f", totalEntradas), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Salidas:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            Text(String.format(locale, "%,.2f", totalSalidas), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Saldo Final:", style = MaterialTheme.typography.labelSmall)
                            Text(String.format(locale, "%,.2f", currentStockValue), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

data class StockCardRow(
    val id: String,
    val fecha: String,
    val tipo: String,
    val cantidad: Double,
    val motivo: String,
    val saldo: Double
)

private fun generateStockCardPdfFromTransactions(
    context: android.content.Context,
    material: Material,
    transactions: List<InventoryTransaction>,
    pageSize: PageSize,
    locale: Locale,
    periodLabel: String,
    rangeStart: String,
    rangeEnd: String
): File {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", locale)
    val sorted = transactions.sortedBy { it.date.time }
    var running = 0.0
    val rows = sorted.map { tx ->
        running += tx.quantityChange
        StockCardRow(
            id = tx.id,
            fecha = dateFormat.format(tx.date),
            tipo = if (tx.quantityChange > 0) "Entrada" else "Salida",
            cantidad = abs(tx.quantityChange),
            motivo = tx.reason,
            saldo = running
        )
    }
    val totalEntradas = rows.filter { it.tipo == "Entrada" }.sumOf { it.cantidad }
    val totalSalidas = rows.filter { it.tipo == "Salida" }.sumOf { it.cantidad }
    return generateStockCardPdf(
        context, material, rows, totalEntradas, totalSalidas, pageSize, locale,
        subtitleOverride = "$periodLabel  |  Rango: $rangeStart - $rangeEnd"
    )
}

private fun generateStockCardPdf(
    context: android.content.Context,
    material: Material,
    rows: List<StockCardRow>,
    totalEntradas: Double,
    totalSalidas: Double,
    pageSize: PageSize,
    locale: Locale,
    subtitleOverride: String? = null
): File {
    val pdfDocument = PdfDocument()
    val paint = AndroidPaint().apply { isAntiAlias = true; textSize = 9f; color = AndroidColor.BLACK }
    val headerPaint = AndroidPaint().apply { isAntiAlias = true; textSize = 9f; color = AndroidColor.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val titlePaint = AndroidPaint().apply { isAntiAlias = true; textSize = 14f; color = AndroidColor.BLACK; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val subtitlePaint = AndroidPaint().apply { isAntiAlias = true; textSize = 11f; color = AndroidColor.parseColor("#333333"); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

    val pageWidth = pageSize.widthPt
    val pageHeight = pageSize.heightPt
    val margin = 40f
    val contentWidth = pageWidth - 2 * margin
    val rowHeight = 16f
    val headerY = 40f

    var pageNumber = 1
    var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas
    var y = headerY

    // Title
    val title = "TARJETA DE ESTIBA - ${material.name.uppercase()}"
    canvas.drawText(title, margin, y, titlePaint)
    y += 18f
    canvas.drawText("Material: ${material.name} | Unidad: ${material.unitType} | Stock Actual: ${String.format(locale, "%,.2f", material.quantity)}", margin, y, subtitlePaint)
    y += 14f
    if (subtitleOverride != null) {
        canvas.drawText(subtitleOverride, margin, y, subtitlePaint)
        y += 14f
    }
    canvas.drawText("Fecha de impresión: ${SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(Date())}", margin, y, paint)
    y += 16f

    // Column positions
    val colId = margin
    val colFecha = margin + contentWidth * 0.06f
    val colEntrada = margin + contentWidth * 0.30f
    val colSalida = margin + contentWidth * 0.46f
    val colMotivo = margin + contentWidth * 0.60f
    val colSaldo = margin + contentWidth * 0.82f

    fun drawTableHeader() {
        // Header background
        val headerBgPaint = AndroidPaint().apply { color = AndroidColor.parseColor("#1976D2") }
        canvas.drawRect(margin, y - 10f, margin + contentWidth, y + 5f, headerBgPaint)

        headerPaint.textSize = 9f
        canvas.drawText("Id.", colId, y, headerPaint)
        canvas.drawText("Fecha", colFecha, y, headerPaint)
        canvas.drawText("Entrada", colEntrada, y, headerPaint)
        canvas.drawText("Salida", colSalida, y, headerPaint)
        canvas.drawText("Motivo", colMotivo, y, headerPaint)
        canvas.drawText("Saldo", colSaldo, y, headerPaint)
        y += 15f
    }

    drawTableHeader()

    // Data rows
    var pageSubtotalEntradas = 0.0
    var pageSubtotalSalidas = 0.0
    var rowCountOnPage = 0
    var rowId = 1

    for (row in rows) {
        if (y + rowHeight > pageHeight - 60f) {
            // Page subtotal before new page
            y += 5f
            val subtotalBg = AndroidPaint().apply { color = AndroidColor.parseColor("#E3F2FD") }
            canvas.drawRect(margin, y - 10f, margin + contentWidth, y + 5f, subtotalBg)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Subtotal página:", margin, y, paint)
            canvas.drawText("E: ${String.format(locale, "%,.2f", pageSubtotalEntradas)}", colEntrada, y, paint)
            canvas.drawText("S: ${String.format(locale, "%,.2f", pageSubtotalSalidas)}", colSalida, y, paint)
            paint.typeface = Typeface.DEFAULT

            // Page number footer
            val footerPaint = AndroidPaint().apply { textSize = 8f; color = AndroidColor.GRAY; textAlign = AndroidPaint.Align.CENTER }
            canvas.drawText("Página $pageNumber", pageWidth / 2f, pageHeight - 25f, footerPaint)

            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = headerY

            // Re-draw header on new page
            canvas.drawText("TARJETA DE ESTIBA - ${material.name.uppercase()} (Cont...)", margin, y, titlePaint)
            y += 18f
            canvas.drawText("Stock Actual: ${String.format(locale, "%,.2f", material.quantity)} ${material.unitType}", margin, y, subtitlePaint)
            y += 14f
            drawTableHeader()
            pageSubtotalEntradas = 0.0
            pageSubtotalSalidas = 0.0
            rowCountOnPage = 0
        }

        // Draw row
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 8f
        paint.color = AndroidColor.BLACK

        canvas.drawText(rowId.toString(), colId, y, paint)
        canvas.drawText(row.fecha, colFecha, y, paint)

        if (row.tipo == "Entrada") {
            paint.color = AndroidColor.parseColor("#1976D2")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(String.format(locale, "%,.2f", row.cantidad), colEntrada, y, paint)
            paint.color = AndroidColor.BLACK
            paint.typeface = Typeface.DEFAULT
            canvas.drawText("-", colSalida, y, paint)
        } else {
            canvas.drawText("-", colEntrada, y, paint)
            paint.color = AndroidColor.parseColor("#D32F2F")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(String.format(locale, "%,.2f", row.cantidad), colSalida, y, paint)
            paint.color = AndroidColor.BLACK
            paint.typeface = Typeface.DEFAULT
        }

        canvas.drawText(row.motivo.take(20), colMotivo, y, paint)
        canvas.drawText(String.format(locale, "%,.2f", row.saldo), colSaldo, y, paint)

        if (row.tipo == "Entrada") pageSubtotalEntradas += row.cantidad else pageSubtotalSalidas += row.cantidad

        y += rowHeight
        rowCountOnPage++
        rowId++
    }

    // Final page subtotal
    y += 5f
    val subtotalBg = AndroidPaint().apply { color = AndroidColor.parseColor("#E3F2FD") }
    canvas.drawRect(margin, y - 10f, margin + contentWidth, y + 5f, subtotalBg)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("Subtotal página:", margin, y, paint)
    canvas.drawText("E: ${String.format(locale, "%,.2f", pageSubtotalEntradas)}", colEntrada, y, paint)
    canvas.drawText("S: ${String.format(locale, "%,.2f", pageSubtotalSalidas)}", colSalida, y, paint)
    paint.typeface = Typeface.DEFAULT

    // Grand total
    y += 20f
    val grandBg = AndroidPaint().apply { color = AndroidColor.parseColor("#1976D2") }
    canvas.drawRect(margin, y - 12f, margin + contentWidth, y + 6f, grandBg)
    val grandPaint = AndroidPaint().apply { textSize = 10f; color = AndroidColor.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
    canvas.drawText("TOTAL GENERAL", margin + 5f, y, grandPaint)
    canvas.drawText("Entradas: ${String.format(locale, "%,.2f", totalEntradas)}", colEntrada - 10f, y, grandPaint)
    canvas.drawText("Salidas: ${String.format(locale, "%,.2f", totalSalidas)}", colSalida - 10f, y, grandPaint)
    canvas.drawText("Saldo: ${String.format(locale, "%,.2f", totalEntradas - totalSalidas)}", colSaldo - 10f, y, grandPaint)

    // Page number footer
    val footerPaint = AndroidPaint().apply { textSize = 8f; color = AndroidColor.GRAY; textAlign = AndroidPaint.Align.CENTER }
    canvas.drawText("Página $pageNumber", pageWidth / 2f, pageHeight - 25f, footerPaint)

    pdfDocument.finishPage(page)

    val fileName = "TARJETA_ESTIBA_${material.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
    file.outputStream().use { pdfDocument.writeTo(it) }
    pdfDocument.close()
    return file.canonicalFile
}
