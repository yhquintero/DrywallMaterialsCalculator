package com.drywall.calculator.presentation.ui.currency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.drywall.common.ui.components.ChartDataPoint
import com.drywall.common.ui.components.EvolutionChart
import com.drywall.calculator.presentation.ui.components.ValidatedTextField
import java.util.*

enum class ChartFilter { DAY, WEEK, MONTH, YEAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyConverterScreen(
    navController: NavController? = null,
    viewModel: CurrencyViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsState()
    val latestRate by viewModel.latestRate.collectAsState()
    val isScraping by viewModel.isScraping.collectAsState()
    val lastScraped by viewModel.lastScrapedTimestamp.collectAsState()
    val lastError by viewModel.lastScrapeError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.scrapeSuccess.collect {
            snackbarHostState.showSnackbar(
                message = "Tasas actualizadas correctamente desde eltoque.com",
                duration = SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(lastError) {
        lastError?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
        }
    }

    var inputValue by remember { mutableStateOf("") }
    var inputCurrency by remember { mutableStateOf("USD") }
    var outputCurrency by remember { mutableStateOf("CUP") }

    var usdRateInput by remember(latestRate) { mutableStateOf(latestRate?.let { String.format(Locale.US, "%.2f", it.usdRate) } ?: "0.00") }
    var eurRateInput by remember(latestRate) { mutableStateOf(latestRate?.let { String.format(Locale.US, "%.2f", it.eurRate) } ?: "0.00") }
    var mlcRateInput by remember(latestRate) { mutableStateOf(latestRate?.let { String.format(Locale.US, "%.2f", it.mlcRate) } ?: "0.00") }
    var cadRateInput by remember(latestRate) { mutableStateOf(latestRate?.let { String.format(Locale.US, "%.2f", it.cadRate) } ?: "0.00") }
    var mexRateInput by remember(latestRate) { mutableStateOf(latestRate?.let { String.format(Locale.US, "%.2f", it.mexRate) } ?: "0.00") }
    var zelleRateInput by remember(latestRate) { mutableStateOf(latestRate?.let { String.format(Locale.US, "%.2f", it.zelleRate) } ?: "0.00") }
    var claRateInput by remember(latestRate) { mutableStateOf(latestRate?.let { String.format(Locale.US, "%.2f", it.claRate) } ?: "0.00") }
    
    var selectedFilter by remember { mutableStateOf(ChartFilter.WEEK) }

    LaunchedEffect(Unit) {
        viewModel.fetchRates()
    }

    val currentUsdRate = usdRateInput.toDoubleOrNull() ?: 0.0
    val currentEurRate = eurRateInput.toDoubleOrNull() ?: 0.0
    val currentMlcRate = mlcRateInput.toDoubleOrNull() ?: 0.0
    val currentCadRate = cadRateInput.toDoubleOrNull() ?: 0.0
    val currentMexRate = mexRateInput.toDoubleOrNull() ?: 0.0
    val currentZelleRate = zelleRateInput.toDoubleOrNull() ?: 0.0
    val currentClaRate = claRateInput.toDoubleOrNull() ?: 0.0

    fun getRate(code: String): Double = when (code) {
        "USD" -> currentUsdRate
        "EUR" -> currentEurRate
        "MLC" -> currentMlcRate
        "CAD" -> currentCadRate
        "MEX" -> currentMexRate
        "ZELLE" -> currentZelleRate
        "CLA" -> currentClaRate
        else -> 1.0
    }

    fun convert(): String {
        val value = inputValue.toDoubleOrNull() ?: return "0.00"
        val valueInCup = if (inputCurrency == "CUP") value else value * getRate(inputCurrency)
        val result = if (outputCurrency == "CUP") valueInCup else valueInCup / getRate(outputCurrency)
        return String.format(Locale.getDefault(), "%,.2f", result)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Sección de Tasas Actuales
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tasas del Día (CUP por unidad)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            if (lastScraped.isNotEmpty()) {
                                Text("En tiempo real: $lastScraped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (isScraping) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { viewModel.fetchRates() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Divisas Principales
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValidatedTextField(value = usdRateInput, onValueChange = { usdRateInput = it }, label = "USD", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        ValidatedTextField(value = eurRateInput, onValueChange = { eurRateInput = it }, label = "EUR", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        ValidatedTextField(value = mlcRateInput, onValueChange = { mlcRateInput = it }, label = "MLC", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Divisas Secundarias
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValidatedTextField(value = cadRateInput, onValueChange = { cadRateInput = it }, label = "CAD", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        ValidatedTextField(value = mexRateInput, onValueChange = { mexRateInput = it }, label = "MEX", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        ValidatedTextField(value = zelleRateInput, onValueChange = { zelleRateInput = it }, label = "ZELLE", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValidatedTextField(value = claRateInput, onValueChange = { claRateInput = it }, label = "CLA", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        Spacer(modifier = Modifier.weight(2f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.updateRates(
                                usd = usdRateInput.toDoubleOrNull() ?: 0.0,
                                eur = eurRateInput.toDoubleOrNull() ?: 0.0,
                                mlc = mlcRateInput.toDoubleOrNull() ?: 0.0,
                                cup = 1.0,
                                cad = cadRateInput.toDoubleOrNull() ?: 0.0,
                                mex = mexRateInput.toDoubleOrNull() ?: 0.0,
                                zelle = zelleRateInput.toDoubleOrNull() ?: 0.0,
                                cla = claRateInput.toDoubleOrNull() ?: 0.0
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Actualizar y Guardar Historial")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Conversor Rápido
            Text("Conversor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValidatedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = "Monto",
                    modifier = Modifier.weight(1.5f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                CurrencySelector(
                    selected = inputCurrency,
                    onSelected = { inputCurrency = it },
                    modifier = Modifier.weight(1f)
                )
            }
            
            IconButton(
                onClick = {
                    val temp = inputCurrency
                    inputCurrency = outputCurrency
                    outputCurrency = temp
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.SwapVert, "Intercambiar")
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    modifier = Modifier.weight(1.5f).height(56.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Text(convert(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    }
                }
                CurrencySelector(
                    selected = outputCurrency,
                    onSelected = { outputCurrency = it },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- SECCIÓN DE EVOLUCIÓN ---
            val ranges = listOf(
                ChartFilter.DAY to "1D",
                ChartFilter.WEEK to "1S",
                ChartFilter.MONTH to "1M",
                ChartFilter.YEAR to "1A"
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timeline, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("EVOLUCIÓN DE TASAS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ranges.forEach { (filter, label) ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            )
                        }
                    }

                    val chartData = remember(history, selectedFilter) {
                        val currentTime = System.currentTimeMillis()
                        val filterTime = when (selectedFilter) {
                            ChartFilter.DAY -> currentTime - java.util.concurrent.TimeUnit.DAYS.toMillis(1)
                            ChartFilter.WEEK -> currentTime - java.util.concurrent.TimeUnit.DAYS.toMillis(7)
                            ChartFilter.MONTH -> currentTime - java.util.concurrent.TimeUnit.DAYS.toMillis(30)
                            ChartFilter.YEAR -> currentTime - java.util.concurrent.TimeUnit.DAYS.toMillis(365)
                        }
                        history.filter { it.timestamp >= filterTime }
                        .filter { it.usdRate > 0.0 || it.eurRate > 0.0 }
                        .sortedBy { it.timestamp }
                            .map {
                                ChartDataPoint(
                                    timestamp = it.timestamp,
                                    values = mapOf(
                                        "USD" to it.usdRate,
                                        "EUR" to it.eurRate,
                                        "MLC" to it.mlcRate,
                                        "CAD" to it.cadRate,
                                        "MEX" to it.mexRate,
                                        "ZELLE" to it.zelleRate,
                                        "CLA" to it.claRate
                                    )
                                )
                            }
                    }

                    val currencyColorsMap = mapOf(
                        "USD" to Color(0xFF2196F3),
                        "EUR" to Color(0xFFE91E63),
                        "MLC" to Color(0xFF4CAF50),
                        "CAD" to Color(0xFFFF9800),
                        "MEX" to Color(0xFF9C27B0),
                        "ZELLE" to Color(0xFF00BCD4),
                        "CLA" to Color(0xFF795548)
                    )

                    EvolutionChart(
                        history = chartData,
                        currencyColors = currencyColorsMap,
                        modifier = Modifier.fillMaxWidth().height(250.dp),
                        lastUpdateDate = if (lastScraped.isNotEmpty()) lastScraped else null
                    )

                    // Leyenda Completa
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            LegendItem(Color(0xFF2196F3), "USD")
                            LegendItem(Color(0xFFE91E63), "EUR")
                            LegendItem(Color(0xFF4CAF50), "MLC")
                            LegendItem(Color(0xFFFF9800), "CAD")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            LegendItem(Color(0xFF9C27B0), "MEX")
                            LegendItem(Color(0xFF00BCD4), "ZELLE")
                            LegendItem(Color(0xFF795548), "CLA")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

fun getCurrencyColor(code: String): Color = when (code) {
    "USD" -> Color(0xFF2196F3)
    "EUR" -> Color(0xFFE91E63)
    "MLC" -> Color(0xFF4CAF50)
    "CAD" -> Color(0xFFFF9800)
    "MEX" -> Color(0xFF9C27B0)
    "ZELLE" -> Color(0xFF00BCD4)
    "CLA" -> Color(0xFF795548)
    else -> Color.Gray
}

@Composable
fun CurrencySelector(selected: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("USD", "EUR", "MLC", "CAD", "MEX", "ZELLE", "CLA", "CUP")
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
            Text(selected); Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { onSelected(opt); expanded = false }) }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
