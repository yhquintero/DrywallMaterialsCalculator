package com.drywall.calculator.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier as ComposeModifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val HighlightBlue = Color(0xFF1976D2)

/**
 * Campo de fecha reutilizable que muestra la fecha en formato dd/MM/yyyy y abre un
 * calendario personalizado al pulsarlo.
 *
 * - No permite seleccionar fechas mayores a la fecha actual (allowFuture = false por defecto).
 * - Las fechas incluidas en [highlightedDates] se muestran con un círculo azul.
 *
 * @param value texto de la fecha en formato dd/MM/yyyy (puede estar vacío)
 * @param onValueChange se invoca con la nueva fecha formateada dd/MM/yyyy
 */
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Fecha (dd/mm/aaaa)",
    modifier: ComposeModifier = ComposeModifier,
    isError: Boolean = false,
    allowFuture: Boolean = false,
    highlightedDates: List<Date> = emptyList()
) {
    var showPicker by remember { mutableStateOf(false) }
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    OutlinedTextField(
        value = value,
        onValueChange = { /* solo lectura, se edita vía calendario */ },
        label = { Text(label) },
        readOnly = true,
        isError = isError,
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Abrir calendario")
            }
        },
        modifier = modifier,
        singleLine = true
    )

    if (showPicker) {
        CalendarPickerDialog(
            initialDate = parseDateOrToday(value, sdf),
            allowFuture = allowFuture,
            highlightedDates = highlightedDates,
            onDismiss = { showPicker = false },
            onDateSelected = { picked ->
                onValueChange(sdf.format(picked))
                showPicker = false
            }
        )
    }
}

@Composable
private fun CalendarPickerDialog(
    initialDate: Date,
    allowFuture: Boolean,
    highlightedDates: List<Date>,
    onDismiss: () -> Unit,
    onDateSelected: (Date) -> Unit
) {
    val dayKeyFmt = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()) }
    val highlightedKeys = remember(highlightedDates) {
        highlightedDates.map { dayKeyFmt.format(it) }.toSet()
    }
    val todayKey = remember { dayKeyFmt.format(Date()) }
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.time
    }

    val monthCal = remember {
        Calendar.getInstance().apply {
            time = initialDate
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    var displayYear by remember { mutableStateOf(monthCal.get(Calendar.YEAR)) }
    var displayMonth by remember { mutableStateOf(monthCal.get(Calendar.MONTH)) }
    var selectedKey by remember { mutableStateOf(dayKeyFmt.format(initialDate)) }

    val monthNames = remember {
        arrayOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
    }
    val weekDays = listOf("D", "L", "M", "M", "J", "V", "S")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar fecha") },
        text = {
            Column {
                // Cabecera con navegación de mes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = {
                        if (displayMonth == 0) { displayMonth = 11; displayYear-- } else displayMonth--
                    }) { Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior") }

                    Text(
                        "${monthNames[displayMonth]} $displayYear",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Bloquear avanzar a meses completamente futuros
                    val canGoNext = allowFuture || run {
                        val now = Calendar.getInstance()
                        displayYear < now.get(Calendar.YEAR) ||
                            (displayYear == now.get(Calendar.YEAR) && displayMonth < now.get(Calendar.MONTH))
                    }
                    IconButton(
                        onClick = { if (displayMonth == 11) { displayMonth = 0; displayYear++ } else displayMonth++ },
                        enabled = canGoNext
                    ) { Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente") }
                }

                Spacer(Modifier.height(4.dp))

                // Encabezado de días de la semana
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekDays.forEach { d ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(d, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Rejilla de días
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, displayYear)
                    set(Calendar.MONTH, displayMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Dom
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                val totalCells = firstDayOfWeek + daysInMonth
                val rows = (totalCells + 6) / 7

                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            val dayNum = cellIndex - firstDayOfWeek + 1
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (dayNum in 1..daysInMonth) {
                                    val dayCal = Calendar.getInstance().apply {
                                        set(displayYear, displayMonth, dayNum, 0, 0, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    val key = dayKeyFmt.format(dayCal.time)
                                    val isFuture = !allowFuture && dayCal.time.after(todayStart)
                                    val isHighlighted = highlightedKeys.contains(key)
                                    val isSelected = key == selectedKey
                                    val isToday = key == todayKey

                                    val bgColor = when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isHighlighted -> HighlightBlue.copy(alpha = 0.18f)
                                        else -> Color.Transparent
                                    }
                                    val borderMod = when {
                                        isHighlighted && !isSelected -> Modifier.border(1.5.dp, HighlightBlue, CircleShape)
                                        isToday && !isSelected -> Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                        else -> Modifier
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(bgColor)
                                            .then(borderMod)
                                            .clickable(enabled = !isFuture) {
                                                selectedKey = key
                                                onDateSelected(dayCal.time)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isHighlighted || isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                isHighlighted -> HighlightBlue
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, HighlightBlue, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Fechas con precio registrado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun parseDateOrToday(text: String, sdf: SimpleDateFormat): Date {
    return try {
        sdf.parse(text) ?: Date()
    } catch (e: Exception) {
        Date()
    }
}
