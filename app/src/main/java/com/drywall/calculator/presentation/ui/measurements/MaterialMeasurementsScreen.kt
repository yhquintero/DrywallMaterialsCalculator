package com.drywall.calculator.presentation.ui.measurements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.drywall.calculator.data.local.entity.MaterialMeasurement
import com.drywall.calculator.presentation.ui.components.ValidatedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialMeasurementsScreen(
    navController: NavController,
    onShowSnackbar: (String) -> Unit,
    viewModel: MaterialMeasurementViewModel = hiltViewModel()
) {
    val measurements by viewModel.measurements.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MaterialMeasurement?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<MaterialMeasurement?>(null) }

    var name by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("0.0") }
    var height by remember { mutableStateOf("0.0") }
    var thickness by remember { mutableStateOf("0.0") }
    var length by remember { mutableStateOf("0.0") }
    var colorHex by remember { mutableStateOf("#6200EE") }
    var unitSystem by remember { mutableStateOf("metric") }

    fun clearFields() {
        name = ""
        width = "0.0"
        height = "0.0"
        thickness = "0.0"
        length = "0.0"
        colorHex = "#6200EE"
        unitSystem = "metric"
        editingItem = null
    }

    LaunchedEffect(editingItem) {
        editingItem?.let {
            name = it.name
            width = it.width.toString()
            height = it.height.toString()
            thickness = it.thickness.toString()
            length = it.length.toString()
            colorHex = it.colorHex
            unitSystem = it.unitSystem
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Header con botón Inicio y Nuevo
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    HeaderActionButton(
                        icon = Icons.Default.Home,
                        label = "Inicio",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { navController.navigate("dashboard") }
                    )

                    HeaderActionButton(
                        icon = Icons.Default.Add,
                        label = "Nuevo",
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = {
                            clearFields()
                            showDialog = true
                        },
                        isFilled = true
                    )
                }
            }

            if (measurements.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay medidas en el catálogo. Use el botón + para agregar.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(measurements) { item ->
                        MeasurementCard(
                            item = item,
                            onEdit = {
                                editingItem = item
                                showDialog = true
                            },
                            onDelete = { showDeleteConfirm = item }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Eliminar Medida") },
            text = { Text("¿Desea eliminar '${showDeleteConfirm!!.name}' del catálogo?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMeasurement(showDeleteConfirm!!)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancelar") }
            }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false; clearFields() },
            title = { Text(if (editingItem == null) "Nueva Medida de Material" else "Editar Medida") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ValidatedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Nombre del Catálogo (ej. Perfiles Omega)",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Unidades:", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(16.dp))
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = unitSystem == "metric",
                                onClick = { unitSystem = "metric" },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                label = { Text("m") }
                            )
                            SegmentedButton(
                                selected = unitSystem == "imperial",
                                onClick = { unitSystem = "imperial" },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                label = { Text("ft") }
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValidatedTextField(
                            value = width,
                            onValueChange = { width = it },
                            label = "Ancho",
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        ValidatedTextField(
                            value = height,
                            onValueChange = { height = it },
                            label = "Alto",
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        ValidatedTextField(
                            value = thickness,
                            onValueChange = { thickness = it },
                            label = "Espesor",
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        ValidatedTextField(
                            value = length,
                            onValueChange = { length = it },
                            label = "Largo",
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }

                    Text("Color Distintivo:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val colors = listOf("#6200EE", "#03DAC5", "#FF0187", "#FFB74D", "#4CAF50", "#2196F3")
                        colors.forEach { colorStr ->
                            val color = Color(android.graphics.Color.parseColor(colorStr))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(color, MaterialTheme.shapes.small)
                                    .clickable { colorHex = colorStr }
                                    .then(if (colorHex == colorStr) Modifier.background(Color.Black.copy(alpha = 0.3f), MaterialTheme.shapes.small) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                if (colorHex == colorStr) {
                                    Icon(Icons.Default.Save, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.saveMeasurements(
                                MaterialMeasurement(
                                    id = editingItem?.id ?: 0,
                                    name = name,
                                    width = width.toDoubleOrNull() ?: 0.0,
                                    height = height.toDoubleOrNull() ?: 0.0,
                                    thickness = thickness.toDoubleOrNull() ?: 0.0,
                                    length = length.toDoubleOrNull() ?: 0.0,
                                    unitSystem = unitSystem,
                                    colorHex = colorHex
                                )
                            )
                            onShowSnackbar("Medida guardada correctamente")
                            showDialog = false
                            clearFields()
                        }
                    },
                    enabled = name.isNotBlank(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false; clearFields() }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun HeaderActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    isFilled: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onClick,
            colors = if (isFilled) {
                IconButtonDefaults.filledIconButtonColors(containerColor = containerColor, contentColor = contentColor)
            } else {
                IconButtonDefaults.iconButtonColors(containerColor = containerColor, contentColor = contentColor)
            },
            modifier = Modifier.size(44.dp)
        ) {
            Icon(icon, contentDescription = label)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MeasurementCard(item: MaterialMeasurement, onEdit: () -> Unit, onDelete: () -> Unit) {
    val cardColor = Color(android.graphics.Color.parseColor(item.colorHex))
    val unit = if (item.unitSystem == "metric") "m" else "ft"

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = cardColor.copy(alpha = 0.05f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp, 100.dp)
                    .background(cardColor, MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cardColor)
                Spacer(modifier = Modifier.height(8.dp))
                // Data stacked vertically as requested
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Ancho: ${item.width} $unit", style = MaterialTheme.typography.bodyMedium)
                    Text("Alto: ${item.height} $unit", style = MaterialTheme.typography.bodyMedium)
                    Text("Espesor: ${item.thickness} $unit", style = MaterialTheme.typography.bodyMedium)
                    Text("Largo: ${item.length} $unit", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = cardColor) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
