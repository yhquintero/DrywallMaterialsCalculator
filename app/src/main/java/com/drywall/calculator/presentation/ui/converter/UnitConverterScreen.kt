package com.drywall.calculator.presentation.ui.converter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.*
import kotlin.math.*
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.navigation.NavController
import androidx.compose.ui.Alignment
import com.drywall.calculator.presentation.ui.components.ValidatedTextField
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay

import com.drywall.calculator.presentation.ui.currency.CurrencyConverterScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConverterScreen(
    navController: NavController? = null,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    viewModel: UnitConverterViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsState()
    val precision = config?.decimalPrecision ?: 4
    
    var inputValue by remember { mutableStateOf("") }
    var inputUnit by remember { mutableStateOf("Metros (m)") }
    var outputUnit by remember { mutableStateOf("Pies (ft)") }
    var expandedInput by remember { mutableStateOf(false) }
    var expandedOutput by remember { mutableStateOf(false) }
    var unitType by remember { mutableStateOf("Longitud") }
    var expandedType by remember { mutableStateOf(false) }

    var limitMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(limitMessage) {
        if (limitMessage != null) {
            delay(3000)
            limitMessage = null
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded || widthSizeClass == WindowWidthSizeClass.Medium

    val units = mapOf(
        "Longitud" to listOf("Metros (m)", "Centímetros (cm)", "Milímetros (mm)", "Pies (ft)", "Pulgadas (in)", "Yardas (yd)"),
        "Área" to listOf("Metros cuadrados (m²)", "Pies cuadrados (ft²)", "Centímetros cuadrados (cm²)", "Pulgadas cuadradas (in²)"),
        "Peso" to listOf("Kilogramos (kg)", "Gramos (g)", "Libras (lb)", "Onzas (oz)"),
        "Volumen" to listOf("Litros (L)", "Mililitros (mL)", "Galones (gal)", "Pies cúbicos (ft³)", "Pulgadas cúbicas (in³)"),
        "Divisas" to listOf("CUP (Peso Cubano)", "USD (US Dollar)", "EUR (Euro)", "MLC (Moneda Libremente Convertible)"),
        "Temperatura" to listOf("Celsius (°C)", "Fahrenheit (°F)", "Kelvin (K)"),
        "Energía" to listOf("Joules (J)", "Calorías (cal)", "Kilowatios-hora (kWh)"),
        "Velocidad" to listOf("m/s", "km/h", "mph", "nudos"),
        "Tiempo" to listOf("Segundos", "Minutos", "Horas", "Días", "Semanas"),
        "Potencia" to listOf("Watios (W)", "Caballos (HP)", "Kilowatios (kW)"),
        "Datos" to listOf("Bytes", "Kilobytes (KB)", "Megabytes (MB)", "Gigabytes (GB)", "Terabytes (TB)"),
        "Presión" to listOf("Pascales (Pa)", "Bar", "Atmósfera (atm)", "PSI"),
        "Ángulo" to listOf("Grados", "Radianes"),
        "Densidad" to listOf("kg/m³", "g/cm³", "lb/ft³"),
        "Caudal" to listOf("L/s", "m³/h", "gal/min"),
        "Iluminación" to listOf("Lux (lx)", "Foot-candle (fc)"),
        "Velocidad de Red" to listOf("bps", "Kbps", "Mbps", "Gbps", "Tbps", "Pbps", "B/s", "KB/s", "MB/s", "GB/s", "TB/s", "PB/s"),
        "Especial" to listOf("Letras", "Números Romanos", "Base Binaria", "Base Hexadecimal", "Prefijos SI")
    )

    val icons = mapOf(
        "Longitud" to Icons.Default.Straighten,
        "Área" to Icons.Default.SquareFoot,
        "Peso" to Icons.Default.Scale,
        "Volumen" to Icons.Default.WaterDrop,
        "Divisas" to Icons.Default.CurrencyExchange,
        "Temperatura" to Icons.Default.Thermostat,
        "Energía" to Icons.Default.Bolt,
        "Velocidad" to Icons.Default.Speed,
        "Tiempo" to Icons.Default.Schedule,
        "Potencia" to Icons.Default.ElectricBolt,
        "Datos" to Icons.Default.Storage,
        "Presión" to Icons.Default.Compress,
        "Ángulo" to Icons.Default.Architecture,
        "Densidad" to Icons.Default.Layers,
        "Caudal" to Icons.Default.Opacity,
        "Iluminación" to Icons.Default.Lightbulb,
        "Velocidad de Red" to Icons.Default.NetworkCheck,
        "Especial" to Icons.Default.Abc
    )

    val conversionFactors = mapOf(
        "Metros (m)" to 1.0, "Centímetros (cm)" to 0.01, "Milímetros (mm)" to 0.001, "Pies (ft)" to 0.3048, "Pulgadas (in)" to 0.0254, "Yardas (yd)" to 0.9144,
        "Metros cuadrados (m²)" to 1.0, "Pies cuadrados (ft²)" to 0.092903, "Centímetros cuadrados (cm²)" to 0.0001, "Pulgadas cuadradas (in²)" to 0.00064516,
        "Kilogramos (kg)" to 1.0, "Gramos (g)" to 0.001, "Libras (lb)" to 0.453592, "Onzas (oz)" to 0.0283495,
        "Litros (L)" to 1.0, "Mililitros (mL)" to 0.001, "Galones (gal)" to 3.78541, "Pies cúbicos (ft³)" to 28.3168, "Pulgadas cuadradas (in³)" to 0.0163871,
        "Segundos" to 1.0, "Minutos" to 60.0, "Horas" to 3600.0, "Días" to 86400.0, "Semanas" to 604800.0,
        "m/s" to 1.0, "km/h" to 1.0/3.6, "mph" to 0.44704, "nudos" to 0.514444,
        "Joules (J)" to 1.0, "Calorías (cal)" to 4.184, "Kilowatios-hora (kWh)" to 3600000.0,
        "Watios (W)" to 1.0, "Caballos (HP)" to 745.7, "Kilowatios (kW)" to 1000.0,
        "Bytes" to 1.0, "Kilobytes (KB)" to 1024.0, "Megabytes (MB)" to 1048576.0, "Gigabytes (GB)" to 1073741824.0, "Terabytes (TB)" to 1099511627776.0,
        "Pascales (Pa)" to 1.0, "Bar" to 100000.0, "Atmósfera (atm)" to 101325.0, "PSI" to 6894.76,
        "Grados" to 1.0, "Radianes" to 57.2958,
        "kg/m³" to 1.0, "g/cm³" to 1000.0, "lb/ft³" to 16.0185,
        "L/s" to 1.0, "m³/h" to 1.0/3.6, "gal/min" to 0.0630902,
        "Lux (lx)" to 1.0, "Foot-candle (fc)" to 10.7639,
        "bps" to 1.0, "Kbps" to 1000.0, "Mbps" to 1000000.0, "Gbps" to 1000000000.0, "Tbps" to 1000000000000.0, "Pbps" to 1000000000000000.0,
        "B/s" to 8.0, "KB/s" to 8000.0, "MB/s" to 8000000.0, "GB/s" to 8000000000.0, "TB/s" to 8000000000000.0, "PB/s" to 8000000000000000.0
    )

    fun convert(): String {
        try {
            val cleanValue = inputValue.replace(",", ".")
            if (cleanValue.isBlank() || cleanValue == "." || cleanValue == "-") return "0.00"
            
            val value = cleanValue.toDoubleOrNull() ?: return "Error"
            
            // Límites para evitar desbordamientos o crashes
            if (value > 1e15 || value < -1e15) {
                limitMessage = "Valor demasiado grande para procesar"
                return "Límite excedido"
            }

            if (unitType == "Especial") {
                return when (outputUnit) {
                    "Letras" -> {
                        if (value > 999_999_999_999_999L) {
                            limitMessage = "Máximo 999 billones en letras"
                            return "Excede límite"
                        }
                        val longVal = cleanValue.toLongOrNull() ?: value.toLong()
                        numberToLetters(longVal)
                    }
                    "Números Romanos" -> {
                        if (value < 1 || value > 3999) {
                            limitMessage = "Romanos solo entre 1 y 3999"
                            return "Fuera de rango"
                        }
                        numberToRoman(value.toInt())
                    }
                    "Base Binaria" -> {
                        if (value > 1e12) return "Demasiado grande"
                        (cleanValue.toLongOrNull() ?: value.toLong()).toString(2)
                    }
                    "Base Hexadecimal" -> {
                        if (value > 1e15) return "Demasiado grande"
                        (cleanValue.toLongOrNull() ?: value.toLong()).toString(16).uppercase()
                    }
                    "Prefijos SI" -> getSIPrefix(value)
                    else -> "---"
                }
            }

            if (unitType == "Temperatura") {
                return when {
                    inputUnit == "Celsius (°C)" && outputUnit == "Fahrenheit (°F)" -> String.format("%.2f", value * 9/5 + 32)
                    inputUnit == "Celsius (°C)" && outputUnit == "Kelvin (K)" -> String.format("%.2f", value + 273.15)
                    inputUnit == "Fahrenheit (°F)" && outputUnit == "Celsius (°C)" -> String.format("%.2f", (value - 32) * 5/9)
                    inputUnit == "Fahrenheit (°F)" && outputUnit == "Kelvin (K)" -> String.format("%.2f", (value - 32) * 5/9 + 273.15)
                    inputUnit == "Kelvin (K)" && outputUnit == "Celsius (°C)" -> String.format("%.2f", value - 273.15)
                    inputUnit == "Kelvin (K)" && outputUnit == "Fahrenheit (°F)" -> String.format("%.2f", (value - 273.15) * 9/5 + 32)
                    else -> String.format("%.2f", value)
                }
            }

            val inFactor = conversionFactors[inputUnit] ?: 1.0
            val outFactor = conversionFactors[outputUnit] ?: 1.0
            val result = (value * inFactor) / outFactor
            
            if (unitType == "Velocidad de Red") {
                return java.math.BigDecimal.valueOf(result).stripTrailingZeros().toPlainString()
            }
            
            return if (result > 1000000000) String.format(Locale.US, "%.4e", result) else com.drywall.calculator.utils.NumberFormatter.format(result, precision)
        } catch (e: Exception) {
            return "Error de cálculo"
        }
    }

    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .then(if (unitType != "Divisas") Modifier.verticalScroll(scrollState) else Modifier)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            if (limitMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(limitMessage!!, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            if (isExpanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        UnitTypeSelector(unitType, icons, units, expandedType, { unitType = it }, { expandedType = it }, { inputUnit = it }, { outputUnit = it })
                    }
                }
            } else {
                UnitTypeSelector(unitType, icons, units, expandedType, { unitType = it }, { expandedType = it }, { inputUnit = it }, { outputUnit = it })
            }

            if (unitType == "Divisas") {
                CurrencyConverterScreen()
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                ValidatedTextField(
                    value = inputValue, 
                    onValueChange = { 
                        val clean = it.replace("\n", "").replace("\r", "").replace(" ", "")
                        if (clean.isEmpty() || clean == "." || clean == "," || clean.replace(",", ".").toDoubleOrNull() != null) {
                            if (clean.length <= 18) inputValue = clean 
                        }
                    },
                    label = "Valor a convertir", 
                    modifier = Modifier.fillMaxWidth().padding(0.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expandedInput, 
                        onExpandedChange = { expandedInput = !expandedInput }, 
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = inputUnit, onValueChange = {}, readOnly = true, label = { Text("De") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedInput) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            enabled = unitType != "Especial"
                        )
                        ExposedDropdownMenu(expanded = expandedInput, onDismissRequest = { expandedInput = false }) {
                            units[unitType]?.forEach { unit ->
                                DropdownMenuItem(text = { Text(unit) }, onClick = { inputUnit = unit; expandedInput = false })
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = expandedOutput, 
                        onExpandedChange = { expandedOutput = !expandedOutput }, 
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = outputUnit, onValueChange = {}, readOnly = true, label = { Text("A") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedOutput) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = expandedOutput, onDismissRequest = { expandedOutput = false }) {
                            units[unitType]?.forEach { unit ->
                                DropdownMenuItem(text = { Text(unit) }, onClick = { outputUnit = unit; expandedOutput = false })
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(), 
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Resultado", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(convert(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Start)
                        Text(outputUnit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }

                if (unitType == "Velocidad de Red") {
                    DownloadCalculatorSection(inputValue, inputUnit, conversionFactors)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitTypeSelector(
    unitType: String,
    icons: Map<String, ImageVector>,
    units: Map<String, List<String>>,
    expandedType: Boolean,
    onTypeChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onInputUnitChange: (String) -> Unit,
    onOutputUnitChange: (String) -> Unit
) {
    ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { onExpandedChange(!expandedType) }) {
        OutlinedTextField(
            value = unitType, onValueChange = {}, readOnly = true, label = { Text("Categoría") },
            leadingIcon = { Icon(icons[unitType] ?: Icons.Default.Build, null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { onExpandedChange(false) }) {
            units.keys.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type) }, 
                    leadingIcon = { Icon(icons[type] ?: Icons.Default.Build, null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        onTypeChange(type)
                        onInputUnitChange(units[type]!![0])
                        onOutputUnitChange(if (units[type]!!.size > 1) units[type]!![1] else units[type]!![0])
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun DownloadCalculatorSection(speedValue: String, speedUnit: String, factors: Map<String, Double>) {
    var fileSize by remember { mutableStateOf("") }
    
    val timeResult = remember(speedValue, speedUnit, fileSize) {
        val value = speedValue.replace(",", ".").toDoubleOrNull() ?: 0.0
        val size = fileSize.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (value <= 0.0 || size <= 0.0) ""
        else {
            val baseBps = value * (factors[speedUnit] ?: 1.0)
            val speedMBs = baseBps / 8000000.0
            val totalSeconds = (size / speedMBs).toLong()
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            if (h > 0) "${h}h ${m}m ${s}s" else if (m > 0) "${m}m ${s}s" else "${s}s"
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Calculadora de Descarga", fontWeight = FontWeight.Bold)
            ValidatedTextField(
                value = fileSize,
                onValueChange = { fileSize = it.replace(" ", "") },
                label = "Tamaño de Archivo (MB)",
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (timeResult.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "Tiempo estimado: $timeResult",
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

private fun numberToRoman(number: Int): String {
    if (number < 1 || number > 3999) return "No soportado"
    val romanValues = listOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val romanSymbols = listOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    var num = number
    val sb = StringBuilder()
    for (i in romanValues.indices) {
        while (num >= romanValues[i]) {
            num -= romanValues[i]
            sb.append(romanSymbols[i])
        }
    }
    return sb.toString()
}

private fun numberToLetters(number: Long): String {
    if (number == 0L) return "cero"
    if (number < 0) return "menos " + numberToLetters(-number)
    val units = listOf("", "un", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve")
    val tens = listOf("", "diez", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa")
    val special = listOf("diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve")
    val hundreds = listOf("", "cien", "doscientos", "trescientos", "cuatrocientos", "quinientos", "seiscientos", "setecientos", "ochocientos", "novecientos")
    fun convertThree(n: Int): String {
        if (n == 0) return ""
        var res = ""
        val h = n / 100
        val d = (n % 100) / 10
        val u = n % 10
        if (h > 0) {
            if (h == 1 && d == 0 && u == 0) return "cien"
            res += (if (h == 1) "ciento" else hundreds[h]) + " "
        }
        if (d == 1) {
            res += special[u]
        } else {
            if (d > 1) {
                res += tens[d]
                if (u > 0) res += " y "
            }
            if (u > 0) res += units[u]
        }
        return res.trim()
    }
    var result = ""
    var n = number
    val billones = n / 1_000_000_000_000L
    if (billones > 0) {
        result += (if (billones == 1L) "un billón" else convertThree(billones.toInt()) + " billones") + " "
        n %= 1_000_000_000_000L
    }
    val millones = n / 1_000_000L
    if (millones > 0) {
        result += (if (millones == 1L) "un millón" else convertThree(millones.toInt()) + " millones") + " "
        n %= 1_000_000L
    }
    val miles = (n / 1000).toInt()
    if (miles > 0) {
        result += (if (miles == 1) "mil" else convertThree(miles) + " mil") + " "
        n %= 1000
    }
    if (n > 0 || result.isEmpty()) result += convertThree(n.toInt())
    var finalResult = result.trim().replace("  ", " ")
    if (finalResult.endsWith("un")) finalResult += "o"
    return finalResult.replaceFirstChar { it.uppercase() }
}

private fun getSIPrefix(value: Double): String {
    if (value == 0.0) return "0"
    val prefixes = listOf("y", "z", "a", "f", "p", "n", "µ", "m", "", "k", "M", "G", "T", "P", "E", "Z", "Y")
    val exponent = (log10(abs(value)) / 3).toInt()
    val index = exponent + 8
    return if (index in prefixes.indices) {
        String.format("%.2f %s", value / 10.0.pow((exponent * 3).toDouble()), prefixes[index])
    } else String.format("%.2e", value)
}
