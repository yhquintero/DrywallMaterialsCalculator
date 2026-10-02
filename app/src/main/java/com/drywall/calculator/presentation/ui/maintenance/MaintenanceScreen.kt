package com.drywall.calculator.presentation.ui.maintenance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.drywall.calculator.data.local.entity.Project

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    navController: NavController,
    viewModel: MaintenanceViewModel = hiltViewModel()
) {
    val projects by viewModel.projects.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var jsonText by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.uiMessage.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Consola de Mantenimiento IA") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (editingProject == null) {
                Text(
                    "ADVERTENCIA: Esta consola permite edición directa de la base de datos. Use con precaución.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(projects) { project ->
                        ListItem(
                            headlineContent = { Text(project.name) },
                            supportingContent = { Text("ID: ${project.id}") },
                            trailingContent = {
                                IconButton(onClick = { 
                                    editingProject = project
                                    jsonText = project.specialPartsJson
                                    nameText = project.name
                                }) {
                                    Icon(Icons.Default.Edit, "Editar Raw")
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }

                // Panel de Acciones Críticas
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Acciones de Recuperación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = { viewModel.performBackupRecovery(navController.context) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Restaurar desde Respaldo Seguro")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = { viewModel.forceReinitializeSecurity(navController.context) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Warning, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Reiniciar Seguridad / Licencia")
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text("Editando: ${editingProject!!.name}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("Nombre del Proyecto") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Special Parts JSON:", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { editingProject = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = {
                                viewModel.updateProjectRaw(editingProject!!.copy(
                                    name = nameText,
                                    specialPartsJson = jsonText
                                ))
                                editingProject = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Guardar RAW")
                        }
                    }
                }
            }
        }
    }
}
