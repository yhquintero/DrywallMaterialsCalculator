package com.drywall.calculator.presentation.ui.projects

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.drywall.calculator.data.local.entity.Project
import com.drywall.calculator.presentation.ui.components.ScanType
import com.drywall.calculator.presentation.ui.components.ScannerDialog
import com.drywall.calculator.presentation.ui.components.ValidatedTextField
import com.drywall.calculator.presentation.ui.components.PermissionGate
import android.Manifest
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(viewModel: ProjectsViewModel = hiltViewModel()) {
    val projects by viewModel.projects.collectAsState()
    val allClients by viewModel.allClients.collectAsState()
    val clientsSearchResult by viewModel.clients.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Project?>(null) }
    var deletionBlockedByClient by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }

    var showScanner by remember { mutableStateOf(false) }
    var scanType by remember { mutableStateOf(ScanType.ID_FRONT) }
    var showPermissionGate by remember { mutableStateOf(false) }

    fun clearFields() {
        name = ""
        client = ""
        phone = ""
        email = ""
        area = ""
        type = ""
        nameError = null
        editingProject = null
    }

    LaunchedEffect(name) {
        if (name.isNotBlank()) {
            val exists = projects.any { it.name.trim().lowercase() == name.trim().lowercase() && it.id != editingProject?.id }
            nameError = if (exists) "Ya existe una obra con este nombre" else null
        } else {
            nameError = null
        }
    }

    LaunchedEffect(editingProject) {
        editingProject?.let {
            name = it.name
            client = it.clientName
            phone = it.clientPhone
            email = it.clientEmail
            area = String.format(java.util.Locale.US, "%.4f", it.totalAreaM2)
            type = it.constructionType
        }
    }

    if (showScanner) {
        ScannerDialog(
            scanType = scanType,
            onDismiss = { showScanner = false },
            onResult = { data ->
                if (data.name != null || data.ni != null) {
                    client = "${data.name ?: ""} ${data.surnames ?: ""}".trim()
                    if (client.isEmpty()) client = data.ni ?: ""
                }
                if (scanType == ScanType.ID_FRONT) {
                    scanType = ScanType.ID_BACK
                } else {
                    showScanner = false
                }
            }
        )
    }

    if (showPermissionGate) {
        PermissionGate(
            permission = Manifest.permission.CAMERA,
            rationale = "Para escanear los datos del cliente desde su carnet de identidad, necesitamos acceso a la cámara.",
            onPermissionGranted = { showScanner = true },
            onDismiss = { showPermissionGate = false }
        )
    }

    Scaffold(
        snackbarHost = { },
        bottomBar = {
            if (allClients.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(56.dp),
                        onClick = {
                            clearFields()
                            showDialog = true
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Architecture, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Añadir Nueva Obra", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
            ) {
                if (allClients.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No hay Clientes registrados. Debe registrar un cliente antes de crear una obra.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else if (projects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No hay Obras registradas. Use el botón inferior para empezar.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(projects) { project ->
                            ProjectCard(
                                project = project,
                                onEdit = {
                                    editingProject = project
                                    showDialog = true
                                },
                                onDelete = {
                                    val clientExists = allClients.any { it.name + " " + it.surnames == project.clientName || it.ni == project.clientName }
                                    if (clientExists) {
                                        deletionBlockedByClient = true
                                    } else {
                                        showDeleteConfirm = project
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (deletionBlockedByClient) {
        AlertDialog(
            onDismissRequest = { deletionBlockedByClient = false },
            title = { Text("Acción Protegida") },
            text = { Text("No se podrá eliminar la Obra si hay clientes asociados. Primero debe desvincular o eliminar la asociación con el cliente.") },
            confirmButton = {
                TextButton(onClick = { deletionBlockedByClient = false }) { Text("Entendido") }
            }
        )
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Eliminar Obra") },
            text = { Text("¿Desea eliminar la obra ${showDeleteConfirm!!.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProject(showDeleteConfirm!!)
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
            title = { Text(if (editingProject == null) "Nueva Obra" else "Editar Obra") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        ValidatedTextField(
                            value = name, 
                            onValueChange = { name = it }, 
                            label = "Nombre de la Obra", 
                            modifier = Modifier.fillMaxWidth(),
                            isError = nameError != null
                        )
                        if (nameError != null) {
                            Text(
                                text = nameError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                    
                    Column {
                        ValidatedTextField(
                            value = searchQuery,
                            onValueChange = { 
                                val clean = it.replace("\n", "").replace("\r", "")
                                viewModel.setSearchQuery(clean)
                                client = clean 
                            },
                            label = "Buscar Cliente (Nombre o CI)",
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )
                        if (clientsSearchResult.isNotEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp).padding(top = 4.dp)) {
                                LazyColumn {
                                    items(clientsSearchResult) { c ->
                                        DropdownMenuItem(
                                            text = { Text("${c.name} ${c.surnames} (${c.ni})") },
                                            onClick = {
                                                client = "${c.name} ${c.surnames}"
                                                phone = c.phone ?: ""
                                                email = c.email ?: ""
                                                viewModel.setSearchQuery(client)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ValidatedTextField(
                        value = phone,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '+' }) phone = it },
                        label = "Teléfono Cliente",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    ValidatedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email Cliente",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    ValidatedTextField(
                        value = area,
                        onValueChange = { 
                            val clean = it.replace("\n", "").replace("\r", "")
                            if (clean.isEmpty() || clean.toDoubleOrNull() != null) area = clean 
                        },
                        label = "Área m²",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    val constructionTypes = com.drywall.calculator.domain.calculator.MaterialAdvisoryEngine.getAllConstructionTypes()
                    var expandedType by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expandedType,
                        onExpandedChange = { expandedType = !expandedType }
                    ) {
                        OutlinedTextField(
                            value = type,
                            onValueChange = { type = it },
                            label = { Text("Tipo de Construcción") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) }
                        )
                        ExposedDropdownMenu(
                            expanded = expandedType,
                            onDismissRequest = { expandedType = false }
                        ) {
                            constructionTypes.forEach { constructionType ->
                                DropdownMenuItem(
                                    text = { Text(constructionType) },
                                    onClick = {
                                        type = constructionType
                                        expandedType = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val a = area.toDoubleOrNull() ?: 0.0
                        if (name.isNotBlank() && nameError == null) {
                            if (editingProject == null) {
                                viewModel.addProject(name, client, phone, email, a, type)
                            } else {
                                val updatedProj = editingProject!!.copy(
                                    name = name,
                                    clientName = client,
                                    clientPhone = phone,
                                    clientEmail = email,
                                    totalAreaM2 = a,
                                    constructionType = type
                                )
                                viewModel.updateProject(updatedProj)
                            }
                            showDialog = false
                            clearFields()
                        }
                    },
                    enabled = name.isNotBlank() && nameError == null
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false; clearFields() }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun ProjectCard(project: Project, onEdit: () -> Unit, onDelete: () -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Cliente: ${project.clientName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Área Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("${String.format(locale, "%.4f", project.totalAreaM2)} m²", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Tipo de Obra", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(project.constructionType, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
