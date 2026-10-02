package com.drywall.cleaner

import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.drywall.common.security.TrialResetter
import com.drywall.common.security.BiometricHelper
import com.drywall.cleaner.ui.ReadmeScreen
import com.drywall.cleaner.utils.LicensePdfGenerator
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Security Gate on Startup
        BiometricHelper.authenticateWithBiometrics(
            activity = this,
            title = "Seguridad del Administrador",
            subtitle = "Autentíquese para acceder a las herramientas de licencia"
        ) { authenticated ->
            if (authenticated) {
                setupContent()
            } else {
                Toast.makeText(this, "Acceso denegado", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupContent() {
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                    var backPressCount by remember { mutableStateOf(0) }

                    // Double back logic
                    LaunchedEffect(backPressCount) {
                        if (backPressCount > 0) {
                            if (backPressCount >= 2) {
                                // Force close
                                Process.killProcess(Process.myPid())
                            }
                            delay(2000)
                            backPressCount = 0
                        }
                    }

                    DisposableEffect(backPressedDispatcher) {
                        val callback = object : androidx.activity.OnBackPressedCallback(true) {
                            override fun handleOnBackPressed() {
                                backPressCount++
                                if (backPressCount < 2) {
                                    Toast.makeText(this@MainActivity, "Presione atrás de nuevo para cerrar y forzar cierre", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        backPressedDispatcher?.addCallback(callback)
                        onDispose { callback.remove() }
                    }

                    CleanerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerScreen() {
    var currentScreen by remember { mutableStateOf("welcome") }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var actionLabel by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val title = when (currentScreen) {
        "help" -> "Ayuda"
        else -> "Drywall Cleaner"
    }

    Scaffold(
        topBar = {
            if (currentScreen != "welcome") {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { currentScreen = "actions" }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            if (currentScreen != "welcome") {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Inicio") },
                        selected = currentScreen == "actions",
                        onClick = { currentScreen = "actions" }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        label = { Text("Ayuda") },
                        selected = currentScreen == "help",
                        onClick = { currentScreen = "help" }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            "welcome" -> {
                WelcomeContent(onContinue = { currentScreen = "actions" })
            }
            "actions" -> {
                ActionsContent(
                    modifier = Modifier.padding(innerPadding),
                    onResetTrial = {
                        actionLabel = "Reiniciar Prueba Gratis"
                        pendingAction = {
                            val ok = TrialResetter.resetAndActivateTrial(context)
                            LicensePdfGenerator.generateActionReport(context, "Reiniciar Prueba", ok)
                            Toast.makeText(context,
                                if (ok) "Prueba reiniciada: 7 dias nuevos activados. Reporte generado."
                                else "Error al reiniciar la prueba.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        showConfirmDialog = true
                    },
                    onRemoveLicense = {
                        actionLabel = "Quitar Licencia y Activar Prueba"
                        pendingAction = {
                            val ok = TrialResetter.removeLicenseAndActivateTrial(context)
                            LicensePdfGenerator.generateActionReport(context, "Quitar Licencia", ok)
                            Toast.makeText(context,
                                if (ok) "Licencia eliminada. Prueba activada. Reporte generado."
                                else "Error al quitar la licencia.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        showConfirmDialog = true
                    },
                    onDeepReset = {
                        actionLabel = "Reset Completo de Prueba"
                        pendingAction = {
                            val ok = TrialResetter.deepReset(context)
                            LicensePdfGenerator.generateActionReport(context, "Reset Completo", ok)
                            Toast.makeText(context,
                                if (ok) "Reseteo completo exitoso. Reporte generado."
                                else "Error al resetear.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        showConfirmDialog = true
                    }
                )
            }
            "help" -> {
                ReadmeScreen(
                    modifier = Modifier.padding(innerPadding),
                    onBack = { currentScreen = "actions" }
                )
            }
        }
    }

    if (showConfirmDialog && pendingAction != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false; pendingAction = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(actionLabel) },
            text = { Text("Esta accion modificara la licencia de la App principal. Asegurese de tener copia de datos importantes.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        pendingAction?.invoke()
                        pendingAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false; pendingAction = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun WelcomeContent(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AdminPanelSettings,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Drywall Calculator",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Administrador de Prueba y Licencia",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Herramienta para gestionar el periodo de prueba y la licencia de la aplicacion principal.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                FeatureRow(icon = Icons.Default.RestartAlt, text = "Reiniciar periodo de prueba (7 dias nuevos)")
                Spacer(Modifier.height(8.dp))
                FeatureRow(icon = Icons.Default.RemoveCircleOutline, text = "Quitar licencia profesional y volver a prueba")
                Spacer(Modifier.height(8.dp))
                FeatureRow(icon = Icons.Default.DeleteSweep, text = "Reset completo de todos los bloqueos")
            }
        }

        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Continuar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
fun ActionsContent(
    modifier: Modifier = Modifier,
    onResetTrial: () -> Unit,
    onRemoveLicense: () -> Unit,
    onDeepReset: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Tune,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Gestion de Licencia",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Seleccione la accion que desea realizar sobre la App principal.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        // Action 1: Reiniciar Prueba
        ActionCard(
            icon = Icons.Default.RestartAlt,
            title = "Reiniciar Prueba Gratis",
            description = "Elimina la prueba actual y activa 7 dias nuevos. La App se reiniciara en modo prueba.",
            accentColor = MaterialTheme.colorScheme.tertiary,
            onClick = onResetTrial
        )

        Spacer(Modifier.height(12.dp))

        // Action 2: Quitar Licencia
        ActionCard(
            icon = Icons.Default.RemoveCircleOutline,
            title = "Quitar Licencia Profesional",
            description = "Elimina la licencia profesional y activa una prueba de 7 dias. Podra ingresar una nueva licencia despues.",
            accentColor = MaterialTheme.colorScheme.error,
            onClick = onRemoveLicense
        )

        Spacer(Modifier.height(12.dp))

        // Action 3: Deep Reset
        ActionCard(
            icon = Icons.Default.DeleteForever,
            title = "Reset Completo",
            description = "Elimina todos los datos de prueba y licencia. La App arranca como si fuera nueva instalacion.",
            accentColor = Color(0xFFD32F2F),
            onClick = onDeepReset
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = accentColor
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FeatureRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
