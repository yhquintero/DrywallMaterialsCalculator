package com.drywall.common.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drywall.common.security.NetworkTimeProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun AnimatedMenuIcon(progress: Float) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val color = Color.White
        val thickness = 2.dp
        val width = 18.dp
        
        Box(
            modifier = Modifier
                .size(width, thickness)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationZ = 45f * progress
                    translationY = -6.dp.toPx() * (1 - progress)
                    translationX = 2.dp.toPx() * progress
                    scaleX = 1f - (0.2f * progress)
                }
                .background(color, CircleShape)
                .align(Alignment.CenterStart)
                .padding(start = 3.dp)
        )
        Box(
            modifier = Modifier
                .size(width, thickness)
                .graphicsLayer { translationX = 2.dp.toPx() * progress }
                .background(color, CircleShape)
                .align(Alignment.CenterStart)
                .padding(start = 3.dp)
        )
        Box(
            modifier = Modifier
                .size(width, thickness)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationZ = -45f * progress
                    translationY = 6.dp.toPx() * (1 - progress)
                    translationX = 2.dp.toPx() * progress
                    scaleX = 1f - (0.2f * progress)
                }
                .background(color, CircleShape)
                .align(Alignment.CenterStart)
                .padding(start = 3.dp)
        )
    }
}

@Composable
fun NetworkSyncScreen(
    onSyncComplete: () -> Unit,
    onUpdateLastSync: (Context) -> Unit = {}
) {
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
            val result = NetworkTimeProvider.syncWithInternet()
            if (result.isSuccess) {
                val networkTime = result.getOrNull() ?: 0L
                onUpdateLastSync(context)
                val deviceTime = System.currentTimeMillis()
                timeDiscrepancy = networkTime - deviceTime
                
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                syncedTimeText = sdf.format(java.util.Date(networkTime))
                usedServerText = NetworkTimeProvider.getLastUsedServer()
                
                if (abs(timeDiscrepancy) < 60000) {
                    delay(1000)
                    onSyncComplete()
                }
            } else {
                syncAttempts++
                if (syncAttempts < 3) {
                    syncError = "Error de conexión. Reintentando... (Intento $syncAttempts de 3)"
                    delay(3000)
                    startSync()
                } else {
                    syncError = "No se pudo establecer conexión."
                    showAppExitNotice = true
                }
            }
            isSyncing = false
        }
    }

    LaunchedEffect(Unit) { startSync() }

    if (showAppExitNotice) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Sin Conexión") },
            text = { Text("No se pudo sincronizar la hora. La validación de licencia requiere hora correcta. Sin sincronización, la aplicación se cerrará.") },
            confirmButton = {
                Button(onClick = { (context as? android.app.Activity)?.finish() }) { Text("Salir") }
            },
            dismissButton = null
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.WifiProtectedSetup, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("Sincronización de Tiempo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        if (isSyncing) {
            CircularProgressIndicator()
            Text("Conectando con servidores NTP...", modifier = Modifier.padding(top = 16.dp))
        } else if (syncedTimeText.isNotEmpty()) {
            val isTimeAccurate = abs(timeDiscrepancy) < 60000
            Card(colors = CardDefaults.cardColors(containerColor = if (isTimeAccurate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (isTimeAccurate) "Éxito" else "RELOJ DESINCRONIZADO", fontWeight = FontWeight.Bold)
                    Text("Hora Red: $syncedTimeText")
                    if (!isTimeAccurate) {
                        Text("Diferencia: ${abs(timeDiscrepancy / 60000)} min. Corrija en ajustes.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            if (isTimeAccurate) {
                Button(onClick = onSyncComplete, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Continuar") }
            } else {
                Button(onClick = { 
                    try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS)) } 
                    catch (e: Exception) {} 
                }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Corregir Hora")
                }
            }
        }
    }
}
