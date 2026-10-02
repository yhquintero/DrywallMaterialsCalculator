package com.drywall.calculator.presentation.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.drywall.calculator.utils.security.LicenseInfo
import com.drywall.calculator.utils.security.LicensingManager
import com.drywall.common.security.SecureStorageUtils
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseManagementScreen(
    onLicenseRemoved: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: LicenseViewModel = hiltViewModel()
) {
    val licenseState by viewModel.licenseState.collectAsState()
    val consumedHistory by viewModel.consumedHistory.collectAsState()
    val message by viewModel.message.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var showViolationDialog by remember { mutableStateOf(false) }
    var showDeleteRecordDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<com.drywall.calculator.utils.security.ConsumedLicense?>(null) }
    val context = LocalContext.current

    val isTrialActive = remember { LicensingManager.isTrialActive(context) }
    val hasTrialBeenUsed = remember { LicensingManager.hasTrialEverBeenUsed(context) }
    val professionalEverActivated: Boolean = remember { 
        SecureStorageUtils.getEncryptedPrefs(context, "secure_licensing_prefs_v2")
            .getBoolean("professional_license_ever_activated", false) 
    }

    LaunchedEffect(licenseState) {
        if (licenseState is LicenseState.Loaded && !(licenseState as LicenseState.Loaded).isValid) {
            if (LicensingManager.isLicenseViolation(context)) {
                showViolationDialog = true
            }
        }
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    if (showViolationDialog) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)) },
            title = { Text("VIOLACIÓN DE SEGURIDAD", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "Se ha detectado una manipulación del reloj o intento de fraude. " +
                    "La licencia ha sido INVALIDADA permanentemente por seguridad.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showViolationDialog = false
                        viewModel.removeLicense()
                        onLicenseRemoved()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Cerrar Sesión y Bloquear") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. LICENSE STATE (top - with back arrow)
        when (licenseState) {
            is LicenseState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is LicenseState.Empty -> {
                // No license - show empty state with back arrow
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Regresar",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Sin licencia activa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (isTrialActive) {
                                Text("Período de prueba activo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            } else if (hasTrialBeenUsed) {
                                Text("Prueba expirada - Ingrese licencia profesional", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("Active una licencia o prueba para comenzar", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            is LicenseState.Loaded -> {
                val license = (licenseState as LicenseState.Loaded).license
                val isValid = (licenseState as LicenseState.Loaded).isValid
                LicenseInfoCard(license = license, isValid = isValid, onBack = onNavigateBack)
            }
        }

        // 2. BUTTONS (always visible, compact)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Activar", style = MaterialTheme.typography.labelLarge)
            }

            if (licenseState is LicenseState.Loaded) {
                OutlinedButton(
                    onClick = { showConfirmDeleteDialog = true },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Eliminar", style = MaterialTheme.typography.labelLarge)
                }
            } else if (hasTrialBeenUsed && !isTrialActive && !professionalEverActivated) {
                // Determinar si la prueba aún tiene tiempo pero está inactiva (error de sistema) o si ya expiró
                val trialEnd = LicensingManager.getTrialEndTime(context)
                val now = LicensingManager.getCurrentTimeSafe(context)
                val isExpired = now >= trialEnd

                OutlinedButton(
                    onClick = { viewModel.resetTrial() },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(
                        imageVector = if (isExpired) Icons.Default.History else Icons.Default.Refresh,
                        contentDescription = null, 
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isExpired) "Limpiar Historial Prueba" else "Recuperar Prueba", 
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // 3. HISTORY (fills remaining space)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Historial de Uso de Licencias",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            TextButton(
                onClick = {
                    viewModel.exportHistoryToPdf { file ->
                        if (file != null) {
                            com.drywall.calculator.utils.PdfUtils.sharePdf(context, file)
                        } else {
                            Toast.makeText(context, "Error al generar reporte", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Exportar PDF", style = MaterialTheme.typography.labelMedium)
            }
        }

        val config = LocalConfiguration.current
        val locale = config.locales[0]
        val currentLicense = (licenseState as? LicenseState.Loaded)?.license

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Usuario", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Estado", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                    Text("Fecha", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Spacer(modifier = Modifier.width(32.dp))
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                if (consumedHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay registros", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(consumedHistory) { record ->
                            val isCurrent = currentLicense?.signature == record.signature
                            val isTrialRecord = record.signature == LicensingManager.TRIAL_SIGNATURE
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    record.user,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    record.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (record.reason.contains("Violación")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1.2f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                val dateStr = if (record.consumptionDate > 1000000000000L) {
                                    SimpleDateFormat("dd/MM/yyyy", locale).format(Date(record.consumptionDate))
                                } else "N/A"
                                Text(
                                    dateStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(0.8f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                                )

                                if (!isCurrent && !isTrialRecord) {
                                    IconButton(
                                        onClick = {
                                            recordToDelete = record
                                            showDeleteRecordDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Borrar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(32.dp))
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddLicenseDialog(
            onDismiss = { showAddDialog = false },
            onSave = { json ->
                viewModel.saveNewLicense(json) {
                    showAddDialog = false
                }
            }
        )
    }

    if (showConfirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            title = { Text("Eliminar licencia") },
            text = { Text("¿Está seguro de que desea eliminar la licencia actual? Perderá el acceso a las funciones profesionales.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeLicense()
                        showConfirmDeleteDialog = false
                        onLicenseRemoved()
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDeleteRecordDialog && recordToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteRecordDialog = false
                recordToDelete = null
            },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Eliminar registro") },
            text = {
                Text("¿Está seguro de que desea eliminar el registro de '${recordToDelete?.user}' del historial de uso? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        recordToDelete?.let { viewModel.deleteHistoryRecord(it.signature) }
                        showDeleteRecordDialog = false
                        recordToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteRecordDialog = false
                    recordToDelete = null
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun LicenseInfoCard(license: LicenseInfo, isValid: Boolean, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Datos de la Licencia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isValid) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

            InfoRow(label = "Usuario:", value = license.user)
            InfoRow(label = "ID Dispositivo:", value = license.deviceId)
            InfoRow(label = "Plan:", value = license.type)

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(text = "Vencimiento: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text(text = formatDate(license.expiryDate), style = MaterialTheme.typography.bodySmall)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(text = "Tiempo Restante: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = getRemainingTime(context, license.expiryDate),
                    color = if (getRemainingDays(context, license.expiryDate) <= 7) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color? = null) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = "$label ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Text(
            text = value,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    return format.format(date)
}

fun getRemainingDays(context: android.content.Context, expiryDate: Long): Long {
    val now = LicensingManager.getCurrentTimeSafe(context)
    val diff = expiryDate - now
    return diff / (1000 * 60 * 60 * 24)
}

fun getRemainingTime(context: android.content.Context, expiryDate: Long): String {
    val now = LicensingManager.getCurrentTimeSafe(context)
    val diff = expiryDate - now
    if (diff <= 0) return "Expirada"
    val days = diff / (1000 * 60 * 60 * 24)
    val hours = (diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
    val minutes = (diff % (1000 * 60 * 60)) / (1000 * 60)
    return when {
        days > 0 -> "$days días y $hours horas"
        hours > 0 -> "$hours horas y $minutes minutos"
        else -> "$minutes minutos"
    }
}

@Composable
fun AddLicenseDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var licenseJson by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pegar código de licencia") },
        text = {
            Column {
                OutlinedTextField(
                    value = licenseJson,
                    onValueChange = {
                        licenseJson = it.replace("\n", "").replace("\r", "")
                        error = null
                    },
                    label = { Text("JSON completo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                )
                Text(
                    text = "Pegue el código que recibió por WhatsApp o email",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (licenseJson.isBlank()) {
                        error = "El código no puede estar vacío"
                        return@TextButton
                    }
                    onSave(licenseJson.trim())
                }
            ) {
                Text("Activar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
