package com.drywall.calculator.presentation.ui.licensing

import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.drywall.calculator.presentation.theme.DrywallTheme
import com.drywall.calculator.utils.security.LicensingManager
import com.drywall.common.security.SecurityUtils
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.util.*
import kotlin.time.Duration.Companion.seconds

enum class LicenseRequestType(val label: String, val icon: ImageVector) {
    DAY("1 Día Profesional", Icons.Default.Timer),
    WEEK("1 Semana Profesional", Icons.Default.DateRange),
    MONTH("1 Mes Profesional", Icons.Default.Event),
    YEAR("1 Año Profesional", Icons.Default.WorkspacePremium),
    TWO_YEARS("2 Años Profesional", Icons.Default.Stars)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    onActivated: () -> Unit,
    currentDarkMode: Boolean? = null,
    onToggleDarkMode: (Boolean?) -> Unit = {},
) {
    val context = LocalContext.current
    val deviceId = remember { SecurityUtils.getDeviceId(context) }
    var userName by remember { mutableStateOf("") }
    
    // Cambiamos el valor por defecto a DAY o dejamos que el usuario elija.
    // Si el usuario reporta que "solo acepta 1 año", asegurémonos que el estado sea reactivo.
    var selectedType by remember { mutableStateOf(LicenseRequestType.DAY) }
    var licenseKey by remember { mutableStateOf("") }
    var errorType by remember { mutableStateOf<String?>(null) }
    var errorDetail by remember { mutableStateOf<String?>(null) }

    var currentLicense by remember { mutableStateOf(LicensingManager.getLicense(context)) }
    var isValid by remember { mutableStateOf(LicensingManager.isLicenseValid(context)) }
    var isExpired by remember { mutableStateOf(LicensingManager.isLicenseExpired(context)) }
    var isManipulated by remember { mutableStateOf(LicensingManager.isClockManipulated(context)) }

    var remainingTimeText by remember { mutableStateOf("") }

    // Auto-redirección si ya está activa (Solución al bloqueo con licencia válida)
    LaunchedEffect(Unit) {
        if (LicensingManager.isLicenseValid(context)) {
            onActivated()
        }
    }

    LaunchedEffect(currentLicense) {
        while (true) {
            val expiry = currentLicense?.expiryDate ?: 0L
            val now = LicensingManager.getCurrentTimeSafe(context)
            val diff = expiry - now
            
            if (diff > 0) {
                val years = diff / (365L * 24 * 60 * 60 * 1000)
                var rem = diff % (365L * 24 * 60 * 60 * 1000)
                val months = rem / (30L * 24 * 60 * 60 * 1000)
                rem %= (30L * 24 * 60 * 60 * 1000)
                val weeks = rem / (7L * 24 * 60 * 60 * 1000)
                rem %= (7L * 24 * 60 * 60 * 1000)
                val days = rem / (24L * 60 * 60 * 1000)
                rem %= (24L * 60 * 60 * 1000)
                val hours = rem / (60 * 60 * 1000)
                rem %= (60 * 60 * 1000)
                val minutes = rem / (60 * 1000)
                rem %= (60 * 1000)
                val seconds = rem / 1000
                
                remainingTimeText = buildString {
                    val locale = Locale.US
                    if (years > 0) append("${years}a ")
                    if (months > 0) append("${months}m ")
                    if (weeks > 0) append("${weeks}s ")
                    if (days > 0) append("${days}d ")
                    append(String.format(locale, "%02d", hours.toInt()))
                    append(":")
                    append(String.format(locale, "%02d", minutes.toInt()))
                    append(":")
                    append(String.format(locale, "%02d", seconds.toInt()))
                }
            } else {
                remainingTimeText = "EXPIRADA"
            }
            delay(1.seconds)
        }
    }

    // Confirmación antes de salir de la pantalla de activación
    var showExitConfirm by remember { mutableStateOf(value = false) }
    BackHandler {
        if (currentLicense != null && isValid) showExitConfirm = true
    }
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("¿Salir sin activar?") },
            text = { Text("Si sale sin activar la licencia, algunas funciones no estarán disponibles. ¿Desea continuar?") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirm = false
                        if (LicensingManager.isLicenseValid(context)) {
                            onActivated()
                        } else {
                            (context as? android.app.Activity)?.finish()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("SALIR") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    ActivationScreenContent(
        widthSizeClass = widthSizeClass,
        deviceId = deviceId,
        userName = userName,
        onUserNameChange = { userName = it },
        selectedType = selectedType,
        onTypeSelect = { selectedType = it },
        onSendRequest = { handleWhatsAppRequest(context, userName, deviceId, selectedType) },
        licenseKey = licenseKey,
        onKeyChange = {
            licenseKey = it
            errorType = null
            errorDetail = null
        },
        errorType = errorType,
        errorDetail = errorDetail,
        onActivate = {
            if (licenseKey.isBlank()) {
                errorType = "PEGUE_EL_CODIGO"
                errorDetail = null
            } else {
                when (val result = LicensingManager.saveLicense(context, licenseKey)) {
                    is com.drywall.calculator.utils.security.ActivationResult.Success -> {
                        Toast.makeText(context, "¡Licencia Activada Correctamente!", Toast.LENGTH_LONG).show()
                        currentLicense = LicensingManager.getLicense(context)
                        isValid = LicensingManager.isLicenseValid(context)
                        isExpired = LicensingManager.isLicenseExpired(context)
                        isManipulated = LicensingManager.isClockManipulated(context)
                        onActivated()
                    }
                    is com.drywall.calculator.utils.security.ActivationResult.Failure -> {
                        errorType = "CÓDIGO_INVALIDO"
                        errorDetail = when {
                            result.message.contains("Firma") -> "La firma digital no es válida."
                            result.message.contains("Dispositivo") -> "Este código no corresponde a este dispositivo."
                            result.message.contains("Expirada") -> "Esta licencia ya expiró."
                            result.message.contains("Consumida") -> "Esta licencia ya fue utilizada."
                            result.message.contains("Formato") -> "El formato del código no es válido."
                            result.message.contains("Input demasiado grande") -> "El código ingresado es demasiado largo."
                            result.message.contains("Campos con valores") -> "El código contiene valores no válidos."
                            else -> "Código de licencia inválido. Verifique e intente de nuevo."
                        }
                    }
                }
            }
        },
        license = currentLicense,
        isValid = isValid,
        isExpired = isExpired,
        isManipulated = isManipulated,
        remainingTimeText = remainingTimeText,
        currentDarkMode = currentDarkMode,
        onToggleDarkMode = onToggleDarkMode,
        onRenew = { handleWhatsAppRequest(context, userName, deviceId, selectedType) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivationScreenContent(
    widthSizeClass: WindowWidthSizeClass,
    deviceId: String,
    userName: String,
    onUserNameChange: (String) -> Unit,
    selectedType: LicenseRequestType,
    onTypeSelect: (LicenseRequestType) -> Unit,
    onSendRequest: () -> Unit,
    licenseKey: String,
    onKeyChange: (String) -> Unit,
    errorType: String?,
    errorDetail: String?,
    onActivate: () -> Unit,
    license: com.drywall.calculator.utils.security.LicenseInfo?,
    isValid: Boolean,
    isExpired: Boolean,
    isManipulated: Boolean,
    remainingTimeText: String,
    currentDarkMode: Boolean?,
    onToggleDarkMode: (Boolean?) -> Unit,
    onRenew: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Activación Profesional", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    var showResetConfirm by remember { mutableStateOf(false) }
                    IconButton(onClick = { showResetConfirm = true }) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Limpiar Todo", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    
                    if (showResetConfirm) {
                        AlertDialog(
                            onDismissRequest = { showResetConfirm = false },
                            title = { Text("¿RESETEO DE FÁBRICA?") },
                            text = { Text("Esta acción borrará:\n1. Licencia actual\n2. Permisos biométricos\n3. Sincronización de red\n\nDeberá repetir TODOS los pasos desde cero. ¿Continuar?") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val activity = context as? android.app.Activity
                                        LicensingManager.clearLicense(context)
                                        showResetConfirm = false
                                        activity?.finish()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) { Text("RESETEAR Y SALIR") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showResetConfirm = false }) { Text("Cancelar") }
                            }
                        )
                    }
                },
                actions = {
                    ThemeToggleButton(currentDarkMode, onToggleDarkMode)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
        
        if (isExpanded) {
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LicenseStatusCards(license, isValid, isExpired, isManipulated, remainingTimeText, onRenew)
                    RequestLicenseSection(
                        userName = userName,
                        onUserNameChange = onUserNameChange,
                        deviceId = deviceId,
                        selectedType = selectedType,
                        onTypeSelect = onTypeSelect,
                        onSendRequest = onSendRequest
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActivationSection(
                        licenseKey = licenseKey,
                        onKeyChange = onKeyChange,
                        errorType = errorType,
                        errorDetail = errorDetail,
                        onActivate = onActivate
                    )
                    
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Hardware ID: $deviceId",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
            }
        } else {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LicenseStatusCards(license, isValid, isExpired, isManipulated, remainingTimeText, onRenew)

                RequestLicenseSection(
                    userName = userName,
                    onUserNameChange = onUserNameChange,
                    deviceId = deviceId,
                    selectedType = selectedType,
                    onTypeSelect = onTypeSelect,
                    onSendRequest = onSendRequest
                )

                ActivationSection(
                    licenseKey = licenseKey,
                    onKeyChange = onKeyChange,
                    errorType = errorType,
                    errorDetail = errorDetail,
                    onActivate = onActivate
                )
                
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "ID: $deviceId", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeToggleButton(
    currentDarkMode: Boolean?,
    onToggleDarkMode: (Boolean?) -> Unit
) {
    IconButton(onClick = {
        val nextMode = when (currentDarkMode) {
            null -> false // De Automático a Claro
            false -> true  // De Claro a Oscuro
            true -> null   // De Oscuro a Automático
        }
        onToggleDarkMode(nextMode)
    }, modifier = Modifier.size(36.dp)) {
        Icon(
            when (currentDarkMode) {
                true -> Icons.Outlined.DarkMode
                false -> Icons.Outlined.LightMode
                else -> Icons.Outlined.Contrast
            },
            contentDescription = "Cambiar Tema",
            modifier = Modifier.size(20.dp)
        )
    }
}


@Composable
private fun LicenseStatusCards(
    license: com.drywall.calculator.utils.security.LicenseInfo?,
    isValid: Boolean,
    isExpired: Boolean,
    isManipulated: Boolean,
    remainingTimeText: String,
    onRenewClick: () -> Unit
) {
    if ((license != null) && isValid && !isExpired && !isManipulated) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("TIEMPO RESTANTE PROFESIONAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        remainingTimeText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            }
        }
    }

    if (license != null && !isValid && !isExpired && !isManipulated) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "LICENCIA INVÁLIDA",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "Esta licencia no corresponde a este dispositivo o la firma ha sido alterada.",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRenewClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("SOLICITAR NUEVO CÓDIGO", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }

    if (isManipulated || isExpired) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isManipulated) "SEGURIDAD: Reloj desincronizado." 
                        else "LICENCIA EXPIRADA.",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onRenewClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("RENOVAR AHORA", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestLicenseSection(
    userName: String,
    onUserNameChange: (String) -> Unit,
    deviceId: String,
    selectedType: LicenseRequestType,
    onTypeSelect: (LicenseRequestType) -> Unit,
    onSendRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VpnKey, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("1. Datos de Solicitud", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = userName, 
                onValueChange = onUserNameChange,
                label = { Text("Nombre o Empresa") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hardware ID (Copia Auto):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(deviceId, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    val context = LocalContext.current
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Device ID", deviceId))
                        Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.size(32.dp)) { 
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp)) 
                    }
                }
            }

            Text("Seleccione Plan Profesional", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            
            // Rejilla de planes optimizada: 2 filas (3+2) con altura reducida
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val types = LicenseRequestType.entries
                types.chunked(3).forEach { rowEntries ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowEntries.forEach { type ->
                            val isSelected = selectedType == type
                            Surface(
                                onClick = { onTypeSelect(type) },
                                shape = MaterialTheme.shapes.medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp) // Reducido para ahorrar espacio vertical
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = type.icon, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(18.dp), // Icono más pequeño para que quepa texto
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = type.label.replace(" Profesional", ""), 
                                        style = MaterialTheme.typography.labelSmall, 
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Visible
                                    )
                                }
                            }
                        }
                        if (rowEntries.size < 3) {
                            repeat(3 - rowEntries.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onSendRequest,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("SOLICITAR POR WHATSAPP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ActivationSection(
    licenseKey: String,
    onKeyChange: (String) -> Unit,
    errorType: String?,
    errorDetail: String?,
    onActivate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("2. Activar Licencia", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = licenseKey,
                onValueChange = onKeyChange,
                placeholder = { Text("Pegue el código aquí", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.fillMaxWidth(),
                isError = errorType != null,
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            if (errorType == "PEGUE_EL_CODIGO") {
                Text("Pegue el código de licencia en el campo superior",
                     color = MaterialTheme.colorScheme.error,
                     style = MaterialTheme.typography.labelSmall)
            } else if (errorType == "CÓDIGO_INVALIDO") {
                Column {
                    Text("No se pudo activar la licencia.",
                         color = MaterialTheme.colorScheme.error,
                         style = MaterialTheme.typography.labelSmall,
                         fontWeight = FontWeight.Bold)
                    errorDetail?.let {
                        Text(it,
                             color = MaterialTheme.colorScheme.onSurfaceVariant,
                             style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Button(
                onClick = onActivate,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Key, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("ACTIVAR AHORA", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun handleWhatsAppRequest(context: android.content.Context, userName: String, deviceId: String, selectedType: LicenseRequestType) {
    if (userName.isBlank()) {
        Toast.makeText(context, "Por favor ingrese su nombre", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val message = "Hola, solicito activar mi Licencia Profesional de Calculadora Drywall.\n\n👤 Usuario: ${userName.trim()}\n🆔 ID Dispositivo: ${deviceId.trim()}\n📅 Plan: ${selectedType.label}\n🕒 Fecha Actual: $currentDate"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "https://api.whatsapp.com/send?text=${URLEncoder.encode(message, "UTF-8")}".toUri()
        }
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "WhatsApp no está instalado. Instálelo desde Google Play e intente de nuevo.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error al abrir WhatsApp: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ActivationScreenPreview() {
    DrywallTheme {
        ActivationScreenContent(
            widthSizeClass = WindowWidthSizeClass.Compact,
            deviceId = "ABC-123-XYZ",
            userName = "",
            onUserNameChange = {},
            selectedType = LicenseRequestType.YEAR,
            onTypeSelect = {},
            onSendRequest = {},
            licenseKey = "",
            onKeyChange = {},
            errorType = null,
            errorDetail = null,
            onActivate = {},
            license = null,
            isValid = true,
            isExpired = false,
            isManipulated = false,
            remainingTimeText = "",
            currentDarkMode = null,
            onToggleDarkMode = {},
            onRenew = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, widthDp = 800, heightDp = 480)
@Composable
fun ActivationScreenExpandedPreview() {
    DrywallTheme {
        ActivationScreenContent(
            widthSizeClass = WindowWidthSizeClass.Expanded,
            deviceId = "ABC-123-XYZ",
            userName = "Constructora Perez",
            onUserNameChange = {},
            selectedType = LicenseRequestType.MONTH,
            onTypeSelect = {},
            onSendRequest = {},
            licenseKey = "",
            onKeyChange = {},
            errorType = "CODIGO_INVALIDO",
            errorDetail = "Error de prueba",
            onActivate = {},
            license = null,
            isValid = false,
            isExpired = false,
            isManipulated = false,
            remainingTimeText = "",
            currentDarkMode = true,
            onToggleDarkMode = {},
            onRenew = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ActivationScreenActivePreview() {
    DrywallTheme {
        ActivationScreenContent(
            widthSizeClass = WindowWidthSizeClass.Compact,
            deviceId = "ABC-123-XYZ",
            userName = "Juan Perez",
            onUserNameChange = {},
            selectedType = LicenseRequestType.YEAR,
            onTypeSelect = {},
            onSendRequest = {},
            licenseKey = "PREMIUM-LICENSE-KEY",
            onKeyChange = {},
            errorType = null,
            errorDetail = null,
            onActivate = {},
            license = com.drywall.calculator.utils.security.LicenseInfo(
                user = "Juan Perez",
                deviceId = "ABC-123-XYZ",
                creationDate = System.currentTimeMillis(),
                expiryDate = System.currentTimeMillis() + 100000000,
                signature = "sig",
                type = "YEAR"
            ),
            isValid = true,
            isExpired = false,
            isManipulated = false,
            remainingTimeText = "15d 12:30:45",
            currentDarkMode = false,
            onToggleDarkMode = {},
            onRenew = {}
        )
    }
}
