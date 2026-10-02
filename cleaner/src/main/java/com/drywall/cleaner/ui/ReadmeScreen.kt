package com.drywall.cleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drywall.cleaner.BuildConfig
import java.util.Calendar

data class CleanerFeatureInfo(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val features: List<String>,
    val color: Color
)

@Composable
fun ReadmeScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()
    val scrollState = rememberScrollState()
    val version = "v${BuildConfig.VERSION_NAME}"

    val features = listOf(
        CleanerFeatureInfo(
            "Reinicio de Prueba",
            Icons.Default.RestartAlt,
            "Restaura el periodo de prueba de la app principal.",
            listOf(
                "Elimina la prueba actual y activa 7 dias nuevos.",
                "La app se reinicia en modo prueba automaticamente.",
                "Genera un reporte PDF de la accion realizada."
            ),
            MaterialTheme.colorScheme.tertiary
        ),
        CleanerFeatureInfo(
            "Quitar Licencia",
            Icons.Default.RemoveCircleOutline,
            "Elimina la licencia profesional activa.",
            listOf(
                "Vuelve la app al modo prueba de 7 dias.",
                "Permite ingresar una nueva licencia despues.",
                "Genera un reporte PDF de auditoria."
            ),
            MaterialTheme.colorScheme.error
        ),
        CleanerFeatureInfo(
            "Reset Completo",
            Icons.Default.DeleteForever,
            "Limpieza total de datos de prueba y licencia.",
            listOf(
                "Elimina todos los registros de prueba.",
                "Borra la licencia profesional instalada.",
                "La app arranca como si fuera nueva instalacion."
            ),
            Color(0xFFD32F2F)
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
            }
            Text(
                "Ayuda",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "DRYWALL CLEANER",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Administrador de Prueba y Licencia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Versión $version",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Text(
            "Drywall Cleaner es la herramienta administrativa para gestionar el periodo de prueba y la licencia de la aplicacion principal Calculadora Drywall.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            "Funciones Disponibles",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        features.forEach { feature ->
            FeatureCard(feature)
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(modifier = Modifier.alpha(0.5f))

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Copyright © $currentYear YHQuintero.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                "YHQuintero Soluciones Digitales",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Todos los derechos reservados.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                "YHQuintero Soluciones $version",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureCard(feature: CleanerFeatureInfo) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    feature.icon,
                    contentDescription = null,
                    tint = feature.color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    feature.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = feature.color
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                feature.description,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))
            feature.features.forEach { item ->
                Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(feature.color, MaterialTheme.shapes.small))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        item,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
