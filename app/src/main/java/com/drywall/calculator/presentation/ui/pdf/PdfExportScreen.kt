package com.drywall.calculator.presentation.ui.pdf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.drywall.calculator.data.local.entity.Project
import com.drywall.calculator.utils.PdfGenerator

import com.drywall.calculator.utils.PdfUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExportScreen(navController: NavController, viewModel: PdfViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsState()
    val profile by viewModel.companyProfile.collectAsState()
    val pdfFile by viewModel.pdfFile.collectAsState(initial = null)
    
    var selectedProject by remember { mutableStateOf<Project?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var showLogoAlert by remember { mutableStateOf(false) }
    var selectedPageSize by remember { mutableStateOf(PdfGenerator.PageSize.A4) }

    if (showLogoAlert) {
        AlertDialog(
            onDismissRequest = { showLogoAlert = false },
            title = { Text("Datos Faltantes") },
            text = { Text("Se debe poner primeramente el Logotipo u los Datos Fundamentales de la Empresa en la sección 'Empresa' para generar documentos profesionales.") },
            confirmButton = {
                Button(onClick = { 
                    showLogoAlert = false
                    navController.navigate("company")
                }) { Text("Ir a Empresa") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoAlert = false }) { Text("Cerrar") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text("Exportar a PDF", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        var showPageSizeDialog by remember { mutableStateOf(false) }
        var pendingType by remember { mutableStateOf<PdfGenerator.DocType?>(null) }

        if (showPageSizeDialog) {
            AlertDialog(
                onDismissRequest = { showPageSizeDialog = false },
                title = { Text("Tamaño de hoja") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Seleccione el formato de papel para el documento:")
                        PdfGenerator.PageSize.entries.forEach { ps ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPageSize = ps }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ps.label)
                                if (selectedPageSize == ps) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showPageSizeDialog = false
                        if (profile?.logoUri == null || profile?.businessName.isNullOrBlank()) {
                            showLogoAlert = true
                        } else {
                            selectedProject?.let { p ->
                                pendingType?.let { type ->
                                    viewModel.generateDocumentForProject(context, p, type, selectedPageSize)
                                }
                            }
                        }
                    }) { Text("Generar") }
                },
                dismissButton = {
                    TextButton(onClick = { showPageSizeDialog = false }) { Text("Cancelar") }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedProject?.name ?: "Seleccione una obra",
                onValueChange = {},
                readOnly = true,
                label = { Text("Obra") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.name) },
                        onClick = {
                            selectedProject = project
                            expanded = false
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Documentos a Generar:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        val buttons = listOf(
            Triple("Cotización", PdfGenerator.DocType.QUOTATION, MaterialTheme.colorScheme.primary),
            Triple("Orden de Compra", PdfGenerator.DocType.PURCHASE_ORDER, MaterialTheme.colorScheme.secondary),
            Triple("Factura Final", PdfGenerator.DocType.FINAL_INVOICE, MaterialTheme.colorScheme.tertiary),
            Triple("Reporte de Medidas", PdfGenerator.DocType.MEASUREMENTS_REPORT, MaterialTheme.colorScheme.outline)
        )

        buttons.forEach { (label, type, color) ->
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedProject != null,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                onClick = {
                    pendingType = type
                    showPageSizeDialog = true
                }
            ) {
                Icon(Icons.Default.Print, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(label)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        pdfFile?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Documento generado exitosamente", style = MaterialTheme.typography.titleSmall)
                    Text(it.name, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { PdfUtils.viewPdf(context, it) }
                    ) {
                        Text("Ver Documento")
                    }
                }
            }
        }
    }
}
