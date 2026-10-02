package com.drywall.keygen


import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drywall.common.utils.DatabaseBackupUtils
import com.drywall.keygen.data.IssuedLicense
import com.drywall.keygen.security.KeyGenSecurity
import com.drywall.keygen.ui.ErrorLogScreen
import com.drywall.keygen.ui.LegendItem
import com.drywall.keygen.ui.RateHistoryChart
import com.drywall.keygen.ui.ReadmeScreen
import com.drywall.keygen.ui.SettingsScreen
import com.drywall.keygen.utils.PdfGenerator
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.platform.LocalLocale

class MainActivity : FragmentActivity() {

    private fun forceExit() {
        finishAndRemoveTask()
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        try {
            KeyGenSecurity.init(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error Crítico de Seguridad: ${e.message}", e)
            Toast.makeText(this, "ERROR DE SEGURIDAD: ${e.message}", Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
            return
        }

        if (isDeviceRooted()) {
            Toast.makeText(this, "ADVERTENCIA: Dispositivo con acceso root detectado. El generador de licencias no se ejecutará por seguridad.", Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
            return
        }

        cacheDir.listFiles()?.forEach { if (it.name.endsWith(".pdf") || it.name.startsWith("DIAGNOSTICO_")) it.delete() }

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }

            val prefs = remember {
                KeyGenSecurity.getEncryptedPrefs(this@MainActivity, "keygen_prefs_encrypted")
            }
            var isDarkMode by remember { 
                mutableStateOf(prefs.getBoolean("is_dark_mode", true)) 
            }
            
            var showSplash by rememberSaveable { mutableStateOf(true) }
            var isAuthenticated by rememberSaveable { mutableStateOf(false) }
            var needsSync by rememberSaveable { mutableStateOf(true) }
            
            val securityPrefs = remember { 
                KeyGenSecurity.getEncryptedPrefs(this@MainActivity, "secure_security_prefs")
            }
            var showSecurityPermissionDialog by rememberSaveable { 
                mutableStateOf(!securityPrefs.getBoolean("security_permission_accepted", false)) 
            }
            
            LaunchedEffect(Unit) {
                delay(2.seconds)
                showSplash = false
                if (!showSecurityPermissionDialog) {
                    authenticateWithBiometrics(
                        onResult = { success ->
                            if (success) {
                                isAuthenticated = true
                            } else {
                                forceExit()
                            }
                        }
                    )
                }
            }

            if (showSecurityPermissionDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Seguridad Requerida") },
                    text = { Text("Para acceder al Generador de Licencias, se requiere autenticación mediante el bloqueo de pantalla de su dispositivo (Biometría o Credenciales).") },
                    confirmButton = {
                        Button(onClick = {
                            securityPrefs.edit().putBoolean("security_permission_accepted", true).apply()
                            showSecurityPermissionDialog = false
                            authenticateWithBiometrics(
                                onResult = { success ->
                                    if (success) {
                                        isAuthenticated = true
                                    } else {
                                        forceExit()
                                    }
                                }
                            )
                        }) { Text("Continuar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { forceExit() }) { Text("Salir") }
                    }
                )
            }

            MaterialTheme(
                colorScheme = if (isDarkMode) {
                    darkColorScheme(
                        primary = Color(0xFF2196F3),
                        secondary = Color(0xFF4CAF50),
                        tertiary = Color(0xFFFF9800)
                    )
                } else {
                    lightColorScheme(
                        primary = Color(0xFF1976D2),
                        secondary = Color(0xFF388E3C),
                        tertiary = Color(0xFFF57C00)
                    )
                }
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (showSplash) {
                        SplashScreenView(version = "v${BuildConfig.VERSION_NAME}")
                    } else if (!isAuthenticated) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (needsSync) {
                        NetworkSyncScreen(
                            snackbarHostState = snackbarHostState,
                            onSyncComplete = { needsSync = false }
                        )
                    } else {
                        MainScreen(
                            snackbarHostState = snackbarHostState,
                            isDarkMode = isDarkMode,
                            onThemeChange = { dark ->
                                isDarkMode = dark
                                prefs.edit().putBoolean("is_dark_mode", dark).apply()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun isDeviceRooted(): Boolean {
        return try {
            val buildTags = android.os.Build.TAGS
            if (buildTags != null && buildTags.contains("test-keys")) return true
            val paths = arrayOf(
                "/system/app/Superuser.apk", "/system/app/su", "/system/bin/su",
                "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
                "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su"
            )
            paths.any { java.io.File(it).exists() }
        } catch (_: Exception) {
            false
        }
    }

    private fun authenticateWithBiometrics(onResult: (Boolean) -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        
        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val executor = ContextCompat.getMainExecutor(this)
                val authPrompt = BiometricPrompt(this, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            onResult(false)
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            onResult(true)
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Acceso Administrativo")
                    .setSubtitle("Autenticación requerida para Keygen")
                    .setAllowedAuthenticators(authenticators)
                    .build()

                authPrompt.authenticate(promptInfo)
            }
            else -> {
                Toast.makeText(this, "Debe configurar un bloqueo de pantalla seguro", Toast.LENGTH_LONG).show()
                onResult(false)
            }
        }
    }
}

@Composable
fun NetworkSyncScreen(snackbarHostState: SnackbarHostState, onSyncComplete: () -> Unit) {
    var isSyncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var syncedTimeText by remember { mutableStateOf("") }
    var usedServerText by remember { mutableStateOf("") }
    var timeDiscrepancy by remember { mutableLongStateOf(0L) }
    var syncAttempts by remember { mutableIntStateOf(0) }
    var showAppExitNotice by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun startSync() {
        scope.launch {
            isSyncing = true
            syncError = null
            val result = com.drywall.common.security.NetworkTimeProvider.syncWithInternet()
            if (result.isSuccess) {
                val networkTime = result.getOrNull() ?: 0L
                val deviceTime = System.currentTimeMillis()
                timeDiscrepancy = networkTime - deviceTime
                
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                syncedTimeText = sdf.format(java.util.Date(networkTime))
                usedServerText = com.drywall.common.security.NetworkTimeProvider.getLastUsedServer()
                
                if (abs(timeDiscrepancy) < 60000) {
                    delay(1000.milliseconds)
                    onSyncComplete()
                }
            } else {
                syncAttempts++
                if (syncAttempts < 3) {
                    syncError = "Error de conexión. Reintentando en 3 segundos... (Intento $syncAttempts de 3)"
                    delay(3000.milliseconds)
                    startSync()
                } else {
                    syncError = "No se pudo establecer conexión tras 3 intentos."
                    showAppExitNotice = true
                }
            }
            isSyncing = false
        }
    }

    LaunchedEffect(Unit) {
        startSync()
    }

    if (showAppExitNotice) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Fallo de Sincronización") },
            text = { Text("No se pudo obtener la hora real de Internet tras varios intentos. La generación de licencias requiere hora precisa.\n\nLa aplicación se cerrará.") },
            confirmButton = {
                Button(onClick = { 
                    showAppExitNotice = false
                    syncAttempts = 0
                    startSync()
                }) { Text("Reintentar") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    (context as? android.app.Activity)?.finish() 
                }) { Text("Salir") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.WifiProtectedSetup,
            contentDescription = "Icono de Sincronización",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Sincronización de Tiempo",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Para generar licencias válidas, el reloj debe coincidir con la hora real de Internet.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (isSyncing) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Conectando con servidores NTP...")
        } else if (syncedTimeText.isNotEmpty()) {
            val isTimeAccurate = abs(timeDiscrepancy) < 60000

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isTimeAccurate) MaterialTheme.colorScheme.primaryContainer 
                                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (isTimeAccurate) "Sincronización Exitosa" else "RELOJ DESINCRONIZADO", fontWeight = FontWeight.Bold)
                    Text("Hora Red: $syncedTimeText", style = MaterialTheme.typography.bodyLarge)
                    
                    if (!isTimeAccurate) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Diferencia: ${abs(timeDiscrepancy / 1000 / 60)} min. Corrija para continuar.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text("Servidor: $usedServerText", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (isTimeAccurate) {
                Button(
                    onClick = onSyncComplete,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Continuar")
                }
            } else {
                Button(
                    onClick = {
                        try {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS))
                        } catch (e: Exception) {
                            scope.launch {
                                snackbarHostState.showSnackbar("No se pudo abrir los ajustes")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Corregir Hora")
                }
                TextButton(onClick = { syncedTimeText = "" }) {
                    Text("Reintentar")
                }
            }
        } else {
            if (syncError != null) {
                Text(syncError!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
            }
            Button(
                onClick = { startSync() },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.CloudSync, null)
                Spacer(Modifier.width(8.dp))
                Text("Sincronizar Hora Internacional")
            }
        }
    }
}


@Composable
fun SplashScreenView(version: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Logo de Keygen Pro",
                modifier = Modifier.size(180.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "KEYGEN PRO YHQUINTERO",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
        
        Text(
            text = "Versión $version",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun AnimatedMenuIcon(progress: Float) {
    val safeProgress = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val color = Color.White
        val thickness = 2.dp
        val width = 18.dp
        val density = LocalDensity.current
        val translationYDist = with(density) { 6.dp.toPx() }
        val translationXDist = with(density) { 2.dp.toPx() }
        
        // Línea Superior (Creciente 45 grados)
        Box(
            modifier = Modifier
                .size(width, thickness)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationZ = 45f * safeProgress
                    translationY = -translationYDist * (1 - safeProgress)
                    translationX = translationXDist * safeProgress
                    scaleX = 1f - (0.2f * safeProgress)
                }
                .background(color, CircleShape)
                .align(Alignment.CenterStart)
                .padding(start = 3.dp)
        )
        // Línea Media
        Box(
            modifier = Modifier
                .size(width, thickness)
                .graphicsLayer {
                    translationX = translationXDist * safeProgress
                }
                .background(color, CircleShape)
                .align(Alignment.CenterStart)
                .padding(start = 3.dp)
        )
        // Línea Inferior (Decreciente 45 grados)
        Box(
            modifier = Modifier
                .size(width, thickness)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationZ = -45f * safeProgress
                    translationY = translationYDist * (1 - safeProgress)
                    translationX = translationXDist * safeProgress
                    scaleX = 1f - (0.2f * safeProgress)
                }
                .background(color, CircleShape)
                .align(Alignment.CenterStart)
                .padding(start = 3.dp)
        )
    }
}

data class KeygenMenuItem(val index: Int, val label: String, val icon: ImageVector, val description: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: KeygenViewModel = viewModel(),
    isDarkMode: Boolean = true,
    onThemeChange: (Boolean) -> Unit = {}
) {
    val menuItems = listOf(
        KeygenMenuItem(0, "Generador", Icons.Default.AddCircle, "Módulo principal para generar códigos de licencia profesional para clientes."),
        KeygenMenuItem(1, "Historial", Icons.Default.History, "Registro completo de todas las licencias emitidas, estados de pago y reenvío de códigos."),
        KeygenMenuItem(2, "Evolución", Icons.Default.Timeline, "Gráfico interactivo de la variación de tasas de cambio (USD, EUR, MLC) en el tiempo."),
        KeygenMenuItem(3, "Ingresos", Icons.Default.Assessment, "Panel de estadísticas financieras con el total recaudado por cada tipo de moneda."),
        KeygenMenuItem(4, "Ayuda / Info", Icons.Default.Info, "Manual de usuario, consejos de uso y contacto de soporte técnico."),
        KeygenMenuItem(5, "Ajustes", Icons.Default.Settings, "Configuración del tema, tasas de cambio y preferencias del sistema."),
        KeygenMenuItem(6, "Diagnóstico", Icons.Default.Troubleshoot, "Centro de reporte de fallos optimizado para análisis con Gemini AI.")
    )

    var selectedTab by remember { mutableIntStateOf(0) }
    val version = "v${BuildConfig.VERSION_NAME}"
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var backPressedTime by remember { mutableLongStateOf(0L) }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 0,
        pageCount = { menuItems.size }
    )
    
    val lazyListState = rememberLazyListState()

    // Sincronizar Pager -> Menú (Chips)
    LaunchedEffect(pagerState.currentPage) {
        lazyListState.animateScrollToItem(pagerState.currentPage)
    }

    BackHandler {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (pagerState.currentPage != 0) {
            scope.launch { pagerState.animateScrollToPage(0) }
        } else {
            if (backPressedTime + 2000 > System.currentTimeMillis()) {
                val activity = context as? android.app.Activity
                activity?.finishAndRemoveTask()
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Haga clic de nuevo para salir")
                }
                backPressedTime = System.currentTimeMillis()
            }
        }
    }

    val density = LocalDensity.current
    val drawerWidthPx = with(density) { 300.dp.toPx() }

    val drawerProgress by remember(drawerState) {
        derivedStateOf {
            val offsetValue = try { drawerState.currentOffset } catch (_: Exception) {
                if (drawerState.isOpen) 0f else -drawerWidthPx
            }
            if (drawerWidthPx > 0f && !offsetValue.isNaN()) {
                ((offsetValue + drawerWidthPx) / drawerWidthPx).coerceIn(0f, 1f)
            } else {
                if (drawerState.isOpen) 1f else 0f
            }
        }
    }

    var selectedMenuItemForInfo by remember { mutableStateOf<KeygenMenuItem?>(null) }
    
    val scrapeSuccess by viewModel.scrapeSuccess.collectAsState()
    val scrapeError by viewModel.error.collectAsState()

    LaunchedEffect(scrapeSuccess) {
        if (scrapeSuccess) {
            snackbarHostState.showSnackbar(
                message = "Tasas actualizadas correctamente desde eltoque.com",
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            viewModel.clearSuccess()
        }
    }

    LaunchedEffect(scrapeError) {
        scrapeError?.let {
            if (it.contains("Conexión restablecida") || it.contains("Cloudflare")) {
                snackbarHostState.showSnackbar(
                    message = it,
                    duration = androidx.compose.material3.SnackbarDuration.Long
                )
            }
        }
    }

    if (selectedMenuItemForInfo != null) {
        AlertDialog(
            onDismissRequest = { selectedMenuItemForInfo = null },
            icon = { Icon(selectedMenuItemForInfo!!.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(selectedMenuItemForInfo!!.label, fontWeight = FontWeight.Bold) },
            text = { Text(selectedMenuItemForInfo!!.description, textAlign = TextAlign.Center) },
            confirmButton = {
                TextButton(onClick = { selectedMenuItemForInfo = null }) { Text("Entendido") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("KEYGEN PRO YHQUINTERO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        Text(version, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        scope.launch { 
                            if (drawerState.isOpen) drawerState.close() else drawerState.open()
                        } 
                    }) {
                        AnimatedMenuIcon(progress = drawerProgress)
                    }
                },
                actions = {
                    IconButton(onClick = { onThemeChange(!isDarkMode) }) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Cambiar Tema"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Barra de Menú (Chips) SIEMPRE ARRIBA y fuera del Drawer
            Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                LazyRow(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    items(menuItems) { item ->
                        val isSelected = pagerState.currentPage == item.index
                        val color = when (item.index) {
                            0 -> Color(0xFF2196F3)
                            1 -> Color(0xFFFF9800)
                            2 -> Color(0xFFE91E63)
                            3 -> Color(0xFF4CAF50)
                            4 -> Color(0xFF00BCD4)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        
                        FilterChip(
                            selected = isSelected,
                            onClick = { 
                                scope.launch { pagerState.animateScrollToPage(item.index) }
                            },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isSelected) Color.White else color
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(40.dp)
                        )
                    }
                }
            }

            // El Drawer y el Pager se ejecutan debajo de la barra de chips
            ModalNavigationDrawer(
                drawerState = drawerState,
                modifier = Modifier.weight(1f),
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(300.dp).fillMaxHeight()
                    ) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "MENÚ KEYGEN PRO", 
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).alpha(0.5f))
                        
                        menuItems.forEach { item ->
                            NavigationDrawerItem(
                                icon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text(item.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = { selectedMenuItemForInfo = item },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                        }
                                    }
                                },
                                selected = pagerState.currentPage == item.index,
                                onClick = {
                                    scope.launch { 
                                        pagerState.animateScrollToPage(item.index)
                                        drawerState.close() 
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).height(48.dp),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    selectedIconColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        
                        Spacer(Modifier.weight(1f))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        
                        NavigationDrawerItem(
                            label = { Text("Configuración Tema", style = MaterialTheme.typography.labelLarge) },
                            selected = false,
                            onClick = { onThemeChange(!isDarkMode) },
                            icon = { Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null) },
                            modifier = Modifier.padding(12.dp)
                        )
                        
                        Text(
                            "YHQuintero Soluciones v${BuildConfig.VERSION_NAME}",
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            ) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> GeneratorScreen(
                            snackbarHostState = snackbarHostState,
                            scope = scope,
                            onNavigateToEvolution = { 
                                scope.launch { pagerState.animateScrollToPage(2) }
                            }
                        )
                        1 -> LicenseHistoryScreen(
                            snackbarHostState = snackbarHostState,
                            scope = scope
                        )
                        2 -> {
                            LaunchedEffect(Unit) {
                                viewModel.fetchRates()
                            }
                            EvolutionScreen()
                        }
                        3 -> StatisticsScreen()
                        4 -> ReadmeScreen()
                        5 -> SettingsScreen(
                            snackbarHostState = snackbarHostState,
                            isDarkMode = isDarkMode,
                            onThemeChange = onThemeChange
                        )
                        6 -> ErrorLogScreen()
                    }
                }
            }
        }
    }
}

data class LicenseInfo(
    @SerializedName("user") val user: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("creationDate") val creationDate: Long,
    @SerializedName("expiryDate") val expiryDate: Long,
    @SerializedName("signature") val signature: String,
    @SerializedName("type") val type: String,
    @SerializedName("issuerKey") val issuerKey: String
)

data class ExchangeRates(
    @SerializedName("usdToCup") val usdToCup: Double,
    @SerializedName("eurToCup") val eurToCup: Double,
    @SerializedName("mlcToCup") val mlcToCup: Double,
    @SerializedName("cadToCup") val cadToCup: Double,
    @SerializedName("mexToCup") val mexToCup: Double,
    @SerializedName("zelleToCup") val zelleToCup: Double,
    @SerializedName("claToCup") val claToCup: Double,
    @SerializedName("lastUpdate") val lastUpdate: Date
)

enum class Currency(val symbol: String, val code: String) {
    USD("$", "USD"),
    EUR("€", "EUR"),
    MLC("MLC", "MLC"),
    CAD("C$", "CAD"),
    MEX("Mex$", "MEX"),
    ZELLE("Zelle", "ZELLE"),
    CLA("CLA", "CLA")
}

enum class LicenseTypeBase(
    val label: String,
    val days: Int,
    val defaultUsdPrice: Double
) {
    ONE_DAY("1 Día Profesional", 1, 5.00),
    ONE_WEEK("1 Semana Profesional", 7, 20.00),
    ONE_MONTH("1 Mes Profesional", 30, 50.00),
    ONE_YEAR("1 Año Profesional", 365, 300.00),
    TWO_YEARS("2 Años Profesional", 730, 500.00)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun GeneratorScreen(
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    viewModel: KeygenViewModel = viewModel(),
    onNavigateToEvolution: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember {
        KeyGenSecurity.getEncryptedPrefs(context, "keygen_prefs_encrypted")
    }
    
    val rateHistory by viewModel.rateHistory.collectAsState()
    val errorMsg by viewModel.error.collectAsState()

    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    var lastGenerateTime by remember { 
        mutableLongStateOf(prefs.getLong("last_license_generation_time", 0L)) 
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { 
            scope.launch(Dispatchers.IO) {
                try {
                    val db = com.drywall.keygen.data.KeygenDatabase.getDatabase(context)
                    val wdb = db.openHelper.writableDatabase
                    wdb.query(androidx.sqlite.db.SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))
                    wdb.query(androidx.sqlite.db.SimpleSQLiteQuery("VACUUM"))
                } catch (e: Exception) {
                    Log.w("MainActivity", "Pre-export VACUUM failed: ${e.message}")
                }
                DatabaseBackupUtils.exportFullBackup(context, it)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { DatabaseBackupUtils.importFullBackup(context, it) }
    }

    var manualUsdRate by remember { mutableStateOf("") }
    var manualEurRate by remember { mutableStateOf("") }
    var manualMlcRate by remember { mutableStateOf("") }
    var manualCadRate by remember { mutableStateOf("") }
    var manualMexRate by remember { mutableStateOf("") }
    var manualZelleRate by remember { mutableStateOf("") }
    var manualClaRate by remember { mutableStateOf("") }
    
    val scrapedRates by viewModel.currentRates.collectAsState()
    val scrapedTimestamp by viewModel.lastScrapedTimestamp.collectAsState()

    var exchangeRates by remember { mutableStateOf<ExchangeRates?>(null) }

    var customPriceOneDay by remember { mutableStateOf("5.0") }
    var customPriceOneWeek by remember { mutableStateOf("20.0") }
    var customPriceOneMonth by remember { mutableStateOf("50.0") }
    var customPriceOneYear by remember { mutableStateOf("300.0") }
    var customPriceTwoYears by remember { mutableStateOf("500.0") }

    var user by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var requestDate by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(LicenseTypeBase.ONE_MONTH) }
    var selectedCurrency by remember { mutableStateOf(Currency.USD) }
    var showSettings by remember { mutableStateOf(false) }
    var showPublicKeyDialog by remember { mutableStateOf(false) }
    
    var selectedPageSize by remember { mutableStateOf(PdfGenerator.PageSize.A4) }
    var showPageSizeDialog by remember { mutableStateOf(false) }
    var pendingInvoiceData by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    if (showPageSizeDialog && pendingInvoiceData != null) {
        AlertDialog(
            onDismissRequest = { 
                showPageSizeDialog = false 
                pendingInvoiceData = null
            },
            title = { Text("Tamaño de hoja") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seleccione el formato de papel:")
                    PdfGenerator.PageSize.entries.forEach { ps ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPageSize = ps }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ps.label)
                            if (selectedPageSize == ps) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPageSizeDialog = false
                    pendingInvoiceData?.let { (u, d, p) ->
                        PdfGenerator.generateInvoicePdf(context, u, d, selectedType.label, p, selectedPageSize)
                    }
                    pendingInvoiceData = null
                }) { Text("Generar") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showPageSizeDialog = false 
                    pendingInvoiceData = null
                }) { Text("Cancelar") }
            }
        )
    }

    LaunchedEffect(Unit) {
        manualUsdRate = prefs.getString("usd_rate", "0.00") ?: "0.00"
        manualEurRate = prefs.getString("eur_rate", "0.00") ?: "0.00"
        manualMlcRate = prefs.getString("mlc_rate", "0.00") ?: "0.00"
        manualCadRate = prefs.getString("cad_rate", "0.00") ?: "0.00"
        manualMexRate = prefs.getString("mex_rate", "0.00") ?: "0.00"
        manualZelleRate = prefs.getString("zelle_rate", "0.00") ?: "0.00"
        manualClaRate = prefs.getString("cla_rate", "0.00") ?: "0.00"
        
        val usd = manualUsdRate.toDoubleOrNull() ?: 0.0
        val eur = manualEurRate.toDoubleOrNull() ?: 0.0
        val mlc = manualMlcRate.toDoubleOrNull() ?: 0.0
        val cad = manualCadRate.toDoubleOrNull() ?: 0.0
        val mex = manualMexRate.toDoubleOrNull() ?: 0.0
        val zelle = manualZelleRate.toDoubleOrNull() ?: 0.0
        val cla = manualClaRate.toDoubleOrNull() ?: 0.0
        
        exchangeRates = ExchangeRates(usd, eur, mlc, cad, mex, zelle, cla, Date())

        customPriceOneDay = prefs.getString("custom_price_day", "5.0") ?: "5.0"
        customPriceOneWeek = prefs.getString("custom_price_week", "20.0") ?: "20.0"
        customPriceOneMonth = prefs.getString("custom_price_month", "50.0") ?: "50.0"
        customPriceOneYear = prefs.getString("custom_price_year", "300.0") ?: "300.0"
        customPriceTwoYears = prefs.getString("custom_price_twoyears", "500.0") ?: "500.0"
        
        viewModel.fetchRates()
    }

    LaunchedEffect(scrapedRates) {
        if (scrapedRates.isNotEmpty()) {
            scrapedRates["USD"]?.let { manualUsdRate = it.toString() }
            scrapedRates["EUR"]?.let { manualEurRate = it.toString() }
            scrapedRates["MLC"]?.let { manualMlcRate = it.toString() }
            scrapedRates["CAD"]?.let { manualCadRate = it.toString() }
            scrapedRates["MEX"]?.let { manualMexRate = it.toString() }
            scrapedRates["ZELLE"]?.let { manualZelleRate = it.toString() }
            scrapedRates["CLA"]?.let { manualClaRate = it.toString() }
            
            exchangeRates = ExchangeRates(
                manualUsdRate.toDoubleOrNull() ?: 0.0,
                manualEurRate.toDoubleOrNull() ?: 0.0,
                manualMlcRate.toDoubleOrNull() ?: 0.0,
                manualCadRate.toDoubleOrNull() ?: 0.0,
                manualMexRate.toDoubleOrNull() ?: 0.0,
                manualZelleRate.toDoubleOrNull() ?: 0.0,
                manualClaRate.toDoubleOrNull() ?: 0.0,
                Date()
            )
            
            prefs.edit().apply {
                putString("usd_rate", manualUsdRate)
                putString("eur_rate", manualEurRate)
                putString("mlc_rate", manualMlcRate)
                putString("cad_rate", manualCadRate)
                putString("mex_rate", manualMexRate)
                putString("zelle_rate", manualZelleRate)
                putString("cla_rate", manualClaRate)
                apply()
            }
        }
    }

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("DATOS DEL PEDIDO", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (scrapedTimestamp.isNotBlank()) {
                    Text("Actualizado: $scrapedTimestamp", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { exportLauncher.launch(com.drywall.common.utils.DatabaseBackupUtils.getSuggestedFileName(false)) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Save, "Exportar", modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Restore, "Importar", modifier = Modifier.size(20.dp)) }
            }
        }

        OutlinedTextField(
            value = user,
            onValueChange = { user = it.replace("\n", "").replace("\r", "") },
            label = { Text("Nombre Cliente") }, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Cliente") },
            singleLine = true,
            readOnly = true
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = deviceId,
                onValueChange = { deviceId = it.replace("\n", "").replace("\r", "") },
                label = { Text("ID Dispositivo") }, modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = "ID Dispositivo") },
                singleLine = true,
                readOnly = true
            )
            IconButton(onClick = {
                val text = clipboardManager.getText()?.text
                if (!text.isNullOrBlank()) {
                    if (text.contains("ID Dispositivo:") || text.contains("🆔 ID Dispositivo:")) {
                        deviceId = if (text.contains("🆔 ID Dispositivo:")) {
                            text.substringAfter("🆔 ID Dispositivo:").trim().split("\n")[0].trim()
                        } else {
                            text.substringAfter("ID Dispositivo:").trim().split("\n")[0].trim()
                        }

                        user = if (text.contains("👤 Usuario:")) {
                            text.substringAfter("👤 Usuario:").trim().split("\n")[0].trim()
                        } else if (text.contains("Usuario:")) {
                            text.substringAfter("Usuario:").trim().split("\n")[0].trim()
                        } else {
                            user
                        }

                        requestDate = if (text.contains("🕒 Fecha Actual:")) {
                            text.substringAfter("🕒 Fecha Actual:").trim().split("\n")[0].trim()
                        } else if (text.contains("Fecha Actual:")) {
                            text.substringAfter("Fecha Actual:").trim().split("\n")[0].trim()
                        } else {
                            ""
                        }

                        val planLine = if (text.contains("📅 Plan:")) {
                            text.substringAfter("📅 Plan:").trim().split("\n")[0]
                        } else if (text.contains("Plan solicitado:")) {
                            text.substringAfter("Plan solicitado:").trim().split("\n")[0]
                        } else {
                            null
                        }

                        planLine?.let { planStr ->
                            LicenseTypeBase.entries.find {
                                planStr.contains(it.label, ignoreCase = true) ||
                                it.label.contains(planStr, ignoreCase = true)
                            }?.let {
                                selectedType = it
                            }
                        }
                        scope.launch {
                            snackbarHostState.showSnackbar("Datos cargados de WhatsApp")
                        }
                    } else {
                        deviceId = text
                        scope.launch {
                            snackbarHostState.showSnackbar("ID pegado desde portapapeles")
                        }
                    }
                }
            }) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Pegar desde WhatsApp",
                    tint = Color(0xFF25D366),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Currency.entries.forEach { curr ->
                    val isSelected = selectedCurrency == curr
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCurrency = curr },
                        label = { Text(curr.code, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = "Seleccionado", modifier = Modifier.size(14.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = requestDate,
                onValueChange = { requestDate = it },
                label = { Text("Fecha Solicitud (App)") }, modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Default.Event, contentDescription = "Fecha Solicitud") },
                singleLine = true,
                readOnly = true
            )
            val isDateOk = remember(requestDate) {
                if (requestDate.isBlank()) true
                else {
                    try {
                        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                        val reqDate = sdf.parse(requestDate)
                        val now = Date()
                        // La fecha del móvil no puede ser superior a la del Keygen (que está sincronizado)
                        // ni estar demasiado atrasada (más de 1 hora de diferencia para ser estrictos, 
                        // pero el usuario dice "sincronizado obligatoriamente")
                        abs(now.time - reqDate.time) < 300_000 // 5 minutos de tolerancia
                    } catch (e: Exception) { false }
                }
            }
            if (requestDate.isNotBlank()) {
                Icon(
                    imageVector = if (isDateOk) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isDateOk) Color(0xFF4CAF50) else Color.Red
                )
            }
        }

        // Botón movido arriba de la lista de planes para "subirlo"
        Button(
            onClick = {
                if (requestDate.isNotBlank()) {
                    try {
                        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                        val reqDate = sdf.parse(requestDate)
                        val now = Date()
                        if (reqDate != null) {
                            if (abs(now.time - reqDate.time) > 300_000) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("ERROR: El reloj del cliente está desincronizado. Debe actualizarlo.")
                                }
                                return@Button
                            }
                        }
                    } catch (e: Exception) {
                        scope.launch { snackbarHostState.showSnackbar("Error al validar fecha") }
                        return@Button
                    }
                }
                val now = System.currentTimeMillis()
                val minInterval = 30_000L
                if (now - lastGenerateTime < minInterval) {
                    val remaining = (minInterval - (now - lastGenerateTime)) / 1000
                    scope.launch {
                    snackbarHostState.showSnackbar("Espere ${remaining} segundos antes de generar otra licencia")
                }
                    return@Button
                }
                lastGenerateTime = now
                prefs.edit().putLong("last_license_generation_time", now).apply()
                val usdPrice = when (selectedType) {
                    LicenseTypeBase.ONE_DAY -> customPriceOneDay.toDoubleOrNull() ?: 1.0
                    LicenseTypeBase.ONE_WEEK -> customPriceOneWeek.toDoubleOrNull() ?: 5.0
                    LicenseTypeBase.ONE_MONTH -> customPriceOneMonth.toDoubleOrNull() ?: 10.0
                    LicenseTypeBase.ONE_YEAR -> customPriceOneYear.toDoubleOrNull() ?: 100.0
                    LicenseTypeBase.TWO_YEARS -> customPriceTwoYears.toDoubleOrNull() ?: 180.0
                }
                val rates = exchangeRates
                val price = if (rates != null && rates.usdToCup > 0) {
                    when (selectedCurrency) {
                        Currency.USD -> usdPrice
                        Currency.EUR -> (usdPrice * rates.usdToCup) / rates.eurToCup
                        Currency.MLC -> (usdPrice * rates.usdToCup) / rates.mlcToCup
                        Currency.CAD -> (usdPrice * rates.usdToCup) / rates.cadToCup
                        Currency.MEX -> (usdPrice * rates.usdToCup) / rates.mexToCup
                        Currency.ZELLE -> (usdPrice * rates.usdToCup) / rates.zelleToCup
                        Currency.CLA -> (usdPrice * rates.usdToCup) / rates.claToCup
                    }
                } else usdPrice
                val priceStr = "${String.format(Locale.US, "%.2f", price)} ${selectedCurrency.code}"

                viewModel.registerLicenseRequest(user, deviceId, selectedType.label, priceStr)
                pendingInvoiceData = Triple(user, deviceId, priceStr)
                showPageSizeDialog = true
            },
            enabled = user.isNotBlank() && deviceId.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Print, null)
            Spacer(Modifier.width(8.dp))
            Text("GENERAR FACTURA Y REGISTRAR")
        }

        Card(modifier = Modifier.weight(1f)) {
            Column(Modifier.selectableGroup().padding(2.dp).verticalScroll(rememberScrollState())) {
                LicenseTypeBase.entries.forEach { type ->
                    val usdPrice = when (type) {
                        LicenseTypeBase.ONE_DAY -> customPriceOneDay.toDoubleOrNull() ?: type.defaultUsdPrice
                        LicenseTypeBase.ONE_WEEK -> customPriceOneWeek.toDoubleOrNull() ?: type.defaultUsdPrice
                        LicenseTypeBase.ONE_MONTH -> customPriceOneMonth.toDoubleOrNull() ?: type.defaultUsdPrice
                        LicenseTypeBase.ONE_YEAR -> customPriceOneYear.toDoubleOrNull() ?: type.defaultUsdPrice
                        LicenseTypeBase.TWO_YEARS -> customPriceTwoYears.toDoubleOrNull() ?: type.defaultUsdPrice
                    }
                    val rates = exchangeRates
                    val price = if (rates != null && rates.usdToCup > 0) {
                        when (selectedCurrency) {
                            Currency.USD -> usdPrice
                            Currency.EUR -> (usdPrice * rates.usdToCup) / rates.eurToCup
                            Currency.MLC -> (usdPrice * rates.usdToCup) / rates.mlcToCup
                            Currency.CAD -> (usdPrice * rates.usdToCup) / rates.cadToCup
                            Currency.MEX -> (usdPrice * rates.usdToCup) / rates.mexToCup
                            Currency.ZELLE -> (usdPrice * rates.usdToCup) / rates.zelleToCup
                            Currency.CLA -> (usdPrice * rates.usdToCup) / rates.claToCup
                        }
                    } else usdPrice
                    val priceStr = "${String.format(Locale.US, "%.2f", price)} ${selectedCurrency.code}"

                    Surface(
                        onClick = { selectedType = type },
                        color = if (type == selectedType) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (type == selectedType), onClick = null)
                            Text(type.label, modifier = Modifier.padding(start = 8.dp).weight(1f), fontSize = 12.sp, fontWeight = if(type == selectedType) FontWeight.Bold else FontWeight.Normal)
                            Text(priceStr, fontWeight = FontWeight.Bold, color = if(type == selectedType) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LicenseHistoryScreen(
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    viewModel: KeygenViewModel = viewModel()
) {
    val licenses by viewModel.licenses.collectAsState()
    val errorMsg by viewModel.error.collectAsState()
    val context = LocalContext.current
    var licenseToShowJson by remember { mutableStateOf<IssuedLicense?>(null) }
    var licenseToDelete by remember { mutableStateOf<IssuedLicense?>(null) }
    
    var selectedPageSize by remember { mutableStateOf(PdfGenerator.PageSize.A4) }
    var showPageSizeDialog by remember { mutableStateOf(false) }

    if (showPageSizeDialog) {
        AlertDialog(
            onDismissRequest = { showPageSizeDialog = false },
            title = { Text("Tamaño de hoja") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seleccione el formato de papel para el historial:")
                    PdfGenerator.PageSize.entries.forEach { ps ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPageSize = ps }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ps.label)
                            if (selectedPageSize == ps) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPageSizeDialog = false
                    PdfGenerator.generateFullHistoryPdf(context, licenses, selectedPageSize)
                }) { Text("Generar") }
            },
            dismissButton = {
                TextButton(onClick = { showPageSizeDialog = false }) { Text("Cancelar") }
            }
        )
    }

    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CONTROL DE SALIDA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showPageSizeDialog = true }) {
                Icon(Icons.Default.Print, contentDescription = "Exportar Historial", tint = MaterialTheme.colorScheme.primary)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(licenses) { license ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(license.userName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Surface(color = if (license.isPaid) Color(0xFF4CAF50) else Color(0xFFFF9800), shape = RoundedCornerShape(4.dp)) {
                                Text(if (license.isPaid) "PAGADO" else "PENDIENTE", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                        val sdf = SimpleDateFormat("dd/MM/yy HH:mm:ss", LocalLocale.current.platformLocale)
                        Text("Registro: ${sdf.format(Date(license.dateIssued))}", fontSize = 11.sp, color = Color.Gray)
                        
                        if (license.isPaid) {
                            val gson = com.google.gson.Gson()
                            val info = try { gson.fromJson(license.licenseJson, LicenseInfo::class.java) } catch(e: Exception) { null }
                            info?.let {
                                Text("Vigencia: ${sdf.format(Date(it.creationDate))} al ${sdf.format(Date(it.expiryDate))}", 
                                     fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        }

                        Text("ID: ${license.deviceId} | Plan: ${license.planType}", fontSize = 12.sp, color = Color.Gray)
                        Text("Costo: ${license.price}", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { licenseToDelete = license }) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = { PdfGenerator.generateInvoicePdf(context, license.userName, license.deviceId, license.planType, license.price) }) { Icon(Icons.Default.Print, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                            if (!license.isPaid) {
                                Button(
                                    onClick = {
                                        val days = when (license.planType) { 
                                            "1 Día Profesional" -> 1
                                            "1 Semana Profesional" -> 7
                                            "1 Mes Profesional" -> 30 
                                            "1 Año Profesional" -> 365 
                                            "2 Años Profesional" -> 730 
                                            else -> 30 
                                        }
                                        val calendar = Calendar.getInstance()
                                        // Usar hora de red si está disponible para evitar problemas con reloj manual
                                        val baseTime = com.drywall.common.security.NetworkTimeProvider.getSyncedTime()
                                        calendar.timeInMillis = baseTime
                                        val creationDate = baseTime
                                        
                                        calendar.add(Calendar.DAY_OF_YEAR, days)
                                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                                        calendar.set(Calendar.MINUTE, 59)
                                        calendar.set(Calendar.SECOND, 59)
                                        calendar.set(Calendar.MILLISECOND, 0)
                                        val expiry = calendar.timeInMillis
                                        
                                        val cleanUser = license.userName.trim()
                                        val cleanDeviceId = license.deviceId.trim()
                                        val dataToSign = "$cleanUser|$cleanDeviceId|$creationDate|$expiry"
                                        val signature = KeyGenSecurity.signData(dataToSign)
                                        if (signature == null) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Error CRÍTICO: No se pudo firmar la licencia. Verifique logs.")
                                            }
                                            return@Button
                                        }
                                        
                                        val typeName = when (license.planType) { 
                                            "1 Día Profesional" -> "1 DÍA PROFESIONAL"
                                            "1 Semana Profesional" -> "1 SEMANA PROFESIONAL"
                                            "1 Mes Profesional" -> "1 MES PROFESIONAL" 
                                            "1 Año Profesional" -> "1 AÑO PROFESIONAL" 
                                            "2 Años Profesional" -> "2 AÑOS PROFESIONAL" 
                                            else -> "1 MES PROFESIONAL"
                                        }
                                        val licenseInfo = LicenseInfo(cleanUser, cleanDeviceId, creationDate, expiry, signature, typeName, KeyGenSecurity.publicKeyString)
                                        val gson = com.google.gson.GsonBuilder().disableHtmlEscaping().create()
                                        val json = gson.toJson(licenseInfo)
                                        viewModel.markAsPaid(license, json)
                                        shareLicense(context, json, "com.whatsapp", "WhatsApp")
                                    },
                                    modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp)
                                ) { Text("Cobrar", fontSize = 12.sp) }
                            } else {
                                val gson = com.google.gson.Gson()
                                val licenseInfo = try { gson.fromJson(license.licenseJson, LicenseInfo::class.java) } catch (e: Exception) { null }
                                if (licenseInfo != null) {
                                    val remaining = licenseInfo.expiryDate - System.currentTimeMillis()
                                    val days = TimeUnit.MILLISECONDS.toDays(remaining)
                                    if (days in 0..5) {
                                        IconButton(onClick = {
                                            val msg = if (days > 0) {
                                                "Hola ${license.userName}, le informamos que su licencia de Calculadora Drywall vencerá en $days días. ¡Recuerde renovar para no perder sus funciones!"
                                            } else {
                                                "Hola ${license.userName}, su licencia de Calculadora Drywall ha vencido. Contacte con nosotros para renovar su suscripción."
                                            }
                                            shareLicense(context, msg, "com.whatsapp", "WhatsApp")
                                        }) {
                                            Icon(Icons.Default.NotificationsActive, null, tint = Color(0xFFFF5722), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                                IconButton(onClick = { licenseToShowJson = license }) { Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { shareLicense(context, license.licenseJson, "com.whatsapp", "WhatsApp") }) { Icon(Icons.Default.Share, null, tint = Color(0xFF25D366)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (licenseToDelete != null) {
        AlertDialog(
            onDismissRequest = { licenseToDelete = null },
            title = { Text("Eliminar Licencia") },
            text = { Text("¿Desea eliminar la licencia de '${licenseToDelete!!.userName}' definitivamente?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLicense(licenseToDelete!!)
                        licenseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { licenseToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    if (licenseToShowJson != null) {
        Dialog(onDismissRequest = { licenseToShowJson = null }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Código de Licencia", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = licenseToShowJson!!.licenseJson, onValueChange = {}, readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        textStyle = MaterialTheme.typography.bodySmall,
                        minLines = 5
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        @Suppress("DEPRECATION")
                        val clipboard = LocalClipboardManager.current
                        Button(onClick = { 
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(licenseToShowJson!!.licenseJson))
                            scope.launch {
                        snackbarHostState.showSnackbar("Copiado al portapapeles")
                    }
                        }, modifier = Modifier.weight(1f)) { Text("Copiar") }
                        Button(onClick = { licenseToShowJson = null }, modifier = Modifier.weight(1f)) { Text("Cerrar") }
                    }
                }
            }
        }
    }
}

@Composable
fun EvolutionScreen(viewModel: KeygenViewModel = viewModel()) {
    val rateHistory by viewModel.rateHistory.collectAsState()
    val scrapedTimestamp by viewModel.lastScrapedTimestamp.collectAsState()
    val isScraping by viewModel.isScraping.collectAsState()
    val context = LocalContext.current
    
    var selectedRange by remember { mutableStateOf("1S") }
    val ranges = listOf("1S", "1M", "3M", "6M", "1A", "2A", "5A")

    var selectedPageSize by remember { mutableStateOf(PdfGenerator.PageSize.A4) }
    var showPageSizeDialog by remember { mutableStateOf(false) }

    if (showPageSizeDialog) {
        AlertDialog(
            onDismissRequest = { showPageSizeDialog = false },
            title = { Text("Tamaño de hoja") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seleccione el formato de papel para el reporte de evolución:")
                    PdfGenerator.PageSize.entries.forEach { ps ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPageSize = ps }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ps.label)
                            if (selectedPageSize == ps) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPageSizeDialog = false
                    PdfGenerator.generateEvolutionOnlyPdf(context, rateHistory, selectedPageSize)
                }) { Text("Generar") }
            },
            dismissButton = {
                TextButton(onClick = { showPageSizeDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("EVOLUCIÓN DE TASAS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (scrapedTimestamp.isNotBlank()) {
                    Text("En tiempo real: $scrapedTimestamp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row {
                if (isScraping) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp)
                }
                IconButton(onClick = { viewModel.fetchRates() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                }
                IconButton(onClick = { showPageSizeDialog = true }) {
                    Icon(Icons.Default.Print, contentDescription = "Exportar Evolución", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Evolución del precio (CUP) de las divisas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ranges.forEach { range ->
                        val isSelected = selectedRange == range
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedRange = range },
                            label = { Text(range, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                RateHistoryChart(
                    history = rateHistory,
                    range = selectedRange,
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    lastUpdateDate = if (scrapedTimestamp.isNotBlank()) scrapedTimestamp else null
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val codes = listOf("USD", "EUR", "MLC", "CAD", "MEX", "ZELLE", "CLA")
                    for (i in codes.indices step 4) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            codes.subList(i, (i + 4).coerceAtMost(codes.size)).forEach { code ->
                                LegendItem(getCurrencyColor(code), code)
                            }
                            if (i + 4 > codes.size) {
                                repeat(4 - (codes.size - i)) { Spacer(Modifier.width(40.dp)) }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val periodMax = remember(rateHistory, selectedRange) {
                    val currentTime = System.currentTimeMillis()
                    val filterTime = when (selectedRange) {
                        "1S" -> currentTime - TimeUnit.DAYS.toMillis(7)
                        "1M" -> currentTime - TimeUnit.DAYS.toMillis(30)
                        "3M" -> currentTime - TimeUnit.DAYS.toMillis(90)
                        "6M" -> currentTime - TimeUnit.DAYS.toMillis(180)
                        "1A" -> currentTime - TimeUnit.DAYS.toMillis(365)
                        "2A" -> currentTime - TimeUnit.DAYS.toMillis(730)
                        "5A" -> currentTime - TimeUnit.DAYS.toMillis(1825)
                        else -> 0L
                    }
                    val filtered = rateHistory.filter { it.timestamp >= filterTime }
                    if (filtered.isEmpty()) emptyMap()
                    else listOf("USD", "EUR", "MLC", "CAD", "MEX", "ZELLE", "CLA").associateWith { code ->
                        filtered.filter { it.currencyCode == code }.maxByOrNull { it.rate }
                    }
                }

                if (periodMax.isNotEmpty()) {
                    Text("Picos máximos (2 decimales):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val items = periodMax.toList()
                        for (i in items.indices step 4) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items.subList(i, (i + 4).coerceAtMost(items.size)).forEach { (code, hist) ->
                                    hist?.let {
                                        Surface(
                                            color = getCurrencyColor(code).copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(code, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                Text(String.format(Locale.US, "%.2f", it.rate), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                    } ?: Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Registro de Cambios", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Moneda", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Tasa (CUP)", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Fecha/Hora", modifier = Modifier.weight(2.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                HorizontalDivider()
                
                rateHistory.reversed().take(20).forEach { hist ->
                    val dateStr = remember(hist.timestamp) { 
                        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(Date(hist.timestamp))
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(hist.currencyCode, modifier = Modifier.weight(1f), fontSize = 12.sp)
                        Text(String.format(Locale.US, "%.2f", hist.rate), modifier = Modifier.weight(1.5f), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text(dateStr, modifier = Modifier.weight(2.5f), fontSize = 11.sp, color = Color.Gray)
                    }
                    HorizontalDivider(modifier = Modifier.alpha(0.3f))
                }
                
                if (rateHistory.isEmpty()) {
                    Text("No hay registros aún", modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center, color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
        
        Text("Nota: Los valores se mantienen constantes entre cambios. Si pasa un día sin cambios, el gráfico reflejará la estabilidad del último valor.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun StatisticsScreen(viewModel: KeygenViewModel = viewModel()) {
    val licenses by viewModel.licenses.collectAsState()
    val rateHistory by viewModel.rateHistory.collectAsState()
    val context = LocalContext.current
    
    val totals = remember(licenses) {
        val map = mutableMapOf("USD" to 0.0, "EUR" to 0.0, "MLC" to 0.0, "CAD" to 0.0, "MEX" to 0.0, "ZELLE" to 0.0, "CLA" to 0.0)
        licenses.filter { it.isPaid }.forEach { lic ->
            val parts = lic.price.split(" ")
            if (parts.size >= 2) {
                val amount = parts[0].toDoubleOrNull() ?: 0.0
                val currency = parts[1].uppercase()
                if (map.containsKey(currency)) {
                    map[currency] = map[currency]!! + amount
                } else {
                    map[currency] = amount
                }
            }
        }
        map
    }

    var selectedPageSize by remember { mutableStateOf(PdfGenerator.PageSize.A4) }
    var showPageSizeDialog by remember { mutableStateOf(false) }

    if (showPageSizeDialog) {
        AlertDialog(
            onDismissRequest = { showPageSizeDialog = false },
            title = { Text("Tamaño de hoja") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seleccione el formato de papel para el reporte integral:")
                    PdfGenerator.PageSize.entries.forEach { ps ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPageSize = ps }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ps.label)
                            if (selectedPageSize == ps) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPageSizeDialog = false
                    PdfGenerator.generateIncomePdf(context, totals, licenses.count { it.isPaid }, rateHistory, selectedPageSize)
                }) { Text("Generar") }
            },
            dismissButton = {
                TextButton(onClick = { showPageSizeDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DASHBOARD DE INGRESOS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showPageSizeDialog = true }) {
                Icon(Icons.Default.Print, contentDescription = "Exportar Reporte", tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        Card(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Column {
                    Text("Ingresos por Moneda (Monto)", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        totals.filter { it.value > 0 }.forEach { (curr, amount) ->
                            val max = totals.values.maxOrNull() ?: 1.0
                            val heightFactor = if (max > 0) (amount / max).toFloat() else 0f
                            val color = getCurrencyColor(curr)
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                                Box(modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(heightFactor.coerceAtLeast(0.05f))
                                    .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                )
                                Text(curr, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Total Cobrado por Moneda", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                
                totals.forEach { (curr, total) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when(curr) {
                                    "USD" -> Icons.Default.AttachMoney
                                    "EUR" -> Icons.Default.Euro
                                    "MEX" -> Icons.Default.Payments
                                    else -> Icons.Default.AccountBalanceWallet
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(curr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                        Text(
                            String.format(Locale.US, "%.2f", total),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    if (curr != totals.keys.last()) {
                        HorizontalDivider(modifier = Modifier.alpha(0.3f))
                    }
                }
            }
        }

        val countPaid = licenses.count { it.isPaid }
        val countPending = licenses.count { !it.isPaid }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                    Text("Pagadas", fontSize = 12.sp)
                    Text("$countPaid", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            }
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(alpha = 0.1f))) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Pending, null, tint = Color(0xFFFF9800), modifier = Modifier.size(24.dp))
                    Text("Pendientes", fontSize = 12.sp)
                    Text("$countPending", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Text("Nota: Los cambios en las tasas se registran automáticamente para el historial.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
    }
}

fun shareLicense(context: Context, text: String, packageName: String?, label: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        if (packageName != null) setPackage(packageName)
    }
    try { context.startActivity(Intent.createChooser(intent, "Enviar vía $label")) }
    catch (e: Exception) { if (packageName != null) shareLicense(context, text, null, "Email/Mensaje") }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF2196F3),
            secondary = Color(0xFF4CAF50),
            tertiary = Color(0xFFFF9800)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainScreen(snackbarHostState = remember { SnackbarHostState() })
        }
    }
}

private fun getCurrencyColor(code: String): Color {
    return when (code) {
        "USD" -> Color(0xFF4CAF50)
        "EUR" -> Color(0xFF2196F3)
        "MLC" -> Color(0xFF9C27B0)
        "CAD" -> Color(0xFFFF9800)
        "MEX" -> Color(0xFFE91E63)
        "ZELLE" -> Color(0xFF00BCD4)
        "CLA" -> Color(0xFF3F51B5)
        else -> Color.Gray
    }
}
