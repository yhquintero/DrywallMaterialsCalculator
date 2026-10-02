package com.drywall.keygen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drywall.keygen.data.RateHistory
import com.drywall.common.ui.components.ChartDataPoint
import com.drywall.common.ui.components.EvolutionChart
import java.util.concurrent.TimeUnit

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

fun getCurrencyColor(code: String): Color = when (code.uppercase()) {
    "USD" -> Color(0xFF2196F3)
    "EUR" -> Color(0xFFE91E63)
    "MLC" -> Color(0xFF4CAF50)
    "CAD" -> Color(0xFFFF9800)
    "MEX", "MXN" -> Color(0xFF9C27B0)
    "ZELLE" -> Color(0xFF00BCD4)
    "CLA" -> Color(0xFF795548)
    else -> Color.Gray
}

@Composable
fun RateHistoryChart(
    history: List<RateHistory>,
    range: String,
    modifier: Modifier = Modifier,
    lastUpdateDate: String? = null
) {
    val currentTime = System.currentTimeMillis()
    val filterTime = when (range) {
        "1S" -> currentTime - TimeUnit.DAYS.toMillis(7)
        "1M" -> currentTime - TimeUnit.DAYS.toMillis(30)
        "3M" -> currentTime - TimeUnit.DAYS.toMillis(90)
        "6M" -> currentTime - TimeUnit.DAYS.toMillis(180)
        "1A" -> currentTime - TimeUnit.DAYS.toMillis(365)
        "2A" -> currentTime - TimeUnit.DAYS.toMillis(730)
        "5A" -> currentTime - TimeUnit.DAYS.toMillis(1825)
        else -> 0L
    }

    val filteredHistory = history.filter { it.timestamp >= filterTime && it.rate > 0.0 }
        .sortedBy { it.timestamp }
    
    if (filteredHistory.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Sin datos en este rango", color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    // Convertir RateHistory a ChartDataPoint agrupando por timestamp aproximado o exacto
    val chartData = remember(filteredHistory) {
        val groups = filteredHistory.groupBy { it.timestamp }
        groups.map { (time, points) ->
            ChartDataPoint(
                timestamp = time,
                values = points.associate { it.currencyCode to it.rate }
            )
        }.sortedBy { it.timestamp }
    }

    val currencyColorsMap = mapOf(
        "USD" to Color(0xFF2196F3),
        "EUR" to Color(0xFFE91E63),
        "MLC" to Color(0xFF4CAF50),
        "CAD" to Color(0xFFFF9800),
        "MEX" to Color(0xFF9C27B0),
        "MXN" to Color(0xFF9C27B0),
        "ZELLE" to Color(0xFF00BCD4),
        "CLA" to Color(0xFF795548)
    )

    EvolutionChart(
        history = chartData,
        currencyColors = currencyColorsMap,
        modifier = modifier,
        lastUpdateDate = lastUpdateDate
    )
}
