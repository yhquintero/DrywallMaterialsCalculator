package com.drywall.calculator

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.drywall.calculator.data.local.entity.AppConfig
import com.drywall.calculator.presentation.theme.DrywallTheme
import com.drywall.calculator.presentation.ui.licensing.ActivationScreen
import com.drywall.calculator.utils.security.LicensingManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.TimeUnit

data class MenuItem(val route: String, val label: String, val icon: ImageVector, val description: String)

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        if (savedInstanceState == null) {
            // Ejecutar limpieza en background
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                cacheDir?.listFiles()?.forEach { if (it.name.endsWith(".txt") || it.name.endsWith(".db")) it.delete() }
            }
        }

        setContent {
            val mainViewModel: MainActivityViewModel = hiltViewModel()
            val config by mainViewModel.config.collectAsState()
            val licenseValid by mainViewModel.licenseValid.collectAsState()
            val licenseViolation by mainViewModel.licenseViolation.collectAsState()
            val isTrialAvailable by mainViewModel.isTrialAvailable.collectAsState()
            val isTrialAlreadyUsed by mainViewModel.isTrialAlreadyUsed.collectAsState()
            val isTrialActive by mainViewModel.isTrialActive.collectAsState()
            val trialRemainingMs by mainViewModel.trialRemainingMs.collectAsState()
            
            val isPermanentlyLocked by mainViewModel.isPermanentlyLocked.collectAsState()
            val lockReason by mainViewModel.lockReason.collectAsState()
            
            // Iniciar verificación de licencia en background
            LaunchedEffect(Unit) {
                mainViewModel.checkLicense(this@MainActivity)
            }

            // Refresh trial state periodically
            LaunchedEffect(isTrialActive) {
                if (isTrialActive) {
                    while (true) {
                        kotlinx.coroutines.delay(60_000L) // Every minute
                        mainViewModel.refreshTrialState(this@MainActivity)
                    }
                }
            }

            if (licenseViolation) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("SEGURIDAD: Uso Indebido", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    text = { 
                        Text("Se ha detectado una manipulación en el reloj del dispositivo o un intento de uso fuera del período permitido.\n\nPor seguridad, el acceso ha sido bloqueado hasta que se corrija la fecha y hora.") 
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                try {
                                    startActivity(android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS))
                                } catch (_: Exception) {
                                    Toast.makeText(this@MainActivity, "No se pudo abrir los ajustes", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) { 
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("AJUSTAR TIEMPO") 
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { finishAndRemoveTask() }) { 
                            Text("CERRAR APP") 
                        }
                    }
                )
            }

            // Root/Tamper detection dialog
            var showTamperDialog by rememberSaveable { mutableStateOf(false) }
            var tamperMessage by rememberSaveable { mutableStateOf("") }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1500) // Wait for init
                try {
                    val protection = com.drywall.common.security.TrialProtectionManager.getProtectionStatus(this@MainActivity)
                    when {
                        protection.isRooted -> {
                            tamperMessage = "Se ha detectado que este dispositivo tiene acceso de root.\n\n" +
                                "Por razones de seguridad, la aplicación no puede funcionar en dispositivos con root.\n\n" +
                                "La licencia de prueba no está disponible en dispositivos comprometidos."
                            showTamperDialog = true
                        }
                        protection.deviceCompromised -> {
                            tamperMessage = "Se ha detectado que la integridad de la aplicación ha sido comprometida.\n\n" +
                                "Esto puede deberse a:\n" +
                                "• La firma de la aplicación ha sido modificada\n" +
                                "• Se ha instalado una versión no autorizada\n" +
                                "• Se detectó manipulación del código\n\n" +
                                "Por favor, descargue la aplicación oficial desde fuentes autorizadas."
                            showTamperDialog = true
                        }
                        protection.emulatorDetected -> {
                            tamperMessage = "Se ha detectado que la aplicación está ejecutándose en un emulador.\n\n" +
                                "La licencia de prueba no está disponible en emuladores por razones de seguridad."
                            showTamperDialog = true
                        }
                    }
                } catch (_: Exception) { }
            }
            if (showTamperDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)) },
                    title = { Text("SEGURIDAD: Dispositivo Comprometido", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                    text = { Text(tamperMessage) },
                    confirmButton = {
                        Button(
                            onClick = { finishAndRemoveTask() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("CERRAR APLICACIÓN", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            var isActivated by rememberSaveable { mutableStateOf(false) }
            var showTrialDialog by rememberSaveable { mutableStateOf(false) }
            
            // Actualizar isActivated cuando se complete la verificación
            LaunchedEffect(licenseValid) {
                licenseValid?.let { isActivated = it }
            }

            // Show trial dialog when trial is available and no license
            LaunchedEffect(isTrialAvailable, isActivated) {
                if (isTrialAvailable == true && isActivated == false && licenseValid != null) {
                    kotlinx.coroutines.delay(500)
                    showTrialDialog = true
                }
            }

            // Trial activation dialog
            if (showTrialDialog) {
                AlertDialog(
                    onDismissRequest = { showTrialDialog = false },
                    icon = { Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) },
                    title = { Text("Modo Prueba Gratis", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Activa una licencia de prueba de 7 días para explorar TODOS los módulos sin restricción.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Incluye:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    Text("• Acceso total a todos los módulos", style = MaterialTheme.typography.bodySmall)
                                    Text("• Sin límites de uso", style = MaterialTheme.typography.bodySmall)
                                    Text("• Duración: 7 días exactos", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text(
                                "Esta es la ÚNICA prueba disponible. Una vez activada, no se podrá volver a usar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Requiere conexión a internet para sincronizar el tiempo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showTrialDialog = false
                                mainViewModel.activateTrial(this@MainActivity)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Activar Prueba de 7 Días", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTrialDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // Warning dialog when trial is already used on this device
            var showTrialAlreadyUsedDialog by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(isTrialAlreadyUsed, licenseValid) {
                if (isTrialAlreadyUsed == true && licenseValid != null && !showTrialDialog) {
                    kotlinx.coroutines.delay(300)
                    showTrialAlreadyUsedDialog = true
                }
            }
            if (showTrialAlreadyUsedDialog) {
                AlertDialog(
                    onDismissRequest = { showTrialAlreadyUsedDialog = false },
                    icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)) },
                    title = { Text("Período de Prueba Agotado", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Este dispositivo ya utilizó el período de prueba gratuito de 7 días.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "El período de prueba está vinculado de forma permanente a este dispositivo y no puede reinstalarse, ni siquiera desinstalando la aplicación.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Para seguir usando la aplicación, necesita:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    Text("• Una licencia Profesional (1 día a 2 años)", style = MaterialTheme.typography.bodySmall)
                                    Text("• Contacte al proveedor para adquirir su licencia", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text(
                                "Si ya posee una licencia, ingrésela en Configuración > Licencia.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showTrialAlreadyUsedDialog = false },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Entendido")
                        }
                    }
                )
            }

            var showSyncScreen by rememberSaveable { mutableStateOf(false) }
            
            // Determinar si mostrar sync screen después de verificar licencia
            LaunchedEffect(isActivated, licenseValid) {
                if (licenseValid != null) {
                    showSyncScreen = !LicensingManager.isRecentSyncAvailable(this@MainActivity) && !isActivated
                }
            }
            
            var isAuthenticated by rememberSaveable { mutableStateOf(value = false) }
            var authFailed by rememberSaveable { mutableStateOf(value = false) }
            var showSplash by rememberSaveable { mutableStateOf(value = true) }

            // Splash visible mientras se verifica licencia y pasa el tiempo mínimo
            LaunchedEffect(licenseValid) {
                if (licenseValid != null) {
                    delay(1.seconds) // Asegurar tiempo mínimo de logo
                    showSplash = false
                }
            }

            // Sync en background si ya está activado y necesita sync
            LaunchedEffect(isActivated) {
                if (isActivated && !LicensingManager.isRecentSyncAvailable(this@MainActivity)) {
                    com.drywall.common.security.NetworkTimeProvider.syncWithInternet()
                    LicensingManager.updateLastSyncTime(this@MainActivity)
                }
            }

            @Suppress("DEPRECATION")
            val securityPrefs = remember { 
                com.drywall.common.security.SecureStorageUtils.getEncryptedPrefs(this@MainActivity, "secure_security_prefs")
            }
            var showSecurityPermissionDialog by rememberSaveable { 
                mutableStateOf(!securityPrefs.getBoolean("security_permission_accepted", false)) 
            }

            LaunchedEffect(showSecurityPermissionDialog, isAuthenticated, authFailed) {
                if (!showSecurityPermissionDialog && !isAuthenticated && !authFailed) {
                    delay(800.milliseconds) // Un poco más de delay para asegurar que el UI está listo
                    authenticateWithBiometrics { success ->
                        if (success) {
                            isAuthenticated = true
                        } else {
                            authFailed = true
                        }
                    }
                }
            }

            if (showSecurityPermissionDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Seguridad Requerida") },
                    text = { Text("Para acceder a la aplicación, se requiere autenticación mediante el bloqueo de pantalla de su dispositivo (Biometría o Credenciales).") },
                    confirmButton = {
                        Button(onClick = {
                            securityPrefs.edit { putBoolean("security_permission_accepted", true) }
                            showSecurityPermissionDialog = false
                            authenticateWithBiometrics { success ->
                                if (success) {
                                    isAuthenticated = true
                                } else {
                                    authFailed = true
                                }
                            }
                        }) { Text("Continuar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { finish() }) { Text("Salir") }
                    }
                )
            }

            val invalidateLicense = {
                isActivated = false
            }

            DrywallTheme(
                darkTheme = config?.isDarkMode ?: androidx.compose.foundation.isSystemInDarkTheme(),
                decimalPrecision = config?.decimalPrecision ?: 4
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (isPermanentlyLocked) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "Aplicación Bloqueada",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                lockReason ?: "Se ha detectado una manipulación no autorizada.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "La aplicación no puede utilizarse. Si cree que esto es un error, contacte al soporte técnico.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(32.dp))
                            Button(
                                onClick = { finishAndRemoveTask() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Icon(Icons.Default.Close, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Cerrar Aplicación", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (showSplash) {
                        AppSplashScreen(version = "v${BuildConfig.VERSION_NAME}")
                    } else if (authFailed) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(16.dp))
                            Text("Acceso Bloqueado", style = MaterialTheme.typography.headlineSmall)
                            Text("Se requiere autenticación para entrar.", textAlign = TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { authFailed = false }) { Text("Reintentar") }
                            TextButton(onClick = { finish() }) { Text("Salir") }
                        }
                    } else if (!isAuthenticated) {
                        // Wait for authentication
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (showSyncScreen) {
                        NetworkSyncScreen(
                            onSyncComplete = {
                                showSyncScreen = false
                                isActivated = LicensingManager.isLicenseValid(this@MainActivity)
                            }
                        )
                    } else if (isActivated) {
                        DrywallApp(mainViewModel, onLicenseInvalidated = invalidateLicense)
                    } else {
                        ActivationScreen(
                            onActivated = { isActivated = true },
                            currentDarkMode = config?.isDarkMode,
                            onToggleDarkMode = { mainViewModel.toggleDarkMode(it) }
                        )
                    }
                }
            }
        }
    }

    /**
     * Realiza la autenticación mediante biometría o credenciales del dispositivo.
     *
     * @param onResult Callback que recibe un booleano indicando si la autenticación fue exitosa.
     */
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
                    .setTitle("Autenticación de Seguridad")
                    .setSubtitle("Ingrese sus credenciales para continuar")
                    .setAllowedAuthenticators(authenticators)
                    .build()

                authPrompt.authenticate(promptInfo)
            }
            else -> {
                Toast.makeText(this, "Debe configurar un bloqueo de pantalla seguro (PIN, Patrón, Contraseña o Biometría)", Toast.LENGTH_LONG).show()
                onResult(false)
            }
        }
    }
}

/**
 * Pantalla de carga inicial (Splash Screen) que muestra el logo y la versión.
 *
 * @param version String que representa la versión actual de la aplicación.
 */
@Composable
fun AppSplashScreen(version: String) {
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
                contentDescription = "Logo de Calculadora Drywall",
                modifier = Modifier.size(180.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "CALCULADORA DRYWALL\nYHQUINTERO",
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

/**
 * Pantalla para la sincronización de la hora con servidores NTP de Internet.
 * Es necesaria para validar las licencias temporales y el período de prueba.
 *
 * @param onSyncComplete Callback que se ejecuta cuando la sincronización finaliza con éxito.
 */
@Composable
fun NetworkSyncScreen(onSyncComplete: () -> Unit) {
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
                LicensingManager.updateLastSyncTime(context)
                val deviceTime = System.currentTimeMillis()
                timeDiscrepancy = networkTime - deviceTime
                
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                syncedTimeText = sdf.format(java.util.Date(networkTime))
                usedServerText = com.drywall.common.security.NetworkTimeProvider.getLastUsedServer()
                
                // Si la hora es correcta, continuar automáticamente después de un breve delay para que el usuario vea el éxito
                if (kotlin.math.abs(timeDiscrepancy) < 60000) {
                    delay(1.seconds)
                    onSyncComplete()
                }
            } else {
                syncAttempts++
                if (syncAttempts < 3) {
                    syncError = "Error de conexión. Reintentando en 3 segundos... (Intento $syncAttempts de 3)"
                    delay(3.seconds)
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
            title = { Text("Sin Conexión") },
            text = { Text("No se pudo sincronizar la hora. Esto es necesario para activar nuevas licencias. ¿Desea continuar de todas formas (bajo su riesgo)?") },
            confirmButton = {
                Button(onClick = { 
                    showAppExitNotice = false
                    onSyncComplete() 
                }) { Text("Omitir y Continuar") }
            },
            dismissButton = {
                TextButton(onClick = { (context as? android.app.Activity)?.finish() }) { Text("Salir") }
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
            "Para el uso de licencias, el reloj de su dispositivo debe coincidir con la hora real de Internet.",
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
            val isTimeAccurate = kotlin.math.abs(timeDiscrepancy) < 60000 // Menos de 1 minuto de diferencia

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
                            "Su dispositivo tiene una diferencia de ${kotlin.math.abs(timeDiscrepancy / 1000 / 60)} minutos. " +
                            "Debe corregirlo para continuar.",
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
                    Text("Continuar a la Aplicación")
                }
            } else {
                Button(
                    onClick = {
                        try {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS))
                        } catch (_: Exception) {
                            Toast.makeText(context, "No se pudo abrir los ajustes", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Corregir Hora en Ajustes")
                }
                TextButton(onClick = { syncedTimeText = "" }) {
                    Text("Reintentar Sincronización")
                }
            }
        } else {
            if (syncError != null) {
                Text(syncError!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
            }
            Button(
                onClick = {
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
                        } else {
                            syncError = "Error de conexión. Verifique su acceso a Internet y reintente."
                        }
                        isSyncing = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.CloudSync, null)
                Spacer(Modifier.width(8.dp))
                Text("Sincronizar Hora Internacional")
            }
        }
    }
}

/**
 * Contenedor principal de la aplicación que observa el estado del ViewModel y lo pasa al contenido.
 *
 * @param viewModel Instancia de MainActivityViewModel.
 * @param onLicenseInvalidated Callback a ejecutar si se detecta que la licencia ya no es válida.
 */
@Composable
fun DrywallApp(viewModel: MainActivityViewModel, onLicenseInvalidated: () -> Unit) {
    val config by viewModel.config.collectAsState()
    val showShortcutDialog by viewModel.isShortcutDialogOpen.collectAsState()
    val isTrialActive by viewModel.isTrialActive.collectAsState()
    val trialRemainingMs by viewModel.trialRemainingMs.collectAsState()

    DrywallAppContent(
        config = config,
        showShortcutDialog = showShortcutDialog,
        onToggleDarkMode = { viewModel.toggleDarkMode(it) },
        onUpdateShortcuts = { viewModel.updateShortcuts(it) },
        onSetShortcutDialogOpen = { viewModel.setShortcutDialogOpen(it) },
        onLicenseInvalidated = onLicenseInvalidated,
        isTrialActive = isTrialActive,
        trialRemainingMs = trialRemainingMs
    )
}

/**
 * Icono de menú animado que se transforma de hamburguesa a flecha/cruz según el progreso del drawer.
 *
 * @param progressProvider Proveedor de una función que devuelve el progreso de la animación (0.0 a 1.0).
 */
@Composable
fun AnimatedMenuIcon(progressProvider: () -> Float) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val color = Color.White
        val thickness = 2.dp
        val width = 18.dp
        
        // Línea Superior (Creciente 45 grados)
        Box(
            modifier = Modifier
                .size(width, thickness)
                .graphicsLayer {
                    val progress = progressProvider()
                    val safeProgress = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationZ = 45f * safeProgress
                    translationY = -6.dp.toPx() * (1 - safeProgress)
                    translationX = 2.dp.toPx() * safeProgress
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
                    val progress = progressProvider()
                    val safeProgress = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
                    translationX = 2.dp.toPx() * safeProgress
                    alpha = 1f - safeProgress
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
                    val progress = progressProvider()
                    val safeProgress = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationZ = -45f * safeProgress
                    translationY = 6.dp.toPx() * (1 - safeProgress)
                    translationX = 2.dp.toPx() * safeProgress
                    scaleX = 1f - (0.2f * safeProgress)
                }
                .background(color, CircleShape)
                .align(Alignment.CenterStart)
                .padding(start = 3.dp)
        )
    }
}

/**
 * Contenido principal de la aplicación incluyendo navegación, barra superior y barra inferior de accesos directos.
 *
 * @param config Configuración actual de la aplicación.
 * @param showShortcutDialog Estado para mostrar el diálogo de gestión de accesos directos.
 * @param onToggleDarkMode Función para cambiar el tema visual.
 * @param onUpdateShortcuts Función para guardar la nueva lista de accesos directos.
 * @param onSetShortcutDialogOpen Función para controlar la visibilidad del diálogo de accesos directos.
 * @param onLicenseInvalidated Callback para manejar licencias expiradas/inválidas.
 * @param isTrialActive Indica si el período de prueba está activo.
 * @param trialRemainingMs Tiempo restante de prueba en milisegundos.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrywallAppContent(
    config: AppConfig?,
    showShortcutDialog: Boolean,
    onToggleDarkMode: (Boolean?) -> Unit,
    onUpdateShortcuts: (List<String>) -> Unit,
    onSetShortcutDialogOpen: (Boolean) -> Unit,
    onLicenseInvalidated: () -> Unit,
    isTrialActive: Boolean = false,
    trialRemainingMs: Long = 0L
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var backPressedTime by rememberSaveable { mutableLongStateOf(0L) }

    val density = LocalDensity.current
    val drawerWidthPx = with(density) { 280.dp.toPx() }

    val drawerProgress by remember(drawerState) {
        derivedStateOf {
            val offsetValue = try { drawerState.currentOffset } catch (_: Exception) {
                if (drawerState.isOpen) 0f else -drawerWidthPx
            }
            if (drawerWidthPx > 0.001f && !offsetValue.isNaN() && !offsetValue.isInfinite()) {
                ((offsetValue + drawerWidthPx) / drawerWidthPx).coerceIn(0f, 1f)
            } else {
                if (drawerState.isOpen) 1f else 0f
            }
        }
    }

    BackHandler {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            if ((backPressedTime + 2000) > System.currentTimeMillis()) {
                val activity = context as? android.app.Activity
                activity?.finishAndRemoveTask() // C4: Reemplaza exitProcess(0)
            } else {
                Toast.makeText(context, "Haga clic de nuevo para salir", Toast.LENGTH_SHORT).show()
                backPressedTime = System.currentTimeMillis()
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val currentTitle = when (currentRoute) {
        "dashboard" -> stringResource(R.string.dashboard)
        "company" -> stringResource(R.string.company)
        "bank" -> stringResource(R.string.bank)
        "tax" -> "Gestión de Impuestos"
        "clients" -> stringResource(R.string.clients)
        "projects" -> stringResource(R.string.projects)
        "labor" -> "Precios de Mano de Obra"
        "medidas" -> "Catálogo Medidas Mat."
        "materials" -> "Registro de Materiales"
        "calculator" -> "Calculadora de Áreas"
        "inventory" -> "Almacén de Inventario"
        "converter" -> "Convertidor de Unidades"
        "currency" -> "Conversor de Divisas"
        "orders" -> "Órdenes de Compra"
        "stats" -> "Estadísticas de Negocio"
        "providers" -> "Lista de Proveedores"
        "diary" -> "Bitácora de Obra"
        "gallery" -> "Galería de Proyectos"
        "id_card" -> "Carnet Digital"
        "sketchup" -> "Importar desde SketchUp"
        "pdf" -> "Exportación a PDF"
        "money_calculator" -> "Calculadora de Efectivo"
        "financial_reports" -> "Reporte Financiero"
        "income_statement" -> "Estado de Resultado"
        "readme" -> "Ayuda e Información"
        "settings" -> "Configuración del Sistema"
        "license_management" -> "Gestión de Licencia"
        "error_log" -> "Centro de Diagnóstico"
        else -> "Calculadora Drywall"
    }

    LaunchedEffect(Unit) {
        val license = LicensingManager.getLicense(context)
        if (license != null) {
            val remainingTime = license.expiryDate - LicensingManager.getCurrentTimeSafe(context)
            val remainingDays = TimeUnit.MILLISECONDS.toDays(remainingTime)
            if (remainingDays in 1..3) {
                snackbarHostState.showSnackbar(
                    message = "Su licencia profesional vencerá en $remainingDays días.",
                    actionLabel = "Renovar",
                    duration = SnackbarDuration.Long
                )
            } else if (remainingDays <= 0 && remainingTime > 0) {
                snackbarHostState.showSnackbar(
                    message = "Su licencia profesional vence hoy.",
                    actionLabel = "Renovar",
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    val menuItems = listOf(
        MenuItem("dashboard", "Inicio", Icons.Default.Home, "Vista general de tu negocio con alertas, estadísticas y evolución de divisas."),
        MenuItem("company", "Empresa", Icons.Default.Business, "Configura los datos de tu empresa, logo, firma y datos fiscales para documentos."),
        MenuItem("bank", "Cuentas", Icons.Default.AccountBalance, "Gestiona tus cuentas de cobro y pago, con soporte para tarjetas y transferencias."),
        MenuItem("tax", "Impuesto", Icons.Default.Percent, "Configura los impuestos aplicables a tus presupuestos y facturas."),
        MenuItem("clients", "Clientes", Icons.Default.People, "Directorio completo de clientes con escaneo de carnet e historial."),
        MenuItem("projects", "Obras", Icons.Default.Architecture, "Crea y gestiona presupuestos detallados para tus obras de drywall."),
        MenuItem("labor", "Costos", Icons.Default.Engineering, "Define los precios unitarios por metro cuadrado para diferentes servicios."),
        MenuItem("medidas", "Catálogo", Icons.Default.Book, "Catálogo completo de medidas estándar para planchas, perfiles y accesorios."),
        MenuItem("materials", "Materiales", Icons.Default.Inventory, "Catálogo base de materiales con cálculo automático de margen de ganancia."),
        MenuItem("calculator", "Calculadora", Icons.Default.Calculate, "Herramienta rápida para convertir áreas y calcular materiales necesarios."),
        MenuItem("inventory", "Almacén", Icons.Default.Warehouse, "Control de existencias, entradas y salidas de materiales de tu almacén."),
        MenuItem("converter", "Convertidor", Icons.Default.Straighten, "Convierte entre diferentes sistemas de medida usados en construcción."),
        MenuItem("currency", "Divisas", Icons.Default.CurrencyExchange, "Consulta tasas actuales y convierte montos entre CUP, USD, EUR y MLC."),
        MenuItem("orders", "Órdenes", Icons.Default.ShoppingCart, "Genera y exporta órdenes de compra para tus proveedores en formato PDF."),
        MenuItem("stats", "Estadísticas", Icons.Default.BarChart, "Gráficos de rendimiento, inversión en inventario y productividad por obra."),
        MenuItem("providers", "Proveedores", Icons.Default.ContactPage, "Gestión de proveedores con catálogos vinculados y calificación."),
        MenuItem("diary", "Bitácora", Icons.Default.EditNote, "Registro diario de actividades, fotos y progreso de tus proyectos activos."),
        MenuItem("gallery", "Galería", Icons.Default.PhotoLibrary, "Visualiza todas las fotos de tus obras organizadas por proyecto."),
        MenuItem("id_card", "Carnet", Icons.Default.Badge, "Genera tu identificación profesional digital con tus datos y firma."),
        MenuItem("sketchup", "SketchUp", Icons.Default.FileUpload, "Carga reportes de materiales exportados desde SketchUp (CSV)."),
        MenuItem("pdf", "PDF", Icons.Default.PictureAsPdf, "Centro de exportación masiva y visualización de documentos generados."),
        MenuItem("money_calculator", "Dinero", Icons.Default.Payments, "Calculadora de billetes para cuadre de caja y pagos en efectivo."),
        MenuItem("financial_reports", "Reporte F.", Icons.Default.Assessment, "Accede al centro de reportes financieros: Balance de Comprobación, Cuentas T, Estado de Resultados y más."),
        MenuItem("readme", "Ayuda", Icons.Default.Info, "Manual de usuario, consejos de uso y contacto de soporte técnico."),
        MenuItem("settings", "Configuración", Icons.Default.Settings, "Configura el tema oscuro, copias de seguridad y preferencias de la app.")
    )

    var selectedMenuItemForInfo by remember { mutableStateOf<MenuItem?>(null) }

    if (selectedMenuItemForInfo != null) {
        AlertDialog(
            onDismissRequest = { selectedMenuItemForInfo = null },
            icon = { Icon(selectedMenuItemForInfo!!.icon, contentDescription = selectedMenuItemForInfo!!.label, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(selectedMenuItemForInfo!!.label, fontWeight = FontWeight.Bold) },
            text = { Text(selectedMenuItemForInfo!!.description, textAlign = TextAlign.Center) },
            confirmButton = {
                TextButton(onClick = { selectedMenuItemForInfo = null }) { Text("Entendido") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentTitle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            val isDark = config?.isDarkMode ?: false
                            onToggleDarkMode(!isDark)
                        }) {
                            val isDark = config?.isDarkMode ?: false
                            Icon(
                                imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDark) "Modo Claro" else "Modo Oscuro"
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            if (drawerState.isOpen) drawerState.close() else drawerState.open()
                        }
                    }) {
                        AnimatedMenuIcon(progressProvider = { drawerProgress })
                    }
                }
            )
        },
        bottomBar = {
            val selectedShortcuts = config?.selectedShortcuts ?: menuItems.map { it.route }
            if (selectedShortcuts.isNotEmpty()) {
                com.drywall.calculator.presentation.ui.components.FloatingActionScrollPicker(
                    items = menuItems.asSequence()
                        .filter { it.route != "dashboard" && it.route in selectedShortcuts }
                        .map { Triple(it.route, it.label, it.icon) }
                        .toList(),
                    selectedRoute = currentRoute,
                    onItemClick = { route ->
                        if (route == "add_shortcut") {
                            onSetShortcutDialogOpen(true)
                        } else {
                            navController.navigate(route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                            }
                        }
                    }
                )

                if (showShortcutDialog) {
                    ShortcutManagementDialog(
                        title = "Organizar Barra Inferior",
                        allMenuItems = menuItems.filter { it.route != "dashboard" },
                        currentShortcuts = selectedShortcuts.filter { it != "dashboard" },
                        onSave = { newList: List<String> ->
                            onUpdateShortcuts(newList)
                            onSetShortcutDialogOpen(false)
                        },
                        onDismiss = { onSetShortcutDialogOpen(false) }
                    )
                }
            }
        }
    ) { innerPadding ->
        ModalNavigationDrawer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerTonalElevation = 0.dp // Removed for cleaner look inside Scaffold
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        menuItems.forEach { item ->
                            NavigationDrawerItem(
                                icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(22.dp)) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text(item.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = { selectedMenuItemForInfo = item },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Info, contentDescription = "Información", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                        }
                                    }
                                },
                                selected = currentRoute == item.route,
                                onClick = {
                                    navController.navigate(item.route) {
                                        // Pop up to the start destination of the graph to
                                        // avoid building up a large stack of destinations
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        // Avoid multiple copies of the same destination when
                                        // reselecting the same item
                                        launchSingleTop = true
                                        // Restore state when reselecting a previously selected item
                                        restoreState = true
                                    }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .height(48.dp),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Text(
                            "YHQuintero Soluciones v${BuildConfig.VERSION_NAME}",
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, top = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        com.drywall.calculator.presentation.navigation.AppNavigation(
                            navController = navController,
                            modifier = Modifier.fillMaxSize(),
                            onLicenseInvalidated = onLicenseInvalidated,
                            onShowSnackbar = { message ->
                                scope.launch { snackbarHostState.showSnackbar(message) }
                            }
                        )
                    }
                    
                    // Trial countdown bar at the bottom
                    if (isTrialActive && trialRemainingMs > 0) {
                        val days = trialRemainingMs / (1000 * 60 * 60 * 24)
                        val hours = (trialRemainingMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                        val minutes = (trialRemainingMs % (1000 * 60 * 60)) / (1000 * 60)
                        val timeText = when {
                            days > 0 -> "${days}d ${hours}h ${minutes}min"
                            hours > 0 -> "${hours}h ${minutes}min"
                            else -> "${minutes}min"
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "PRUEBA GRATIS - Tiempo restante: $timeText",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Diálogo interactivo para que el usuario elija y ordene sus accesos directos de la barra inferior.
 *
 * @param title Título del diálogo.
 * @param allMenuItems Lista completa de todos los módulos disponibles.
 * @param currentShortcuts Lista de rutas de los accesos directos actualmente activos.
 * @param onSave Callback para persistir los cambios realizados.
 * @param onDismiss Callback para cerrar el diálogo sin guardar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutManagementDialog(
    title: String,
    allMenuItems: List<MenuItem>,
    currentShortcuts: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIds by remember { mutableStateOf(currentShortcuts) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedIds = allMenuItems.take(2).map { it.route }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Quitar todos", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = {
                            selectedIds = allMenuItems.map { it.route }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Activar todos", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Selecciona y ordena tus iconos (Mín 2)", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(allMenuItems) { item ->
                        val id = item.route
                        val label = item.label
                        val icon = item.icon
                        val isSelected = selectedIds.contains(id)
                        Surface(
                            onClick = {
                                selectedIds = if (isSelected) {
                                    if (selectedIds.size > 2) selectedIds - id else selectedIds
                                } else {
                                    val fullList = allMenuItems.map { it.route }
                                    val tempSelection = selectedIds + id
                                    fullList.filter { it in tempSelection }
                                }
                            },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp)
                            ) {
                                Checkbox(checked = isSelected, onCheckedChange = null)
                                Icon(
                                    icon, null, 
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    label,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedIds) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DrywallAppPreview() {
    DrywallTheme {
        DrywallAppContent(
            config = AppConfig(),
            showShortcutDialog = false,
            onToggleDarkMode = {},
            onUpdateShortcuts = {},
            onSetShortcutDialogOpen = {},
            onLicenseInvalidated = {},
            isTrialActive = false,
            trialRemainingMs = 0L
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ShortcutManagementDialogPreview() {
    DrywallTheme {
        ShortcutManagementDialog(
            title = "Organizar Barra Inferior",
            allMenuItems = listOf(
                MenuItem("dashboard", "Inicio", Icons.Default.Home, "Desc"),
                MenuItem("company", "Empresa", Icons.Default.Business, "Desc")
            ),
            currentShortcuts = listOf("dashboard", "company"),
            onSave = {},
            onDismiss = {}
        )
    }
}
