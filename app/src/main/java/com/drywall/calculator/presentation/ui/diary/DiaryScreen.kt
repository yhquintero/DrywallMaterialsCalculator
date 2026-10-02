package com.drywall.calculator.presentation.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.drywall.calculator.data.local.entity.WorkDiary
import com.drywall.calculator.presentation.ui.projects.ProjectsViewModel
import com.drywall.calculator.presentation.ui.components.ValidatedTextField
import java.text.SimpleDateFormat
import androidx.compose.ui.platform.LocalConfiguration
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    diaryViewModel: DiaryViewModel = hiltViewModel(),
    projectsViewModel: ProjectsViewModel = hiltViewModel()
) {
    val projects by projectsViewModel.projects.collectAsState(initial = emptyList())
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    val diaryEntries by diaryViewModel.getDiaryEntries(selectedProjectId ?: "").collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<WorkDiary?>(null) }
    var entryToDelete by remember { mutableStateOf<WorkDiary?>(null) }
    var expandedProjects by remember { mutableStateOf(false) }
    var showSuccessAlert by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (selectedProjectId != null) {
                ExtendedFloatingActionButton(
                    onClick = { 
                        editingEntry = null
                        showDialog = true 
                    },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Nueva Entrada") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Filtro Superior - Ahora más prominente y a lo largo de la vista
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expandedProjects,
                        onExpandedChange = { expandedProjects = !expandedProjects },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = projects.find { it.id == selectedProjectId }?.name ?: "Seleccione un Proyecto para filtrar",
                            onValueChange = {},
                            readOnly = true,
                            leadingIcon = { Icon(Icons.Default.FilterList, null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProjects) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expandedProjects,
                            onDismissRequest = { expandedProjects = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            projects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text(project.name, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        selectedProjectId = project.id
                                        expandedProjects = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Área de Contenido - A lo largo de toda la ventana
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (selectedProjectId != null) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        if (diaryEntries.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillParentMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.EditNote, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    Spacer(Modifier.height(16.dp))
                                    Text("No hay registros en esta bitácora.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Use el botón + para añadir una nota hoy.", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            items(diaryEntries) { entry ->
                                DiaryEntryCard(
                                    entry = entry,
                                    onEdit = {
                                        editingEntry = entry
                                        showDialog = true
                                    },
                                    onDelete = {
                                        entryToDelete = entry
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Architecture, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Gestión de Bitácora de Obra", 
                            style = MaterialTheme.typography.titleLarge, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Seleccione un proyecto arriba para comenzar", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Eliminar Entrada de Bitácora") },
            text = { Text("¿Desea eliminar la entrada '${entryToDelete!!.activity}' definitivamente?") },
            confirmButton = {
                Button(
                    onClick = {
                        diaryViewModel.deleteEntry(entryToDelete!!)
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    if (showDialog && selectedProjectId != null) {
        DiaryAddDialog(
            projectId = selectedProjectId!!,
            entry = editingEntry,
            onDismiss = { showDialog = false },
            onSave = { 
                if (editingEntry == null) {
                    diaryViewModel.addEntry(it)
                } else {
                    diaryViewModel.updateEntry(it)
                }
                if (it.progressPercent == 100) {
                    showSuccessAlert = true
                }
            }
        )
    }

    if (showSuccessAlert) {
        AlertDialog(
            onDismissRequest = { showSuccessAlert = false },
            title = { Text("¡Proyecto Finalizado!") },
            text = { Text("Se ha alcanzado el 100% de progreso. Cumplimiento Exitoso.") },
            confirmButton = {
                Button(onClick = { showSuccessAlert = false }) {
                    Text("Excelente")
                }
            }
        )
    }
}

@Composable
fun DiaryEntryCard(
    entry: WorkDiary,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy", LocalConfiguration.current.locales[0]).format(Date(entry.date))
    
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(dateStr, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(entry.weather, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(entry.activity, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (entry.observations.isNotEmpty()) {
                Text(entry.observations, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { entry.progressPercent / 100f },
                    modifier = Modifier.weight(1f).height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("${entry.progressPercent}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DiaryAddDialog(
    projectId: String,
    entry: WorkDiary? = null,
    onDismiss: () -> Unit,
    onSave: (WorkDiary) -> Unit
) {
    var activity by remember { mutableStateOf(entry?.activity ?: "") }
    var observations by remember { mutableStateOf(entry?.observations ?: "") }
    var progress by remember { mutableFloatStateOf(entry?.progressPercent?.toFloat() ?: 50f) }
    var weather by remember { mutableStateOf(entry?.weather ?: "Soleado") }
    
    val weatherOptions = listOf("Soleado", "Nublado", "Lluvia Ligera", "Lluvia Fuerte", "Tormenta", "Viento Fuerte")
    var expandedWeather by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "Nueva Actividad" else "Editar Actividad") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ValidatedTextField(value = activity, onValueChange = { activity = it }, label = "Actividad Realizada")
                ValidatedTextField(value = observations, onValueChange = { observations = it }, label = "Observaciones")
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Avance del Proyecto: ${progress.toInt()}%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Slider(
                    value = progress, 
                    onValueChange = { progress = it }, 
                    valueRange = 0f..100f,
                    steps = 100
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                @OptIn(ExperimentalMaterial3Api::class)
                ExposedDropdownMenuBox(
                    expanded = expandedWeather,
                    onExpandedChange = { expandedWeather = !expandedWeather }
                ) {
                    OutlinedTextField(
                        value = weather,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Estado del Clima") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedWeather) }
                    )
                    ExposedDropdownMenu(expanded = expandedWeather, onDismissRequest = { expandedWeather = false }) {
                        weatherOptions.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { weather = option; expandedWeather = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (activity.isNotBlank()) {
                    onSave(WorkDiary(
                        id = entry?.id ?: 0,
                        projectId = projectId,
                        date = entry?.date ?: System.currentTimeMillis(),
                        activity = activity,
                        observations = observations,
                        progressPercent = progress.toInt(),
                        weather = weather
                    ))
                    onDismiss()
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
