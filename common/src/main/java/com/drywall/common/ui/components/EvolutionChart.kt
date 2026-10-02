package com.drywall.common.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

data class ChartDataPoint(
    val timestamp: Long,
    val values: Map<String, Double>
)

@SuppressLint("NonObservableLocale")
@Composable
fun EvolutionChart(
    history: List<ChartDataPoint>,
    currencyColors: Map<String, Color>,
    modifier: Modifier = Modifier,
    lastUpdateDate: String? = null
) {
    if (history.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Sin datos disponibles", color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    val sortedHistory = remember(history) { history.sortedBy { it.timestamp } }
    val minTime = sortedHistory.first().timestamp
    val maxTime = sortedHistory.last().timestamp.coerceAtLeast(minTime + 1)
    
    val allValues = sortedHistory.flatMap { it.values.values }.filter { it > 0 }
    val minRate = (allValues.minOrNull() ?: 0.0) * 0.95
    val maxRate = (allValues.maxOrNull() ?: 1.0) * 1.05

    val currencyCodes = currencyColors.keys.toList()
    
    // Picos máximos globales por divisa en la selección
    val peakPoints = remember(sortedHistory, currencyCodes) {
        currencyCodes.associateWith { code ->
            sortedHistory.maxByOrNull { it.values[code] ?: 0.0 }
        }
    }

    var selectedPoint by remember { mutableStateOf<ChartDataPoint?>(null) }
    var touchX by remember { mutableFloatStateOf(-1f) }

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 40.dp, end = 16.dp, top = 20.dp, bottom = 30.dp)
                    .pointerInput(sortedHistory) {
                        detectTapGestures(
                            onPress = { offset ->
                                touchX = offset.x
                                val timeAtTouch = minTime + (offset.x / size.width) * (maxTime - minTime)
                                selectedPoint = sortedHistory.minByOrNull { abs(it.timestamp - timeAtTouch.toLong()) }
                            },
                            onTap = { offset ->
                                touchX = offset.x
                                val timeAtTouch = minTime + (offset.x / size.width) * (maxTime - minTime)
                                selectedPoint = sortedHistory.minByOrNull { abs(it.timestamp - timeAtTouch.toLong()) }
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height

                fun getX(time: Long) = ((time - minTime).toFloat() / (maxTime - minTime).toFloat()) * w
                fun getY(rate: Double) = h - (((rate - minRate).toFloat() / (maxRate - minRate).toFloat()) * h)

                // 1. Dibujar Grilla Y
                val yLines = 5
                val paint = android.graphics.Paint().apply {
                    textSize = 24f
                    color = android.graphics.Color.GRAY
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                for (i in 0..yLines) {
                    val yVal = minRate + (maxRate - minRate) * i / yLines
                    val yPos = getY(yVal)
                    drawLine(Color.LightGray.copy(alpha = 0.3f), Offset(0f, yPos), Offset(w, yPos))
                    drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.0f", yVal), -10f, yPos + 8f, paint)
                }

                // 2. Dibujar Grilla X (Fechas)
                val xLines = 4
                paint.textAlign = android.graphics.Paint.Align.CENTER
                for (i in 0..xLines) {
                    val xTime = (minTime + (maxTime - minTime) * i / xLines)
                    val xPos = getX(xTime)
                    val label = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(xTime))
                    drawContext.canvas.nativeCanvas.drawText(label, xPos, h + 35f, paint)
                }

                // 3. Dibujar Líneas de Divisas
                currencyCodes.forEach { code ->
                    val color = currencyColors[code] ?: Color.Gray
                    drawCurrencyLine(sortedHistory, code, color, ::getX, ::getY)
                    
                    // Círculo pequeño fijo en el pico máximo
                    peakPoints[code]?.let { peak ->
                        val px = peak.values[code] ?: 0.0
                        if (px > 0) {
                            drawCircle(
                                color = color,
                                radius = 4f,
                                center = Offset(getX(peak.timestamp), getY(px))
                            )
                        }
                    }
                }

                // 4. Interacción: Línea vertical discontinua y Globos
                selectedPoint?.let { sel ->
                    val selX = getX(sel.timestamp)
                    
                    // Línea vertical discontinua
                    drawLine(
                        color = Color.DarkGray.copy(alpha = 0.4f),
                        start = Offset(selX, 0f),
                        end = Offset(selX, h),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                    )

                    // Círculos huecos en cada punto de intersección
                    sel.values.forEach { (code, value) ->
                        if (value > 0) {
                            val color = currencyColors[code] ?: Color.Gray
                            val py = getY(value)
                            
                            // Círculo hueco
                            drawCircle(
                                color = color,
                                radius = 8f,
                                center = Offset(selX, py),
                                style = Stroke(width = 3f)
                            )
                            
                            // Si es el valor más alto de la selección para esta divisa, dibujar "globo"
                            if (sel == peakPoints[code]) {
                                drawPeakBalloon(color, Offset(selX, py))
                            }
                        }
                    }
                }
            }

            // 5. Mini Tabla (Tooltip)
            selectedPoint?.let { sel ->
                val isLeft = touchX > 300f // Estimación simple
                
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(if (isLeft) Alignment.TopStart else Alignment.TopEnd)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 4.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .width(IntrinsicSize.Max),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Etiqueta del día
                            Text(
                                text = SimpleDateFormat("EEEE, dd MMM", Locale.getDefault()).format(Date(sel.timestamp)),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            // Valores de cada divisa
                            sel.values.filter { it.value > 0 }.forEach { (code, value) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(currencyColors[code] ?: Color.Gray, RoundedCornerShape(2.dp))
                                    )
                                    Text(
                                        text = code,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.2f", value),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (lastUpdateDate != null || selectedPoint != null) {
            val dateToShow = lastUpdateDate ?: SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(sortedHistory.last().timestamp))
            Text(
                text = "Última actualización: $dateToShow",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp, end = 16.dp),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun DrawScope.drawCurrencyLine(
    history: List<ChartDataPoint>,
    code: String,
    color: Color,
    getX: (Long) -> Float,
    getY: (Double) -> Float
) {
    val points = history.filter { (it.values[code] ?: 0.0) > 0 }
    if (points.isEmpty()) return

    val path = Path()
    points.forEachIndexed { index, point ->
        val x = getX(point.timestamp)
        val y = getY(point.values[code]!!)
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            // Estilo step (escalón) para reflejar que la tasa se mantiene hasta el cambio
            path.lineTo(x, getY(points[index - 1].values[code]!!))
            path.lineTo(x, y)
        }
    }
    
    // Extender hasta el final del gráfico
    val lastPoint = points.last()
    val lastX = getX(lastPoint.timestamp)
    if (lastX < size.width) {
        path.lineTo(size.width, getY(lastPoint.values[code]!!))
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    )
}

private fun DrawScope.drawPeakBalloon(
    color: Color,
    center: Offset
) {
    // Dibujar un indicador de destaque para el máximo
    drawCircle(
        color = color,
        radius = 12f,
        center = center,
        style = Stroke(width = 1f)
    )
    drawCircle(
        color = color.copy(alpha = 0.2f),
        radius = 15f,
        center = center
    )
}
