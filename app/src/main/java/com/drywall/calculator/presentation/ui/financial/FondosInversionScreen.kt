package com.drywall.calculator.presentation.ui.financial

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.drywall.calculator.data.local.entity.FondoInversion
import com.drywall.calculator.presentation.ui.projects.ProjectsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FondosInversionScreen(
    viewModel: FinancialReportsViewModel = hiltViewModel(),
    projectsViewModel: ProjectsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val fondos by viewModel.fondosInversion.collectAsState()
    val projects by projectsViewModel.projects.collectAsState(initial = emptyList())
    val formState by viewModel.fondoFormState.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var fondoToDelete by remember { mutableStateOf<FondoInversion?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fondos de Inversión") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                viewModel.updateFondoForm(FondoInversionFormState())
                showAddDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Registrar Inversión")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Inversiones para $selectedMonth/$selectedYear",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            val filteredFondos = fondos.filter { it.mes == selectedMonth && it.anio == selectedYear }
            if (filteredFondos.isEmpty()) {
                item { Text("No hay inversiones registradas para este mes.") }
            } else {
                items(filteredFondos) { fondo ->
                    FondoItem(
                        fondo = fondo,
                        onEdit = {
                            viewModel.updateFondoForm(FondoInversionFormState(
                                id = fondo.id,
                                proyectoId = fondo.proyectoId,
                                proyectoNombre = fondo.proyectoNombre,
                                inversionMateriales = fondo.inversionMateriales,
                                inversionManoObra = fondo.inversionManoObra,
                                inversionTransporte = fondo.inversionTransporte,
                                inversionEquipos = fondo.inversionEquipos,
                                inversionOtros = fondo.inversionOtros,
                                moneda = fondo.moneda,
                                notas = fondo.notas
                            ))
                            showAddDialog = true
                        },
                        onDelete = { fondoToDelete = fondo }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        FondoFormDialog(
            state = formState,
            projects = projects,
            isEditing = formState.id.isNotBlank(),
            onStateChange = { viewModel.updateFondoForm(it) },
            onSave = {
                viewModel.saveFondo()
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    fondoToDelete?.let { fondo ->
        AlertDialog(
            onDismissRequest = { fondoToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Eliminar Inversión") },
            text = {
                Text("¿Está seguro de eliminar la inversión del proyecto \"${fondo.proyectoNombre}\" por $${fondo.totalInversion} ${fondo.moneda}? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFondo(fondo.id)
                        fondoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { fondoToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun FondoItem(
    fondo: FondoInversion,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = fondo.proyectoNombre, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) { 
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) 
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FondoLine("Materiales", fondo.inversionMateriales, fondo.moneda)
            FondoLine("Mano de Obra", fondo.inversionManoObra, fondo.moneda)
            FondoLine("Transporte", fondo.inversionTransporte, fondo.moneda)
            FondoLine("Equipos", fondo.inversionEquipos, fondo.moneda)
            FondoLine("Otros", fondo.inversionOtros, fondo.moneda)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TOTAL", fontWeight = FontWeight.Bold)
                Text("$${fondo.totalInversion} ${fondo.moneda}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun FondoLine(label: String, amount: Double, currency: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text("$${amount} $currency", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FondoFormDialog(
    state: FondoInversionFormState,
    projects: List<com.drywall.calculator.data.local.entity.Project>,
    isEditing: Boolean = false,
    onStateChange: (FondoInversionFormState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Editar Inversión" else "Registrar Inversión") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = state.proyectoNombre.ifBlank { "Seleccionar Obra" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Obra / Proyecto") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            projects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text(project.name) },
                                    onClick = { 
                                        onStateChange(state.copy(proyectoId = project.id, proyectoNombre = project.name))
                                        expanded = false 
                                    }
                                )
                            }
                        }
                    }
                }
                item { FondoInputField("Inversión Materiales", state.inversionMateriales) { onStateChange(state.copy(inversionMateriales = it)) } }
                item { FondoInputField("Inversión Mano de Obra", state.inversionManoObra) { onStateChange(state.copy(inversionManoObra = it)) } }
                item { FondoInputField("Inversión Transporte", state.inversionTransporte) { onStateChange(state.copy(inversionTransporte = it)) } }
                item { FondoInputField("Inversión Equipos", state.inversionEquipos) { onStateChange(state.copy(inversionEquipos = it)) } }
                item { FondoInputField("Inversión Otros", state.inversionOtros) { onStateChange(state.copy(inversionOtros = it)) } }
                item {
                    OutlinedTextField(
                        value = state.notas,
                        onValueChange = { onStateChange(state.copy(notas = it)) },
                        label = { Text("Notas") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text(if (isEditing) "Actualizar" else "Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun FondoInputField(label: String, value: Double, onValueChange: (Double) -> Unit) {
    OutlinedTextField(
        value = if (value > 0) value.toString() else "",
        onValueChange = { it.toDoubleOrNull()?.let { v -> onValueChange(v) } },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        prefix = { Text("$ ") }
    )
}
