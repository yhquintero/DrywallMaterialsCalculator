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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.drywall.calculator.data.local.entity.MovimientoContable
import com.drywall.calculator.presentation.ui.components.DateField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovimientosContablesScreen(
    viewModel: FinancialReportsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val movimientos by viewModel.movimientos.collectAsState()
    val cuentas by viewModel.cuentas.collectAsState()
    val formState by viewModel.movimientoFormState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var movimientoToDelete by remember { mutableStateOf<MovimientoContable?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registro de Movimientos") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                viewModel.updateMovimientoForm(MovimientoContableFormState())
                showAddDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Movimiento")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(movimientos.sortedByDescending { it.fecha }) { movimiento ->
                val cuenta = cuentas.find { it.id == movimiento.cuentaContableId }
                MovimientoItem(
                    movimiento = movimiento,
                    cuentaNombre = cuenta?.nombre ?: "Cuenta Desconocida",
                    onEdit = {
                        viewModel.updateMovimientoForm(MovimientoContableFormState(
                            id = movimiento.id,
                            cuentaContableId = movimiento.cuentaContableId,
                            fecha = movimiento.fecha,
                            descripcion = movimiento.descripcion,
                            montoDebe = movimiento.montoDebe,
                            montoHaber = movimiento.montoHaber,
                            referencia = movimiento.referencia
                        ))
                        showAddDialog = true
                    },
                    onDelete = { movimientoToDelete = movimiento }
                )
            }
        }
    }

    if (showAddDialog) {
        MovimientoFormDialog(
            state = formState,
            cuentas = cuentas,
            isEditing = formState.id.isNotBlank(),
            onStateChange = { viewModel.updateMovimientoForm(it) },
            onSave = {
                viewModel.saveMovimiento()
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    movimientoToDelete?.let { movimiento ->
        val cuenta = cuentas.find { it.id == movimiento.cuentaContableId }
        AlertDialog(
            onDismissRequest = { movimientoToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Eliminar Movimiento") },
            text = {
                Text("¿Está seguro de eliminar el movimiento de \"${cuenta?.nombre ?: "Desconocida"}\" por ${movimiento.descripcion}? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMovimiento(movimiento.id)
                        movimientoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { movimientoToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun MovimientoItem(
    movimiento: MovimientoContable,
    cuentaNombre: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = cuentaNombre, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(text = sdf.format(Date(movimiento.fecha)), style = MaterialTheme.typography.bodySmall)
            }
            Text(text = movimiento.descripcion, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (movimiento.montoDebe > 0) {
                    Text(text = "Debe: $${movimiento.montoDebe}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                if (movimiento.montoHaber > 0) {
                    if (movimiento.montoDebe > 0) Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Haber: $${movimiento.montoHaber}", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovimientoFormDialog(
    state: MovimientoContableFormState,
    cuentas: List<com.drywall.calculator.data.local.entity.CuentaContable>,
    isEditing: Boolean = false,
    onStateChange: (MovimientoContableFormState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Editar Movimiento" else "Registrar Movimiento") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    val selectedCuenta = cuentas.find { it.id == state.cuentaContableId }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCuenta?.nombre ?: "Seleccionar Cuenta",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cuenta") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            cuentas.sortedBy { it.codigo }.forEach { cuenta ->
                                DropdownMenuItem(
                                    text = { Text("${cuenta.codigo} - ${cuenta.nombre}") },
                                    onClick = { 
                                        onStateChange(state.copy(cuentaContableId = cuenta.id))
                                        expanded = false 
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    DateField(
                        value = sdf.format(Date(state.fecha)),
                        onValueChange = { 
                            try { sdf.parse(it)?.let { d -> onStateChange(state.copy(fecha = d.time)) } } catch (_: Exception) {}
                        },
                        label = "Fecha",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.descripcion,
                        onValueChange = { onStateChange(state.copy(descripcion = it)) },
                        label = { Text("Descripción") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.montoDebe.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { v -> onStateChange(state.copy(montoDebe = v)) } },
                        label = { Text("Monto Debe") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.montoHaber.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { v -> onStateChange(state.copy(montoHaber = v)) } },
                        label = { Text("Monto Haber") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.referencia,
                        onValueChange = { onStateChange(state.copy(referencia = it)) },
                        label = { Text("Referencia / Factura") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text(if (isEditing) "Actualizar" else "Registrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
