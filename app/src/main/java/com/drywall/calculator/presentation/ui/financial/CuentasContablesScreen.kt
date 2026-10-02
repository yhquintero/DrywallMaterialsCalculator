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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.drywall.calculator.data.local.entity.CuentaContable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuentasContablesScreen(
    viewModel: FinancialReportsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val cuentas by viewModel.cuentas.collectAsState()
    val formState by viewModel.cuentaFormState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var cuentaToDelete by remember { mutableStateOf<CuentaContable?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catálogo de Cuentas") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                viewModel.updateCuentaForm(CuentaContableFormState())
                showAddDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Nueva Cuenta")
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
            val grouped = cuentas.groupBy { it.tipo }
            val tipos = listOf(
                CuentaContable.TIPO_ACTIVO to "ACTIVOS",
                CuentaContable.TIPO_PASIVO to "PASIVOS",
                CuentaContable.TIPO_PATRIMONIO to "PATRIMONIO",
                CuentaContable.TIPO_INGRESO to "INGRESOS",
                CuentaContable.TIPO_GASTO to "GASTOS"
            )

            tipos.forEach { (tipo, label) ->
                val cuentasDelTipo = grouped[tipo] ?: emptyList()
                if (cuentasDelTipo.isNotEmpty()) {
                    item {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(cuentasDelTipo.sortedBy { it.codigo }) { cuenta ->
                        CuentaItem(
                            cuenta = cuenta,
                            onEdit = {
                                viewModel.updateCuentaForm(CuentaContableFormState(
                                    id = cuenta.id, codigo = cuenta.codigo, nombre = cuenta.nombre,
                                    tipo = cuenta.tipo, subtipo = cuenta.subtipo,
                                    saldoDebe = cuenta.saldoDebe, saldoHaber = cuenta.saldoHaber,
                                    aft = cuenta.aft, depreciacionAcum = cuenta.depreciacionAcum,
                                    descripcion = cuenta.descripcion
                                ))
                                showAddDialog = true
                            },
                            onDelete = { cuentaToDelete = cuenta }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CuentaFormDialog(
            state = formState,
            onStateChange = { viewModel.updateCuentaForm(it) },
            onSave = {
                viewModel.saveCuenta()
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    cuentaToDelete?.let { cuenta ->
        AlertDialog(
            onDismissRequest = { cuentaToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Eliminar Cuenta") },
            text = {
                Text("¿Está seguro de eliminar la cuenta \"${cuenta.codigo} - ${cuenta.nombre}\"? Esta acción no se puede deshacer y afectará los reportes financieros.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCuenta(cuenta.id)
                        cuentaToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { cuentaToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun CuentaItem(
    cuenta: CuentaContable,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${cuenta.codigo} - ${cuenta.nombre}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Saldo: ${if (cuenta.saldoDebe > 0) "Debe $${cuenta.saldoDebe}" else "Haber $${cuenta.saldoHaber}"}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (cuenta.aft > 0 || cuenta.depreciacionAcum > 0) {
                    Text(
                        text = "AFT: $${cuenta.aft} | Dep. Acum: $${cuenta.depreciacionAcum}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuentaFormDialog(
    state: CuentaContableFormState,
    onStateChange: (CuentaContableFormState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.id.isEmpty()) "Nueva Cuenta" else "Editar Cuenta") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = state.codigo,
                        onValueChange = { onStateChange(state.copy(codigo = it)) },
                        label = { Text("Código") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.nombre,
                        onValueChange = { onStateChange(state.copy(nombre = it)) },
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = state.tipo,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf(CuentaContable.TIPO_ACTIVO, CuentaContable.TIPO_PASIVO, 
                                 CuentaContable.TIPO_PATRIMONIO, CuentaContable.TIPO_INGRESO, 
                                 CuentaContable.TIPO_GASTO).forEach { tipo ->
                                DropdownMenuItem(
                                    text = { Text(tipo) },
                                    onClick = { onStateChange(state.copy(tipo = tipo)); expanded = false }
                                )
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.saldoDebe.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { v -> onStateChange(state.copy(saldoDebe = v)) } },
                        label = { Text("Saldo Inicial Debe") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.saldoHaber.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { v -> onStateChange(state.copy(saldoHaber = v)) } },
                        label = { Text("Saldo Inicial Haber") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.aft.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { v -> onStateChange(state.copy(aft = v)) } },
                        label = { Text("Valor AFT (si aplica)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.depreciacionAcum.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { v -> onStateChange(state.copy(depreciacionAcum = v)) } },
                        label = { Text("Depreciación Acumulada (si aplica)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.descripcion,
                        onValueChange = { onStateChange(state.copy(descripcion = it)) },
                        label = { Text("Descripción / Nota") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
