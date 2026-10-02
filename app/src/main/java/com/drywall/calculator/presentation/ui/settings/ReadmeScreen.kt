package com.drywall.calculator.presentation.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.Preview
import com.drywall.calculator.BuildConfig
import com.drywall.calculator.presentation.theme.DrywallTheme
import java.util.Calendar

data class FeatureInfo(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val features: List<String>,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadmeScreen() {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()
    val scrollState = rememberScrollState()
    val version = "v${BuildConfig.VERSION_NAME}"

    val features = listOf(
        FeatureInfo(
            "Panel de Inicio (Dashboard)",
            Icons.Default.Home,
            "Resumen visual e indicadores clave.",
            listOf(
                "Métricas en tiempo real de obras y clientes.",
                "Gráficos de estado de stock en almacén.",
                "Tendencias de construcción y evolución de divisas."
            ),
            MaterialTheme.colorScheme.primary
        ),
        FeatureInfo(
            "Perfil y Empresa",
            Icons.Default.Business,
            "Identidad corporativa y datos fiscales.",
            listOf(
                "Configuración de logo y datos de contacto.",
                "Firma digital profesional para reportes.",
                "Gestión de moneda principal y país de operación."
            ),
            Color(0xFF607D8B)
        ),
        FeatureInfo(
            "Cuentas Bancarias",
            Icons.Default.AccountBalance,
            "Gestión de canales de pago.",
            listOf(
                "Registro de múltiples cuentas (CUP, MLC, USD).",
                "Soporte para transferencias y datos de pago.",
                "Copiado rápido de números de cuenta."
            ),
            Color(0xFF3F51B5)
        ),
        FeatureInfo(
            "Gestión de Impuestos",
            Icons.Default.Percent,
            "Automatización tributaria.",
            listOf(
                "Detección automática de impuestos por país.",
                "Configuración de porcentajes personalizados (IVA, ITBI, etc.).",
                "Aplicación automática en presupuestos finales."
            ),
            Color(0xFF9C27B0)
        ),
        FeatureInfo(
            "Gestión de Clientes",
            Icons.Default.People,
            "Base de datos CRM avanzada.",
            listOf(
                "Escaneo inteligente de carnets de identidad.",
                "Registro de fotos, firmas y ubicación.",
                "Búsqueda instantánea por nombre o CI."
            ),
            Color(0xFFFF9800)
        ),
        FeatureInfo(
            "Obras y Proyectos",
            Icons.Default.Architecture,
            "Administración de proyectos constructivos.",
            listOf(
                "Registro detallado de áreas y tipos de obra.",
                "Vinculación directa con el directorio de clientes.",
                "Seguimiento de estado y avance físico."
            ),
            Color(0xFF2196F3)
        ),
        FeatureInfo(
            "Mano de Obra",
            Icons.Default.Engineering,
            "Control de tarifas y servicios.",
            listOf(
                "Definición de precios por unidad de medida.",
                "Categorización de servicios técnicos.",
                "Cálculo automático de costos de labor."
            ),
            Color(0xFFFFC107)
        ),
        FeatureInfo(
            "Medidas de Materiales",
            Icons.Default.SquareFoot,
            "Personalización técnica.",
            listOf(
                "Ajuste de dimensiones estándar de planchas.",
                "Configuración de longitudes de perfiles.",
                "Sincronización con el motor de cálculo."
            ),
            Color(0xFFE91E63)
        ),
        FeatureInfo(
            "Catálogo de Materiales",
            Icons.Default.Inventory,
            "Lista maestra de suministros.",
            listOf(
                "Precios de compra y márgenes de utilidad.",
                "Cálculo automático de precio de venta.",
                "Soporte para múltiples unidades (m², kg, etc.)."
            ),
            Color(0xFF4CAF50)
        ),
        FeatureInfo(
            "Calculadora",
            Icons.Default.Calculate,
            "Cálculo preciso de materiales.",
            listOf(
                "Cuantificación de perfiles, placas y masillas.",
                "Soporte para sistemas de Tabique y Techo.",
                "Gestión de desperdicios y aproximaciones."
            ),
            Color(0xFF00BCD4)
        ),
        FeatureInfo(
            "Almacén e Inventario",
            Icons.Default.Warehouse,
            "Control logístico total.",
            listOf(
                "Gestión de existencias reales (Stock).",
                "Registro de entradas y salidas de material.",
                "Alertas críticas de stock bajo.",
                "Tarjeta de Estiba con vista previa y exportación PDF.",
                "Edición y eliminación de transacciones con control de stock.",
                "Alerta al intentar salida sin stock disponible."
            ),
            Color(0xFF795548)
        ),
        FeatureInfo(
            "Estadísticas y Análisis",
            Icons.Default.BarChart,
            "Inteligencia de negocio.",
            listOf(
                "Gráficos de rentabilidad por proyecto.",
                "Análisis de consumo de materiales histórico.",
                "Reportes de eficiencia de mano de obra.",
                "Exportación de rendimiento por categoría a PDF.",
                "Filtrado por tipo de unidad de medida."
            ),
            Color(0xFF673AB7)
        ),
        FeatureInfo(
            "Convertidores y Divisas",
            Icons.Default.CurrencyExchange,
            "Herramientas financieras integradas.",
            listOf(
                "Conversor de monedas con tasas actualizables.",
                "Convertidor de unidades técnicas (m² a ft², etc.).",
                "Calculadora de desglose de efectivo."
            ),
            Color(0xFF009688)
        ),
        FeatureInfo(
            "Gestión de Proveedores",
            Icons.Default.ContactPage,
            "Directorio de suministradores.",
            listOf(
                "Base de datos de contacto de proveedores.",
                "Vinculación con catálogos de materiales.",
                "Calificación y notas de servicio post-venta."
            ),
            Color(0xFF3F51B5)
        ),
        FeatureInfo(
            "Órdenes de Compra",
            Icons.Default.ShoppingCart,
            "Adquisición de suministros.",
            listOf(
                "Generación automática de órdenes desde la Calculadora.",
                "Listas de compra agrupadas por tipo de construcción.",
                "Exportación profesional a PDF compartible.",
                "Seguimiento de pedidos pendientes y recibidos.",
                "Guía paso a paso para crear órdenes de compra."
            ),
            Color(0xFFFF5722)
        ),
        FeatureInfo(
            "Bitácora de Obra",
            Icons.Default.EditNote,
            "Diario técnico de campo.",
            listOf(
                "Registro diario de incidencias y progreso.",
                "Captura de fotos para el historial técnico.",
                "Seguimiento porcentual del avance de obra."
            ),
            Color(0xFF4DB6AC)
        ),
        FeatureInfo(
            "SketchUp y Exportación",
            Icons.Default.FileUpload,
            "Interoperabilidad y multimedia.",
            listOf(
                "Importación de reportes CSV desde SketchUp.",
                "Galería de imágenes integrada por proyecto.",
                "Generación de reportes PDF de alta calidad."
            ),
            Color(0xFFF44336)
        ),
        FeatureInfo(
            "Mantenimiento y Recuperación",
            Icons.Default.Build,
            "Integridad y compatibilidad total.",
            listOf(
                "Exportación de datos con versión 1 para reinicio limpio.",
                "Restauración de respaldos sin importar la versión.",
                "Migración automática de seguridad heredada.",
                "Reparación estructural de bases de datos corruptas."
            ),
            Color(0xFFFF9800)
        ),
        FeatureInfo(
            "Seguridad y Ajustes",
            Icons.Default.Settings,
            "Personalización y blindaje.",
            listOf(
                "Temas visuales adaptable y configuración de UI.",
                "Cifrado de grado bancario (AES-256/GCM).",
                "Gestión de licencias y auditoría de errores."
            ),
            Color(0xFF607D8B)
        ),
        FeatureInfo(
            "Exportación PDF",
            Icons.Default.PictureAsPdf,
            "Generación profesional de documentos.",
            listOf(
                "Cotizaciones, Órdenes de Compra y Facturas Finales.",
                "Reportes de Medidas, Inventario y Clientes.",
                "Tarjetas de Estiba y Carnés de Identificación.",
                "Historial de Licencias y Configuración."
            ),
            Color(0xFF00796B)
        ),
        FeatureInfo(
            "Licencias y Activación",
            Icons.Default.VerifiedUser,
            "Gestión segura de licencias profesionales.",
            listOf(
                "Activación por prueba gratuita (7 días).",
                "Licencias por tiempo: 1 día, 1 semana, 1 mes, 1 año, 2 años.",
                "Verificación de firma digital RSA-4096.",
                "Sincronización de hora NTP para validez."
            ),
            Color(0xFF5D4037)
        ),
        FeatureInfo(
            "Importación PDF",
            Icons.Default.FileDownload,
            "Recuperación de datos desde documentos.",
            listOf(
                "Importar reportes de medidas desde PDF.",
                "Comparación visual de datos antes de aplicar.",
                "Modos: Agregar, Combinar, Sobreescribir.",
                "Compatible con PDFs generados por la app."
            ),
            Color(0xFF37474F)
        ),
        FeatureInfo(
            "Registro de Errores",
            Icons.Default.BugReport,
            "Diagnóstico y solución de fallos.",
            listOf(
                "Captura automática de excepciones no controladas.",
                "Exportación de logs para análisis técnico.",
                "Reporte optimizado para IA (Gemini).",
                "Limpieza y gestión de historial de errores."
            ),
            Color(0xFFBF360C)
        ),
        FeatureInfo(
            "Calculadora de Dinero",
            Icons.Default.AttachMoney,
            "Desglose y conversión de efectivo.",
            listOf(
                "Cálculo de billetes y monedas por denominación.",
                "Soporte multi-moneda (CUP, USD, EUR, MLC).",
                "Totalizador rápido para arqueos de caja."
            ),
            Color(0xFF2E7D32)
        ),
        FeatureInfo(
            "Convertidor de Unidades",
            Icons.Default.Straighten,
            "Conversión técnica integrada.",
            listOf(
                "Área: m², ft², yd², varas².",
                "Longitud: m, ft, in, cm, mm.",
                "Volumen: m³, ft³, litros, galones.",
                "Precisión configurable (hasta 4 decimales)."
            ),
            Color(0xFF1565C0)
        ),
        FeatureInfo(
            "Carné de Identificación",
            Icons.Default.Badge,
            "Credencial profesional personalizada.",
            listOf(
                "Foto, datos personales y cargo.",
                "Logo de empresa y código QR.",
                "Firma digital y fecha de emisión.",
                "Exportación a PDF lista para imprimir."
            ),
            Color(0xFF6A1B9A)
        ),
        FeatureInfo(
            "Ajustes Avanzados",
            Icons.Default.Tune,
            "Personalización profunda del sistema.",
            listOf(
                "Temas: Claro, Oscuro, Automático.",
                "Precisión decimal (2-4 decimales).",
                "Unidad de medida principal (Métrico/Imperial).",
                "Respaldos automáticos y programados."
            ),
            Color(0xFF455A64)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Encabezado de Presentación - Estilo Keygen
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
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CALCULADORA DRYWALL",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Versión $version",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Text(
            "YHQuintero Calculadora Drywall es la herramienta definitiva para el profesional de la construcción ligera, diseñada para maximizar la eficiencia y precisión en cada proyecto.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            "Módulos de la Aplicación",
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
fun FeatureCard(feature: FeatureInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = feature.color.copy(alpha = 0.1f)
        ),
        border = BorderStroke(2.dp, feature.color.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    feature.icon, 
                    contentDescription = null, 
                    tint = feature.color,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    feature.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = feature.color
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(feature.color, MaterialTheme.shapes.small)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                feature.description,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = feature.color.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            feature.features.forEach { item ->
                Row(modifier = Modifier.padding(vertical = 2.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

@Preview(showBackground = true)
@Composable
fun ReadmeScreenPreview() {
    DrywallTheme {
        ReadmeScreen()
    }
}
