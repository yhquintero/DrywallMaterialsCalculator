package com.drywall.calculator.presentation.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.drywall.calculator.presentation.ui.materials.MaterialsViewModel
import com.drywall.calculator.presentation.ui.pdf.PdfViewModel
import com.drywall.calculator.presentation.ui.projects.ProjectsViewModel
import com.drywall.calculator.utils.PdfGenerator
import com.drywall.calculator.utils.PdfUtils

@Composable
fun StatsScreen(
    projectsViewModel: ProjectsViewModel = hiltViewModel(),
    materialsViewModel: MaterialsViewModel = hiltViewModel(),
    pdfViewModel: PdfViewModel = hiltViewModel()
) {
    val projects by projectsViewModel.projects.collectAsState(initial = emptyList())
    val materials by materialsViewModel.materials.collectAsState(initial = emptyList())
    val profile by pdfViewModel.companyProfile.collectAsState()
    val generatedPdf by pdfViewModel.pdfFile.collectAsState(initial = null)
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    val totalMaterialValue = materials.sumOf { it.quantity * it.purchasePrice }
    val totalProjectArea = projects.sumOf { it.totalAreaM2 }

    var selectedUnitFilter by remember { mutableStateOf("Todos") }
    var selectedMaterialFilter by remember { mutableStateOf("Todos") }
    var selectedQtyFilter by remember { mutableStateOf("Todos") }
    var showUnitFilterMenu by remember { mutableStateOf(false) }
    var showMaterialFilterMenu by remember { mutableStateOf(false) }
    var showQtyFilterMenu by remember { mutableStateOf(false) }
    var pendingViewPdf by remember { mutableStateOf(false) }
    var showLogoAlert by remember { mutableStateOf(false) }
    var selectedPageSize by remember { mutableStateOf(PdfGenerator.PageSize.A4) }
    var showPageSizeDialog by remember { mutableStateOf(false) }

    val unitTypes = remember(materials) { materials.map { it.unitType }.distinct().sorted() }
    val materialNames = remember(materials) { materials.map { it.name }.distinct().sorted() }
    val qtyRanges = listOf("Todos", "0-10", "10-50", "50-100", "100+")
    
    val filteredMaterials = remember(materials, selectedUnitFilter, selectedMaterialFilter, selectedQtyFilter) {
        materials.filter { m ->
            (selectedUnitFilter == "Todos" || m.unitType == selectedUnitFilter) &&
            (selectedMaterialFilter == "Todos" || m.name == selectedMaterialFilter) &&
            (selectedQtyFilter == "Todos" || when (selectedQtyFilter) {
                "0-10" -> m.quantity in 0.0..10.0
                "10-50" -> m.quantity in 10.0..50.0
                "50-100" -> m.quantity in 50.0..100.0
                "100+" -> m.quantity > 100.0
                else -> true
            })
        }
    }

    LaunchedEffect(generatedPdf) {
        if (generatedPdf != null && pendingViewPdf) {
            pendingViewPdf = false
            PdfUtils.viewPdf(context, generatedPdf!!)
        }
    }

    if (showLogoAlert) {
        AlertDialog(
            onDismissRequest = { showLogoAlert = false },
            title = { Text("Datos Faltantes") },
            text = { Text("Se debe poner primeramente el Logotipo u los Datos Fundamentales de la Empresa en la sección 'Empresa' para generar reportes profesionales.") },
            confirmButton = {
                Button(onClick = {
                    showLogoAlert = false
                }) { Text("Cerrar") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Summary Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Inversión en Stock", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "$${String.format(locale, "%,.2f", totalMaterialValue)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Área Construida", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${String.format(locale, "%,.2f", totalProjectArea)} m²",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title + PDF Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Rendimiento por Categoría", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            FilledIconButton(
                onClick = {
                    if (profile?.logoUri == null || profile?.businessName.isNullOrBlank()) {
                        showLogoAlert = true
                    } else {
                        showPageSizeDialog = true
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Print, contentDescription = "Exportar a PDF", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }

        if (showPageSizeDialog) {
            AlertDialog(
                onDismissRequest = { showPageSizeDialog = false },
                title = { Text("Tamaño de hoja") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Seleccione el formato de papel para el reporte estadístico:")
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
                    TextButton(onClick = {
                        showPageSizeDialog = false
                        pendingViewPdf = true
                        val items = filteredMaterials.map { m ->
                            PdfGenerator.InvoiceItem(
                                name = m.name,
                                qtyPerM2 = 0.0,
                                totalQty = m.quantity,
                                unitPrice = m.purchasePrice,
                                totalPrice = m.quantity * m.purchasePrice,
                                unitType = m.unitType,
                                groupName = "Rendimiento por Categoría"
                            )
                        }
                        pdfViewModel.generateQuotation(
                            context,
                            "REPORTE DE ESTADÍSTICAS",
                            "Rendimiento por Categoría",
                            0.0,
                            items,
                            items.sumOf { it.totalPrice },
                            pageSize = selectedPageSize
                        )
                    }) { Text("Generar") }
                },
                dismissButton = {
                    TextButton(onClick = { showPageSizeDialog = false }) { Text("Cancelar") }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        // Table Header with filter buttons
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("No. Id.", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                // Materiales with filter
                Box(modifier = Modifier.weight(2f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Materiales", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(4.dp))
                        Box {
                            IconButton(onClick = { showMaterialFilterMenu = true }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.TrendingUp, contentDescription = "Filtrar", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = showMaterialFilterMenu, onDismissRequest = { showMaterialFilterMenu = false }) {
                                DropdownMenuItem(text = { Text("Todos") }, onClick = { selectedMaterialFilter = "Todos"; showMaterialFilterMenu = false })
                                materialNames.forEach { name ->
                                    DropdownMenuItem(text = { Text(name, maxLines = 1) }, onClick = { selectedMaterialFilter = name; showMaterialFilterMenu = false })
                                }
                            }
                        }
                        if (selectedMaterialFilter != "Todos") {
                            Text(" ($selectedMaterialFilter)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                // Cantidades with filter
                Box(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        Text("Cantidades", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(4.dp))
                        Box {
                            IconButton(onClick = { showQtyFilterMenu = true }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.TrendingUp, contentDescription = "Filtrar", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = showQtyFilterMenu, onDismissRequest = { showQtyFilterMenu = false }) {
                                qtyRanges.forEach { range ->
                                    DropdownMenuItem(text = { Text(range) }, onClick = { selectedQtyFilter = range; showQtyFilterMenu = false })
                                }
                            }
                        }
                    }
                }
                // Unidades with filter
                Box(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        Text("Unidades", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(4.dp))
                        Box {
                            IconButton(onClick = { showUnitFilterMenu = true }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.TrendingUp, contentDescription = "Filtrar", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = showUnitFilterMenu, onDismissRequest = { showUnitFilterMenu = false }) {
                                DropdownMenuItem(text = { Text("Todos") }, onClick = { selectedUnitFilter = "Todos"; showUnitFilterMenu = false })
                                unitTypes.forEach { ut ->
                                    DropdownMenuItem(text = { Text(ut) }, onClick = { selectedUnitFilter = ut; showUnitFilterMenu = false })
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Scrollable Table Content
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (filteredMaterials.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No hay materiales registrados", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                filteredMaterials.forEachIndexed { index, mat ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                modifier = Modifier.weight(0.6f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = mat.name,
                                modifier = Modifier.weight(2f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = String.format(locale, "%,.1f", mat.quantity),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.End,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = mat.unitType,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

