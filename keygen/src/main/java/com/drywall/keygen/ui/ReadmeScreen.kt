package com.drywall.keygen.ui

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
import com.drywall.keygen.BuildConfig
import java.util.Calendar

data class KeygenFeatureInfo(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val features: List<String>,
    val color: Color
)

@Composable
fun ReadmeScreen() {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()
    val scrollState = rememberScrollState()
    val version = "v${BuildConfig.VERSION_NAME}"

    val features = listOf(
        KeygenFeatureInfo(
            "Generador Profesional",
            Icons.Default.AddCircle,
            "Emisión instantánea de licencias de uso.",
            listOf(
                "Cálculo automático de precios según tasa del día.",
                "Carga inteligente de datos desde portapapeles (WhatsApp).",
                "Generación de facturas PDF personalizadas."
            ),
            Color(0xFF2196F3)
        ),
        KeygenFeatureInfo(
            "Control de Historial",
            Icons.Default.History,
            "Gestión integral de licencias emitidas.",
            listOf(
                "Seguimiento de estados de pago (Pendiente/Pagado).",
                "Reenvío rápido de códigos de activación.",
                "Alertas de vencimiento próximo para clientes."
            ),
            Color(0xFFFF9800)
        ),
        KeygenFeatureInfo(
            "Monitor de Divisas",
            Icons.Default.Timeline,
            "Seguimiento de tasas en tiempo real.",
            listOf(
                "Gráficos interactivos de evolución (USD, EUR, MLC, etc.).",
                "Sincronización automática con fuentes del mercado.",
                "Historial de variaciones para análisis de precios."
            ),
            Color(0xFFE91E63)
        ),
        KeygenFeatureInfo(
            "Inteligencia Financiera",
            Icons.Default.Assessment,
            "Dashboard de ingresos y rendimiento.",
            listOf(
                "Estadísticas de recaudación por tipo de moneda.",
                "Reportes visuales de eficiencia de ventas.",
                "Exportación de informes de ingresos a PDF."
            ),
            Color(0xFF4CAF50)
        ),
        KeygenFeatureInfo(
            "Seguridad de Grado Militar",
            Icons.Default.Security,
            "Blindaje de acceso administrativo.",
            listOf(
                "Autenticación biométrica (Huella/Rostro).",
                "Cifrado AES-256 para datos sensibles.",
                "Protección contra dispositivos rooteados."
            ),
            Color(0xFF607D8B)
        ),
        KeygenFeatureInfo(
            "Sincronización NTP",
            Icons.Default.WifiProtectedSetup,
            "Garantía de integridad temporal.",
            listOf(
                "Validación de hora real mediante servidores internacionales.",
                "Prevención de fraudes por manipulación de reloj local.",
                "Ajuste automático de discrepancias horarias."
            ),
            Color(0xFF3F51B5)
        ),
        KeygenFeatureInfo(
            "Respaldo de Datos",
            Icons.Default.CloudSync,
            "Continuidad y portabilidad total.",
            listOf(
                "Exportación segura de base de datos completa.",
                "Restauración instantánea de registros históricos.",
                "Limpieza automática de archivos temporales."
            ),
            Color(0xFF009688)
        ),
        KeygenFeatureInfo(
            "Gestión de Configuración",
            Icons.Default.Settings,
            "Personalización del motor de cálculo.",
            listOf(
                "Ajuste manual de tasas de cambio preferenciales.",
                "Definición de precios personalizados por plan.",
                "Administración de claves públicas del sistema."
            ),
            Color(0xFF673AB7)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Encabezado
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
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "KEYGEN PRO YHQUINTERO",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Consola de Administración de Licencias",
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
            "Keygen Pro es la herramienta administrativa exclusiva para la gestión de licencias de la Calculadora Drywall, permitiendo un control total sobre las ventas, tasas y seguridad del ecosistema.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            "Módulos Administrativos",
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

@Composable
fun FeatureCard(feature: KeygenFeatureInfo) {
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
