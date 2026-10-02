package com.drywall.calculator.presentation.ui.labor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.drywall.calculator.data.local.entity.LaborPrice
import com.drywall.calculator.presentation.ui.components.ValidatedTextField
import androidx.compose.ui.platform.LocalConfiguration

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment

@Composable
fun LaborPriceScreen(
    navController: NavController,
    onShowSnackbar: (String) -> Unit,
    viewModel: LaborPriceViewModel = hiltViewModel()
) {
    val prices by viewModel.prices.collectAsState()
    var interior by remember { mutableStateOf("15.0") }
    var exterior by remember { mutableStateOf("20.0") }
    var drop by remember { mutableStateOf("12.0") }
    var tile by remember { mutableStateOf("10.0") }
    var profitPercent by remember { mutableStateOf("27.5") }
    var expandedProfit by remember { mutableStateOf(false) }
    var showAlert by remember { mutableStateOf(false) }

    LaunchedEffect(prices) {
        prices?.let {
            if (it.interiorDrywall > 0) interior = it.interiorDrywall.toString()
            if (it.exteriorDrywall > 0) exterior = it.exteriorDrywall.toString()
            if (it.dropCeiling > 0) drop = it.dropCeiling.toString()
            if (it.tileCeiling > 0) tile = it.tileCeiling.toString()
            profitPercent = it.profitPercent.toString()
        }
    }

    val salePrices = remember(interior, exterior, drop, tile, profitPercent) {
        val minProfit = com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MIN_PROFIT
        val maxProfit = com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MAX_PROFIT
        val profit = profitPercent.toDoubleOrNull()?.coerceIn(minProfit, maxProfit) ?: com.drywall.calculator.utils.AppConfigConstants.DEFAULT_PROFIT
        listOf(
            interior.toDoubleOrNull()?.let { it * (1 + profit / 100) } ?: 0.0,
            exterior.toDoubleOrNull()?.let { it * (1 + profit / 100) } ?: 0.0,
            drop.toDoubleOrNull()?.let { it * (1 + profit / 100) } ?: 0.0,
            tile.toDoubleOrNull()?.let { it * (1 + profit / 100) } ?: 0.0
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            
            Text("Costo de Adquisición (por unidad):", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            
            // Layout muy compacto para evitar scroll
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LaborPriceRow(label = "Interior", value = interior, onValueChange = { interior = it }, calculated = salePrices[0])
                LaborPriceRow(label = "Exterior", value = exterior, onValueChange = { exterior = it }, calculated = salePrices[1])
                LaborPriceRow(label = "Cielo Raso", value = drop, onValueChange = { drop = it }, calculated = salePrices[2])
                LaborPriceRow(label = "Baldosa", value = tile, onValueChange = { tile = it }, calculated = salePrices[3])
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text("Ajuste de Rentabilidad:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            
            @OptIn(ExperimentalMaterial3Api::class)
            ExposedDropdownMenuBox(
                expanded = expandedProfit,
                onExpandedChange = { expandedProfit = !expandedProfit }
            ) {
                OutlinedTextField(
                    value = profitPercent,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Margen % (25-30)") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProfit) }
                )
                ExposedDropdownMenu(
                    expanded = expandedProfit,
                    onDismissRequest = { expandedProfit = false }
                ) {
                    listOf("25", "26", "27", "27.5", "28", "29", "30").forEach { selection ->
                        DropdownMenuItem(
                            text = { Text(selection) },
                            onClick = {
                                profitPercent = selection
                                expandedProfit = false
                            }
                        )
                    }
                }
            }

            if (showAlert) {
                AlertDialog(
                    onDismissRequest = { showAlert = false },
                    title = { Text("Valor Incompleto") },
                    text = { Text("Falta un dígito por seleccionar entre 25 o 30.") },
                    confirmButton = {
                        TextButton(onClick = { showAlert = false }) { Text("Aceptar") }
                    }
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                onClick = {
                    val p = profitPercent.toDoubleOrNull() ?: 0.0
                    if (profitPercent.length == 1 || (p > 0 && p < 25)) {
                        showAlert = true
                        return@Button
                    }
                    
                    val i = interior.toDoubleOrNull() ?: 0.0
                    val e = exterior.toDoubleOrNull() ?: 0.0
                    val d = drop.toDoubleOrNull() ?: 0.0
                    val t = tile.toDoubleOrNull() ?: 0.0
                    val profit = p.coerceIn(com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MIN_PROFIT, com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MAX_PROFIT)
                    viewModel.savePrices(LaborPrice(
                        interiorDrywall = i,
                        exteriorDrywall = e,
                        dropCeiling = d,
                        tileCeiling = t,
                        profitPercent = profit
                    ))
                    onShowSnackbar("Precios guardados correctamente")
                    navController.popBackStack()
                },
                shape = MaterialTheme.shapes.large
            ) { 
                Icon(Icons.Default.Save, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar Precios", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LaborPriceRow(label: String, value: String, onValueChange: (String) -> Unit, calculated: Double) {
    val precision = com.drywall.calculator.presentation.theme.LocalDecimalPrecision.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ValidatedTextField(
                value = value,
                onValueChange = { 
                    val clean = it.replace("\n", "").replace("\r", "")
                    if (clean.isEmpty() || clean.toDoubleOrNull() != null) onValueChange(clean) 
                },
                label = label,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text("Venta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                val locale = LocalConfiguration.current.locales[0]
                Text(
                    text = "$${String.format(locale, "%,.${precision}f", calculated)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
