package com.drywall.calculator.presentation.ui.money

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.util.*

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyCalculatorScreen(
    @Suppress("UNUSED_PARAMETER") viewModel: MoneyCalculatorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val decimalDigits = com.drywall.calculator.presentation.theme.LocalDecimalPrecision.current
    
    val formatSymbols = DecimalFormatSymbols(Locale.US)
    
    fun formatCurrency(amount: Double, decimals: Int): String {
        val pattern = if (decimals > 0) {
            "#,##0." + "0".repeat(decimals)
        } else {
            "#,##0"
        }
        val df = DecimalFormat(pattern, formatSymbols)
        return "$ " + df.format(amount)
    }
    
    // Datos
    val initialDenominations = listOf(
        0.01, 0.02, 0.05, 0.20, 0.40, 1.00, 3.00, 5.00, 
        10.00, 20.00, 50.00, 100.00, 200.00, 500.00, 
        1000.00, 2000.00, 5000.00
    )
    val denominations = remember { initialDenominations.toMutableStateList() }
    val quantities = remember { mutableStateMapOf<Double, String>().apply {
        denominations.forEach { put(it, "0") }
    } }

    // Diálogos para CRUD de denominaciones
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Double?>(null) }
    
    val totalQuantities = quantities.values.sumOf { it.toIntOrNull() ?: 0 }
    val totalMoney = quantities.entries.sumOf { (denom, qty) -> 
        denom * (qty.toIntOrNull() ?: 0) 
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                tonalElevation = 1.dp,
                shadowElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar Denominación", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Billetes/Monedas: $totalQuantities", style = MaterialTheme.typography.labelSmall)
                        Text(
                            formatCurrency(totalMoney, decimalDigits),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row {
                        IconButton(onClick = {
                            val results = buildString {
                                append("CALCULADORA DE DINERO\n")
                                denominations.forEach { denom ->
                                    val qty = quantities[denom] ?: "0"
                                    val rowTotal = denom * (qty.toIntOrNull() ?: 0)
                                    append("${com.drywall.calculator.utils.NumberFormatter.format(denom, decimalDigits)} x $qty = ${formatCurrency(rowTotal, decimalDigits)}\n")
                                }
                                append("------------------\n")
                                append("Total: ${formatCurrency(totalMoney, decimalDigits)}")
                            }
                            clipboardManager.setText(AnnotatedString(results))
                            Toast.makeText(context, "Resultados copiados", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copiar") }
                        
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                val results = buildString {
                                    append("CALCULADORA DE DINERO\n")
                                    denominations.forEach { denom ->
                                        val qty = quantities[denom] ?: "0"
                                        val rowTotal = denom * (qty.toIntOrNull() ?: 0)
                                        append("${com.drywall.calculator.utils.NumberFormatter.format(denom, decimalDigits)} x $qty = ${formatCurrency(rowTotal, decimalDigits)}\n")
                                    }
                                    append("------------------\n")
                                    append("Total: ${formatCurrency(totalMoney, decimalDigits)}")
                                }
                                putExtra(Intent.EXTRA_TEXT, results)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir Resultados"))
                        }) { Icon(Icons.Default.Share, "Compartir") }
                        
                        IconButton(onClick = {
                            denominations.forEach { quantities[it] = "0" }
                            Toast.makeText(context, "Lista limpiada", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.DeleteSweep, "Limpiar", tint = Color.Red) }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Header de columnas fijo
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Valor", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.weight(0.5f))
                    Text("Cantidad", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.weight(0.5f))
                    Text("Subtotal", modifier = Modifier.weight(2f), fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.size(24.dp)) // Espacio para el botón de cerrar
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(denominations) { _, denom ->
                    MoneyRow(
                        denomination = denom,
                        quantity = quantities[denom] ?: "0",
                        decimalDigits = decimalDigits,
                        formatCurrency = { d, dec -> formatCurrency(d, dec) },
                        onQuantityChange = { quantities[denom] = it },
                        onEdit = { showEditDialog = denom },
                        onDelete = {
                            denominations.remove(denom)
                            quantities.remove(denom)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.alpha(0.3f), thickness = 0.5.dp)
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // Diálogo para Agregar Denominación
    if (showAddDialog) {
        var newDenom by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Nueva Denominación") },
            text = {
                OutlinedTextField(
                    value = newDenom,
                    onValueChange = { newDenom = it },
                    label = { Text("Valor (ej: 0.50 o 100)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val d = newDenom.toDoubleOrNull()
                    if (d != null && !denominations.contains(d)) {
                        denominations.add(d)
                        denominations.sort()
                        quantities[d] = "0"
                        showAddDialog = false
                    } else {
                        Toast.makeText(context, "Valor inválido o duplicado", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Agregar") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo para Editar Denominación
    showEditDialog?.let { oldDenom ->
        var editDenom by remember { mutableStateOf(oldDenom.toString()) }
        AlertDialog(
            onDismissRequest = { showEditDialog = null },
            title = { Text("Editar Denominación") },
            text = {
                OutlinedTextField(
                    value = editDenom,
                    onValueChange = { editDenom = it },
                    label = { Text("Nuevo Valor") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val d = editDenom.toDoubleOrNull()
                    if (d != null) {
                        val qty = quantities[oldDenom] ?: "0"
                        denominations.remove(oldDenom)
                        quantities.remove(oldDenom)
                        denominations.add(d)
                        denominations.sort()
                        quantities[d] = qty
                        showEditDialog = null
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun MoneyRow(
    denomination: Double,
    quantity: String,
    decimalDigits: Int,
    formatCurrency: (Double, Int) -> String,
    onQuantityChange: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val total = denomination * (quantity.toIntOrNull() ?: 0)
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Columna 1: Denominación (Click para editar/borrar)
        Box(modifier = Modifier.weight(1.5f).clickable { onEdit() }) {
            Text(
                com.drywall.calculator.utils.NumberFormatter.format(denomination, decimalDigits),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // Columna 2: X
        Text("×", modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        // Columna 3: Cantidad (Input)
        OutlinedTextField(
            value = if (quantity == "0") "" else quantity,
            onValueChange = { 
                val filtered = it.filter { char -> char.isDigit() }
                onQuantityChange(filtered.ifEmpty { "0" })
            },
            modifier = Modifier.weight(1.5f).height(52.dp),
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            placeholder = { Text("0", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        )

        // Columna 4: =
        Text("=", modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        // Columna 5: Total Fila
        Text(
            formatCurrency(total, decimalDigits),
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.End,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyLarge
        )
        
        // Botón extra para borrar denominación
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).padding(start = 4.dp)) {
            Icon(Icons.Default.Cancel, null, tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
    }
}
