package com.drywall.calculator.presentation.ui.sketchup

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import java.io.InputStreamReader

@Composable
fun SketchUpImportScreen(navController: NavController, viewModel: SketchUpViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val zones by viewModel.zones.collectAsState()
    val totalArea by viewModel.totalArea.collectAsState()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val reader = InputStreamReader(stream)
                viewModel.importFromJson(context, reader)
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { filePicker.launch(arrayOf("application/json")) }) {
            Text("Importar archivo JSON (SketchUp)")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Zonas importadas:")
        zones.forEach { zone ->
            Text("${zone.name}: ${zone.areaM2} m²")
        }
        Text("Área total: $totalArea m²", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { navController.navigate("calculator") }) {
            Text("Usar área en calculadora")
        }
    }
}
