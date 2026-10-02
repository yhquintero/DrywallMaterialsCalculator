package com.drywall.calculator.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorLogScreen(
    viewModel: ErrorLogViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val logs by viewModel.logs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val diagnosticFile by viewModel.diagnosticFile.collectAsState()
    
    var alertData by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadExistingLogs(context)
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ErrorLogViewModel.UiEvent.ShowAlert -> alertData = event.title to event.message
                is ErrorLogViewModel.UiEvent.ShowSnackbar -> {
                    // Usar Toast si no hay Scaffold con SnackbarHostState aquí
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Centro de Diagnóstico (IA)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLogs(context) }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Limpiar Logs", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Optimizado para Gemini AI", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Si experimentas un cierre inesperado, genera un reporte completo para analizarlo con Inteligencia Artificial y recibir una solución técnica.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { viewModel.runFullSystemScan(context) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isScanning,
                shape = MaterialTheme.shapes.medium
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Troubleshoot, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar Escaneo de Módulos")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Vista Previa del Log:", style = MaterialTheme.typography.titleSmall)
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(Color.Black, MaterialTheme.shapes.small)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = logs.ifEmpty { "Sin registros detectados..." },
                    color = Color(0xFF00FF00), // Verde consola
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }

            if (diagnosticFile != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { shareFile(context, diagnosticFile!!) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.FileDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Exportar .log")
                    }

                    Button(
                        onClick = { shareViaWhatsApp(context, diagnosticFile!!, logs) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)) // WhatsApp Green
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("WhatsApp", color = Color.White)
                    }
                }
            }
        }
    }
}

// Función auxiliar para compartir archivo de texto (similar a PdfUtils pero genérica)
private fun shareFile(context: android.content.Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file.canonicalFile
        )
        
        // Intentar obtener un MIME type más específico
        val extension = file.extension
        val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "text/plain"

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, file.name)
            putExtra(android.content.Intent.EXTRA_TITLE, file.name)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Enviar Log de Diagnóstico"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error al compartir: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun shareViaWhatsApp(context: android.content.Context, file: File, logsText: String) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file.canonicalFile
        )
        
        // Usar text/plain con el nombre de archivo correcto para que WhatsApp preserve la extensión .log
        // El FLAG_GRANT_READ_URI_PERMISSION permite que WhatsApp lea el archivo
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.whatsapp")
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TITLE, file.name)
            putExtra(android.content.Intent.EXTRA_SUBJECT, file.name)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Si WhatsApp no está instalado, usar el genérico
        android.widget.Toast.makeText(context, "WhatsApp no instalado o error. Usando compartir genérico.", android.widget.Toast.LENGTH_SHORT).show()
        shareFile(context, file)
    }
}
