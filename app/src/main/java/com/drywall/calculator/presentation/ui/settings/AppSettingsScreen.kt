package com.drywall.calculator.presentation.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.drywall.calculator.MainActivityViewModel
import com.drywall.common.utils.DatabaseBackupUtils

import com.drywall.calculator.data.local.entity.AppConfig

import androidx.navigation.NavController
import com.drywall.calculator.data.local.entity.*
import com.drywall.calculator.utils.PdfUtils
import com.drywall.common.utils.ErrorTracker
import java.io.File
import com.drywall.calculator.presentation.ui.maintenance.MaintenanceViewModel
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppSettingsScreen(
    viewModel: MainActivityViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    restoreViewModel: RestoreViewModel = hiltViewModel(),
    maintenanceViewModel: MaintenanceViewModel = hiltViewModel(),
    navController: NavController,
    onShowSnackbar: (String) -> Unit,
    onNavigateToLicense: () -> Unit,
    onNavigateToErrorLog: () -> Unit
) {
    val config by viewModel.config.collectAsState()
    val restoreSummary by restoreViewModel.summary.collectAsState()
    val currentDbVersion by restoreViewModel.currentDbVersion.collectAsState()
    val isProcessingRestore by restoreViewModel.isProcessing.collectAsState()
    val backupProgress by restoreViewModel.progress.collectAsState()
    val currentFileName by restoreViewModel.currentFileName.collectAsState()
    val backupElapsedTime by restoreViewModel.elapsedTime.collectAsState()
    val backupReportFile by restoreViewModel.backupReportFile.collectAsState()
    val progressDetail by restoreViewModel.progressDetail.collectAsState()
    val restoreMode by restoreViewModel.restoreMode.collectAsState()
    val customUnitTypes by viewModel.customUnitTypes.collectAsState()
    val diagnosticFile by settingsViewModel.diagnosticFile.collectAsState()
    val context = LocalContext.current
    
    var alertData by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun authenticateAndNavigate() {
        val activity = context as? FragmentActivity ?: return
        val biometricManager = BiometricManager.from(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        
        if (biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(activity)
            val authPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onShowSnackbar("Error de autenticación: $errString")
                    }
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        navController.navigate("maintenance")
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Consola de Mantenimiento")
                .setSubtitle("Autentíquese para acceder a funciones de bajo nivel")
                .setAllowedAuthenticators(authenticators)
                .build()

            authPrompt.authenticate(promptInfo)
        } else {
            onShowSnackbar("Debe configurar seguridad en el dispositivo")
        }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.eventFlow.collect { event ->
            when(event) {
                is SettingsViewModel.UiEvent.ShowSnackbar -> onShowSnackbar(event.message)
                is SettingsViewModel.UiEvent.ShowAlert -> alertData = event.title to event.message
            }
        }
    }

    LaunchedEffect(Unit) {
        restoreViewModel.needRestart.collect { need ->
            if (need) {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
                (context as? android.app.Activity)?.finish()
            }
        }
    }

    if (alertData != null) {
        AlertDialog(
            onDismissRequest = { alertData = null },
            title = { Text(alertData!!.first) },
            text = { Text(alertData!!.second) },
            confirmButton = { Button(onClick = { alertData = null }) { Text("Aceptar") } }
        )
    }

    LaunchedEffect(Unit) {
        restoreViewModel.restoreResult.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    AppSettingsScreenContent(
        config = config,
        restoreSummary = restoreSummary,
        currentDbVersion = currentDbVersion,
        isProcessingRestore = isProcessingRestore,
        backupProgress = backupProgress,
        currentFileName = currentFileName,
        backupElapsedTime = backupElapsedTime,
        backupReportFile = backupReportFile,
        progressDetail = progressDetail,
        restoreMode = restoreMode,
        onToggleDarkMode = { viewModel.toggleDarkMode(it) },
        onUpdateShortcuts = { viewModel.updateShortcuts(it) },
        onUpdateDecimalPrecision = { viewModel.updateDecimalPrecision(it) },
        onNavigateToLicense = onNavigateToLicense,
        onNavigateToErrorLog = onNavigateToErrorLog,
        onStartBackup = { restoreViewModel.performFullBackup(context, it) },
        onStartImport = { restoreViewModel.startImportProcess(context, it) },
        onExecuteRestore = { conflicts, news, overwrite -> restoreViewModel.executeRestore(context, conflicts, news, overwrite) },
        onCancelRestore = { restoreViewModel.cancelRestore() },
        onClearBackupReport = { restoreViewModel.clearBackupReport() },
        onSaveAll = { settingsViewModel.saveAll() },
        onMaintenanceClick = { authenticateAndNavigate() },
        customUnitTypes = customUnitTypes,
        onAddUnit = { viewModel.addCustomUnit(it) },
        onDeleteUnit = { viewModel.deleteCustomUnit(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreenContent(
    config: AppConfig?,
    restoreSummary: RestoreSummary?,
    currentDbVersion: Int,
    isProcessingRestore: Boolean,
    backupProgress: Float,
    currentFileName: String,
    backupElapsedTime: String,
    backupReportFile: File?,
    progressDetail: String,
    restoreMode: RestoreMode,
    onToggleDarkMode: (Boolean?) -> Unit,
    onUpdateShortcuts: (List<String>) -> Unit,
    onUpdateDecimalPrecision: (Int) -> Unit,
    onNavigateToLicense: () -> Unit,
    onNavigateToErrorLog: () -> Unit,
    onStartBackup: (android.net.Uri) -> Unit,
    onStartImport: (android.net.Uri) -> Unit,
    onExecuteRestore: (List<MergeConflict>, List<Any>, Boolean) -> Unit,
    onCancelRestore: () -> Unit,
    onClearBackupReport: () -> Unit,
    onSaveAll: () -> Unit,
    onMaintenanceClick: () -> Unit,
    customUnitTypes: List<UnitType>,
    onAddUnit: (String) -> Unit,
    onDeleteUnit: (UnitType) -> Unit
) {
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { onStartBackup(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { 
            try {
                // Intentar persistir permisos inmediatamente
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                android.util.Log.w("AppSettings", "No se pudo persistir permiso: ${e.message}")
            }
            onStartImport(it) 
        }
    }

    if (restoreSummary != null) {
        RestorePreviewDialog(
            summary = restoreSummary,
            currentDbVersion = currentDbVersion,
            currentFileName = currentFileName,
            onConfirm = onExecuteRestore,
            onDismiss = onCancelRestore
        )
    }

    if (isProcessingRestore) {
        val titleText = when (restoreMode) {
            RestoreMode.BACKUP -> "Exportando Respaldo..."
            RestoreMode.RESTORE -> "Restaurando Datos..."
            RestoreMode.NONE -> "Procesando..."
        }
        val isFinalizing = progressDetail.contains("ÉXITO") || progressDetail.contains("Reiniciando")
        
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            isFinalizing -> Icons.Default.CheckCircle
                            progressDetail.contains("Cliente") || progressDetail.contains("Proyecto") || progressDetail.contains("Material") -> Icons.Default.List
                            currentFileName.startsWith("database/") -> Icons.Default.Storage
                            restoreMode == RestoreMode.RESTORE -> Icons.Default.Restore
                            else -> Icons.Default.CloudSync
                        },
                        contentDescription = null,
                        tint = if (isFinalizing) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(if (isFinalizing) "Proceso Completado" else titleText)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (isFinalizing) {
                        Text(
                            "La restauración se realizó correctamente. Todos los datos, imágenes y configuraciones han sido recuperados.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp), color = Color(0xFF2E7D32))
                        Text(
                            "Reiniciando aplicación...",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp),
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                            CircularProgressIndicator(
                                progress = { backupProgress },
                                modifier = Modifier.size(80.dp),
                                strokeWidth = 6.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Text(
                                text = if (backupProgress > 0) "${(backupProgress * 100).toInt()}%" else "0%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = backupElapsedTime,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        if (progressDetail.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = progressDetail,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        
                        if (currentFileName.isNotEmpty() && progressDetail != currentFileName) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Archivo: $currentFileName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (!isFinalizing) {
                    TextButton(
                        onClick = {
                            android.widget.Toast.makeText(context, "Cancelando operaci\u00f3n...", android.widget.Toast.LENGTH_SHORT).show()
                            onCancelRestore()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        )
    }

    if (backupReportFile != null && !isProcessingRestore && restoreMode == RestoreMode.BACKUP) {
        AlertDialog(
            onDismissRequest = { /* Debe cerrarse explícitamente */ },
            title = { Text("Respaldo Finalizado") },
            text = {
                Column {
                    Text("Se ha generado el reporte de exportación técnica.")
                    Spacer(Modifier.height(8.dp))
                    Text("Tiempo total: $backupElapsedTime", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(onClick = { 
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupReportFile!!)
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_TITLE, backupReportFile!!.name)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, backupReportFile!!.name)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Guardar Reporte de Salva"))
                }) { 
                    Icon(Icons.Default.FileDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar Reporte .log") 
                }
            },
            dismissButton = {
                TextButton(onClick = onClearBackupReport) { Text("Cerrar") }
            }
        )
    }

    val allOptions = listOf(
        "company" to "Empresa",
        "bank" to "Cuentas",
        "tax" to "Impuesto",
        "clients" to "Clientes",
        "projects" to "Obras",
        "labor" to "Costos",
        "measurements" to "Medidas",
        "materials" to "Materiales",
        "calculator" to "Calculadora",
        "inventory" to "Almacén",
        "converter" to "Convertidor",
        "orders" to "Compra",
        "stats" to "Estadísticas",
        "providers" to "Proveedores",
        "diary" to "Bitácora",
        "gallery" to "Fotos",
        "id_card" to "Carnet",
        "sketchup" to "SketchUp",
        "pdf" to "PDF",
        "readme" to "Ayuda"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tarjeta de gestión de licencia
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToLicense,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Licencia Profesional",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Ver estado, activar o eliminar licencia",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Ir"
                    )
                }
            }
        }

        // Sección de Persistencia
        item {
            Column {
                Text("Configuración de Decimales", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Precisión Decimal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Ajusta cuántos decimales se muestran en la app y reportes PDF.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = "${config?.decimalPrecision ?: 4}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Slider(
                            value = (config?.decimalPrecision ?: 4).toFloat(),
                            onValueChange = { onUpdateDecimalPrecision(it.toInt()) },
                            valueRange = 0f..10f,
                            steps = 9,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0", style = MaterialTheme.typography.labelSmall)
                            Text("10", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text("Configuraciones Locales", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Guarda cualquier cambio pendiente en Empresa, Impuestos, Medidas o Mano de Obra.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onSaveAll,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Guardar Todos los Cambios", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text("Unidades de Medida", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Gestiona las unidades personalizadas disponibles en el inventario.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        var showAddDialog by remember { mutableStateOf(false) }
                        var newUnitName by remember { mutableStateOf("") }
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val baseUnits = listOf("caja", "ft²", "kg", "litro", "m²", "paquete", "rollo", "unidad")
                            baseUnits.forEach { unit ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(unit) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            
                            customUnitTypes.forEach { unit ->
                                InputChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text(unit.name) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            "Eliminar",
                                            modifier = Modifier.size(16.dp).clickable { onDeleteUnit(unit) }
                                        )
                                    }
                                )
                            }
                            
                            AssistChip(
                                onClick = { showAddDialog = true },
                                label = { Text("Añadir") },
                                leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        
                        if (showAddDialog) {
                            AlertDialog(
                                onDismissRequest = { showAddDialog = false },
                                title = { Text("Nueva Unidad") },
                                text = {
                                    OutlinedTextField(
                                        value = newUnitName,
                                        onValueChange = { newUnitName = it },
                                        label = { Text("Nombre") },
                                        singleLine = true
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        if (newUnitName.isNotBlank()) {
                                            onAddUnit(newUnitName)
                                            newUnitName = ""
                                            showAddDialog = false
                                        }
                                    }) { Text("Guardar") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showAddDialog = false }) { Text("Cancelar") }
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text("Respaldo y Recuperación (Nube/Local)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Exportar Datos", style = MaterialTheme.typography.bodyMedium) },
                            supportingContent = { Text("Generar un archivo de respaldo seguro (.zip)", style = MaterialTheme.typography.labelSmall) },
                            leadingContent = { Icon(Icons.Default.CloudUpload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.height(56.dp).clickable { exportLauncher.launch(DatabaseBackupUtils.getSuggestedFileName()) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Recuperar Datos", style = MaterialTheme.typography.bodyMedium) },
                            supportingContent = { Text("Importar desde un respaldo existente", style = MaterialTheme.typography.labelSmall) },
                            leadingContent = { Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp)) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.height(56.dp).clickable { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) }
                        )
                    }
                }
            }
        }

        item {
            Column {
                Text("Diagnóstico de Sistema (IA)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { onMaintenanceClick() },
                            onTap = { onNavigateToErrorLog() }
                        )
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BugReport, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Centro de Errores y Diagnóstico", style = MaterialTheme.typography.titleSmall)
                            Text("Genera reportes para Gemini AI si la app falla", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }

}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RestorePreviewDialog(
    summary: RestoreSummary,
    currentDbVersion: Int,
    currentFileName: String,
    onConfirm: (List<MergeConflict>, List<Any>, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedConflicts = remember { mutableStateListOf<MergeConflict>() }
    val selectedNewItems = remember { mutableStateListOf<Any>().apply { addAll(summary.newItems) } }
    var fullOverwrite by remember { mutableStateOf(false) }
    var showDestructiveConfirm by remember { mutableStateOf(false) }
    
    // Always display version 1 for user-facing display
    val displayVersion = 1
    val displayBackupVersion = if (summary.version <= 0) 1 else summary.version
    
    val conflictModules = summary.conflicts.map { it.module }.distinct()
    val expandedConflictModules = remember { mutableStateMapOf<String, Boolean>().apply { 
        conflictModules.forEach { this[it] = false } 
    } }

    // Group new items by type for better visualization
    val newItemsByType = summary.newItems.groupBy { it.javaClass.simpleName }
    val expandedNewModules = remember { mutableStateMapOf<String, Boolean>().apply { 
        newItemsByType.keys.forEach { this[it] = false } 
    } }
    
    var selectedTab by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.95f),
        title = { 
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SettingsBackupRestore, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Previsualización de Respaldo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    "Revise los datos antes de aplicar los cambios permanentes.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Información de Versión
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Versión Actual", style = MaterialTheme.typography.labelSmall)
                            // Mostrar versión de la App (1.0.1) en lugar de versión de BD
                            Text("$currentDbVersion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        
                        Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Versión Respaldo", style = MaterialTheme.typography.labelSmall)
                            // Mostrar versión real del respaldo (empezando en 1)
                            val backupVersion = if (summary.version <= 0) 1 else summary.version
                            Text("$backupVersion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                
                if (summary.counts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Contenido detectado:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        summary.counts.forEach { (label, count) ->
                            if (count > 0) {
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text("$label: $count", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                
                // Restoration options - always visible at top
                Surface(
                    color = if (fullOverwrite) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (fullOverwrite) Icons.Default.Warning else Icons.Default.Info, 
                                null, 
                                tint = if (fullOverwrite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (fullOverwrite) "MODO SOBRESCRITURA TOTAL ACTIVADO" else "OPCIONES DE RESTAURACIÓN",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (fullOverwrite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically, 
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { fullOverwrite = !fullOverwrite }
                                .padding(vertical = 2.dp)
                        ) {
                            Switch(
                                checked = fullOverwrite,
                                onCheckedChange = { fullOverwrite = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.error,
                                    checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier.scale(0.80f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "LIMPIAR Y SOBRESCRIBIR TODO EL SISTEMA", 
                                    style = MaterialTheme.typography.labelSmall, 
                                    fontWeight = FontWeight.ExtraBold, 
                                    color = if (fullOverwrite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Borra todos los datos actuales y restaura archivos/BD del respaldo.", 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        if (summary.version == 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    "Aviso: La base de datos es ilegible. Solo se recuperarán imágenes y archivos si activa esta opción.",
                                    modifier = Modifier.padding(6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Conflictos")
                                if (summary.conflicts.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    Badge { Text("${summary.conflicts.size}") }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Nuevos")
                                if (summary.newItems.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("${summary.newItems.size}") }
                                }
                            }
                        }
                    )
                }

                when (selectedTab) {
                    0 -> { // Pestaña de Conflictos
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (summary.conflicts.isNotEmpty()) {
                                item {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    ) {
                                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "AVISO: Los elementos marcados SOBRESCRIBIRÁN a los actuales por Nombre/NI.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                selectedConflicts.clear()
                                                selectedConflicts.addAll(summary.conflicts)
                                            },
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Reemplazar Todo", style = MaterialTheme.typography.labelSmall)
                                        }
                                        OutlinedButton(
                                            onClick = { selectedConflicts.clear() },
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.RemoveDone, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Omitir Todo", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                conflictModules.forEach { moduleName ->
                                    val isExpanded = expandedConflictModules[moduleName] == true
                                    val moduleConflicts = summary.conflicts.filter { it.module == moduleName }
                                    val allSelectedInModule = moduleConflicts.all { selectedConflicts.contains(it) }

                                    item {
                                        Surface(
                                            color = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            onClick = { expandedConflictModules[moduleName] = !isExpanded },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = if (currentFileName.isEmpty()) "Analizando..." else moduleName,
                                                    modifier = Modifier.weight(1f),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                
                                                Checkbox(
                                                    checked = allSelectedInModule,
                                                    onCheckedChange = { checked ->
                                                        if (checked) {
                                                            moduleConflicts.forEach { if(!selectedConflicts.contains(it)) selectedConflicts.add(it) }
                                                        } else {
                                                            moduleConflicts.forEach { selectedConflicts.remove(it) }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (isExpanded) {
                                        items(moduleConflicts) { conflict ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 4.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Checkbox(
                                                        checked = selectedConflicts.contains(conflict),
                                                        onCheckedChange = { checked ->
                                                            if (checked) selectedConflicts.add(conflict)
                                                            else selectedConflicts.remove(conflict)
                                                        }
                                                    )
                                                    Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                                        Text(conflict.existingName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                        Text("${conflict.type} • ID: ${conflict.identifier}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                    }
                                                    Icon(
                                                        Icons.Default.History, 
                                                        null, 
                                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    Box(modifier = Modifier.fillParentMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                                            Spacer(Modifier.height(12.dp))
                                            Text("Sin conflictos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("Todos los datos del respaldo son nuevos.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> { // Pestaña de Nuevos
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (summary.newItems.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                selectedNewItems.clear()
                                                selectedNewItems.addAll(summary.newItems)
                                            },
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Incluir Todo", style = MaterialTheme.typography.labelSmall)
                                        }
                                        OutlinedButton(
                                            onClick = { selectedNewItems.clear() },
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.RemoveDone, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Omitir Todo", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                newItemsByType.forEach { (type, items) ->
                                    val isExpanded = expandedNewModules[type] == true
                                    val allSelectedInType = items.all { selectedNewItems.contains(it) }
                                    
                                    item {
                                        Surface(
                                            color = if (isExpanded) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            onClick = { expandedNewModules[type] = !isExpanded },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.tertiary
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = when(type) {
                                                        "Project" -> "Obras / Proyectos"
                                                        "Client" -> "Clientes"
                                                        "Material" -> "Materiales"
                                                        "BankAccount" -> "Cuentas Bancarias"
                                                        "Provider" -> "Proveedores"
                                                        "WorkDiary" -> "Bitácora"
                                                        "LaborPrice" -> "Precios Mano de Obra"
                                                        "MaterialMeasurement" -> "Medidas"
                                                        "TaxSetting" -> "Impuestos"
                                                        "AppConfig" -> "Ajustes App"
                                                        "CompanyProfile" -> "Perfil Empresa"
                                                        else -> type
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                
                                                Checkbox(
                                                    checked = allSelectedInType,
                                                    onCheckedChange = { checked ->
                                                        if (checked) {
                                                            items.forEach { if(!selectedNewItems.contains(it)) selectedNewItems.add(it) }
                                                        } else {
                                                            items.forEach { selectedNewItems.remove(it) }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (isExpanded) {
                                        items(items) { newItem ->
                                            val name = when(newItem) {
                                                is Project -> newItem.name
                                                is Client -> "${newItem.name} ${newItem.surnames}"
                                                is Material -> newItem.name
                                                is BankAccount -> newItem.alias
                                                is Provider -> newItem.name
                                                is WorkDiary -> "Entrada: ${newItem.activity}"
                                                else -> "Ajuste de Sistema"
                                            }
                                            
                                            Card(
                                                modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 4.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Checkbox(
                                                        checked = selectedNewItems.contains(newItem),
                                                        onCheckedChange = { checked ->
                                                            if (checked) selectedNewItems.add(newItem)
                                                            else selectedNewItems.remove(newItem)
                                                        }
                                                    )
                                                    Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                                        Text(name, style = MaterialTheme.typography.bodyMedium)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    Box(modifier = Modifier.fillParentMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                        Text("No hay datos nuevos para agregar.", color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullOverwrite) {
                        showDestructiveConfirm = true
                    } else {
                        onConfirm(selectedConflicts.toList(), selectedNewItems.toList(), false)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = selectedConflicts.isNotEmpty() || selectedNewItems.isNotEmpty() || fullOverwrite,
                shape = MaterialTheme.shapes.large,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                colors = if (fullOverwrite) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
            ) {
                Icon(if (fullOverwrite) Icons.Default.DeleteForever else Icons.Default.Check, null)
                Spacer(Modifier.width(12.dp))
                Text(if (fullOverwrite) "SOBRESCRIBIR TODO EL SISTEMA" else "Confirmar y Restaurar Seleccionados", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar Previsualización", color = MaterialTheme.colorScheme.outline)
            }
        }
    )

    if (showDestructiveConfirm) {
        AlertDialog(
            onDismissRequest = { showDestructiveConfirm = false },
            title = { Text("¿ESTÁ SEGURO?", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text("Esta acción SOBRESCRIBIRÁ TODOS los datos actuales de la aplicación con los datos del respaldo.")
                    Spacer(Modifier.height(12.dp))
                    Text("Se perderán todos los cambios realizados desde la fecha del respaldo. Esta operación NO se puede deshacer.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDestructiveConfirm = false
                        onConfirm(selectedConflicts.toList(), selectedNewItems.toList(), true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("SÍ, SOBRESCRIBIR TODO") }
            },
            dismissButton = {
                TextButton(onClick = { showDestructiveConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}
