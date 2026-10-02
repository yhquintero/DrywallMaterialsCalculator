package com.drywall.keygen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drywall.keygen.ErrorLogViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorLogScreen(
    viewModel: ErrorLogViewModel = viewModel()
) {
    val context = LocalContext.current
    val logs by viewModel.logs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val diagnosticFile by viewModel.diagnosticFile.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadExistingLogs(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Centro de Diagnóstico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { viewModel.clearLogs(context) }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Limpiar Logs", tint = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(16.dp))

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
                    "Genera un reporte completo para analizar fallos técnicos mediante Inteligencia Artificial.",
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
                color = Color(0xFF00FF00),
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
                    Text("Exportar")
                }

                Button(
                    onClick = { shareViaWhatsApp(context, diagnosticFile!!) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Message, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("WhatsApp", color = Color.White)
                }
            }
        }
    }
}

private fun shareFile(context: android.content.Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file.canonicalFile
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Enviar Log de Diagnóstico"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error al compartir: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun shareViaWhatsApp(context: android.content.Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file.canonicalFile
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.whatsapp")
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        shareFile(context, file)
    }
}
