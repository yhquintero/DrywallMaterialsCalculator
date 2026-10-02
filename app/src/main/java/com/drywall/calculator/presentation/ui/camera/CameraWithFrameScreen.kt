package com.drywall.calculator.presentation.ui.camera

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CameraWithFrameScreen(
    onCapture: () -> Unit,
    title: String = "Ajustar al Marco"
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Simulación de Camera Preview
        Box(modifier = Modifier.fillMaxSize().padding(32.dp).border(2.dp, Color.Gray)) {
            Text("Vista previa de Cámara", modifier = Modifier.align(Alignment.Center))
        }

        // El "Marco" de recorte
        Box(
            modifier = Modifier
                .size(250.dp, 350.dp)
                .align(Alignment.Center)
                .border(4.dp, Color.Yellow)
        ) {
            Text(
                "ENCUADRE AQUÍ",
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                color = Color.Yellow,
                style = MaterialTheme.typography.labelLarge
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            FloatingActionButton(onClick = onCapture) {
                Icon(Icons.Default.Camera, contentDescription = "Capturar")
            }
        }
    }
}
