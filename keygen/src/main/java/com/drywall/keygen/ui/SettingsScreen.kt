package com.drywall.keygen.ui

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drywall.keygen.KeygenViewModel
import com.drywall.keygen.security.KeyGenSecurity
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    isDarkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
    viewModel: KeygenViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember {
        KeyGenSecurity.getEncryptedPrefs(context, "keygen_prefs_encrypted")
    }

    var manualUsdRate by remember { mutableStateOf(prefs.getString("usd_rate", "622.0") ?: "622.0") }
    var manualEurRate by remember { mutableStateOf(prefs.getString("eur_rate", "710.0") ?: "710.0") }
    var manualMlcRate by remember { mutableStateOf(prefs.getString("mlc_rate", "453.55") ?: "453.55") }
    var manualCadRate by remember { mutableStateOf(prefs.getString("cad_rate", "240.0") ?: "240.0") }
    var manualMexRate by remember { mutableStateOf(prefs.getString("mex_rate", "18.0") ?: "18.0") }
    var manualZelleRate by remember { mutableStateOf(prefs.getString("zelle_rate", "345.0") ?: "345.0") }
    var manualClaRate by remember { mutableStateOf(prefs.getString("cla_rate", "340.0") ?: "340.0") }

    var customPriceOneDay by remember { mutableStateOf(prefs.getString("custom_price_day", "5.0") ?: "5.0") }
    var customPriceOneWeek by remember { mutableStateOf(prefs.getString("custom_price_week", "20.0") ?: "20.0") }
    var customPriceOneMonth by remember { mutableStateOf(prefs.getString("custom_price_month", "50.0") ?: "50.0") }
    var customPriceOneYear by remember { mutableStateOf(prefs.getString("custom_price_year", "300.0") ?: "300.0") }
    var customPriceTwoYears by remember { mutableStateOf(prefs.getString("custom_price_twoyears", "500.0") ?: "500.0") }

    var showPublicKeyDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "CONFIGURACIÓN DEL SISTEMA",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CurrencyExchange, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Tasas de Cambio (CUP)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualUsdRate, 
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) manualUsdRate = it }, 
                        label = { Text("USD") }, 
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = manualEurRate, 
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) manualEurRate = it }, 
                        label = { Text("EUR") }, 
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualMlcRate, 
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) manualMlcRate = it }, 
                        label = { Text("MLC") }, 
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = manualCadRate, 
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) manualCadRate = it }, 
                        label = { Text("CAD") }, 
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualMexRate, 
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) manualMexRate = it }, 
                        label = { Text("MEX") }, 
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = manualZelleRate, 
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) manualZelleRate = it }, 
                        label = { Text("ZELLE") }, 
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                OutlinedTextField(
                    value = manualClaRate, 
                    onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) manualClaRate = it }, 
                    label = { Text("CLA") }, 
                    modifier = Modifier.fillMaxWidth(0.5f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sell, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Precios Personalizados (USD)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customPriceOneDay, 
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) customPriceOneDay = it }, 
                        label = { Text("1 Día") }, 
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = customPriceOneWeek, 
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) customPriceOneWeek = it }, 
                        label = { Text("1 Sem.") }, 
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customPriceOneMonth, 
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) customPriceOneMonth = it }, 
                        label = { Text("1 Mes") }, 
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = customPriceOneYear, 
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) customPriceOneYear = it }, 
                        label = { Text("1 Año") }, 
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                OutlinedTextField(
                    value = customPriceTwoYears, 
                    onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) customPriceTwoYears = it }, 
                    label = { Text("2 Años") }, 
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SettingsSuggest, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Preferencias y Sistema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                ListItem(
                    headlineContent = { Text("Modo Oscuro") },
                    supportingContent = { Text("Cambiar el tema visual de la aplicación") },
                    leadingContent = { Icon(if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode, null) },
                    trailingContent = {
                        Switch(checked = isDarkMode, onCheckedChange = { onThemeChange(it) })
                    }
                )

                HorizontalDivider()

                OutlinedButton(
                    onClick = { showPublicKeyDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) { 
                    Icon(Icons.Default.VpnKey, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ver Clave Pública del Sistema") 
                }
            }
        }

        Button(
            onClick = {
                val newUsd = manualUsdRate.toDoubleOrNull() ?: 622.0
                val newEur = manualEurRate.toDoubleOrNull() ?: 710.0
                val newMlc = manualMlcRate.toDoubleOrNull() ?: 453.55
                val newCad = manualCadRate.toDoubleOrNull() ?: 240.0
                val newMex = manualMexRate.toDoubleOrNull() ?: 18.0
                val newZelle = manualZelleRate.toDoubleOrNull() ?: 345.0
                val newCla = manualClaRate.toDoubleOrNull() ?: 340.0

                // Log changes if different (simplified check)
                viewModel.logRateChange("USD", newUsd)
                viewModel.logRateChange("EUR", newEur)
                viewModel.logRateChange("MLC", newMlc)
                viewModel.logRateChange("CAD", newCad)
                viewModel.logRateChange("MEX", newMex)
                viewModel.logRateChange("ZELLE", newZelle)
                viewModel.logRateChange("CLA", newCla)

                prefs.edit().apply {
                    putString("usd_rate", manualUsdRate)
                    putString("eur_rate", manualEurRate)
                    putString("mlc_rate", manualMlcRate)
                    putString("cad_rate", manualCadRate)
                    putString("mex_rate", manualMexRate)
                    putString("zelle_rate", manualZelleRate)
                    putString("cla_rate", manualClaRate)
                    putString("custom_price_day", customPriceOneDay)
                    putString("custom_price_week", customPriceOneWeek)
                    putString("custom_price_month", customPriceOneMonth)
                    putString("custom_price_year", customPriceOneYear)
                    putString("custom_price_twoyears", customPriceTwoYears)
                    apply()
                }
                
                scope.launch {
                    snackbarHostState.showSnackbar("Configuración guardada correctamente")
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.Save, null)
            Spacer(Modifier.width(8.dp))
            Text("GUARDAR CAMBIOS")
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showPublicKeyDialog) {
        val publicKey = remember { KeyGenSecurity.publicKeyString.ifBlank { "No disponible" } }
        Dialog(onDismissRequest = { showPublicKeyDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Clave Pública del Sistema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Comparta esta clave con el desarrollador para autorizar la app de licencias.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(publicKey, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(publicKey))
                                scope.launch {
                                    snackbarHostState.showSnackbar("Clave pública copiada")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Copiar") }
                        TextButton(onClick = { showPublicKeyDialog = false }, modifier = Modifier.weight(1f)) { Text("Cerrar") }
                    }
                }
            }
        }
    }
}
