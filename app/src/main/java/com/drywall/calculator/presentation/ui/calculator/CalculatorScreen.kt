package com.drywall.calculator.presentation.ui.calculator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.material3.AlertDialog
import com.drywall.calculator.data.repository.CalculationRepository
import com.drywall.calculator.domain.calculator.MaterialAdvisoryEngine
import com.drywall.calculator.presentation.ui.components.ValidatedTextField
import com.drywall.calculator.presentation.ui.pdf.PdfViewModel
import com.drywall.calculator.utils.NumberFormatter
import com.drywall.calculator.utils.PdfUtils
import com.drywall.calculator.utils.PdfGenerator
import java.util.*
import android.net.Uri
import androidx.compose.ui.text.style.TextAlign

private val gson = com.google.gson.Gson()

data class LaborTypeInfo(
    val typeKey: String,
    val label: String,
    val m2: Double,
    val price: Double,
    val subtotal: Double
)

data class PendingPdfData(
    val invoiceItems: List<PdfGenerator.InvoiceItem>,
    val grandTotal: Double,
    val specialData: String?
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    navController: NavController, 
    viewModel: CalculatorViewModel = hiltViewModel(),
    pdfViewModel: PdfViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsState()
    val measurementCatalog by viewModel.measurements.collectAsState()
    val selectedMeasurement by viewModel.selectedMeasurement.collectAsState()
    val allMaterials by viewModel.allMaterials.collectAsState()
    val config by viewModel.config.collectAsState()
    val precision = config?.decimalPrecision ?: 4
    val selectedProject by viewModel.selectedProject.collectAsState()
    val constructionTypes = MaterialAdvisoryEngine.getAllConstructionTypes()
    val selectedType by viewModel.constructionType.collectAsState()
    
    val unitSystem = selectedMeasurement?.unitSystem ?: measurementCatalog.firstOrNull()?.unitSystem ?: "metric"
    val unitLabel = if (unitSystem == "metric") "(m)" else "(ft)"
    val areaLabel = if (unitSystem == "metric") "m²" else "ft²"

    val height by viewModel.height.collectAsState()
    val length by viewModel.length.collectAsState()
    val width by viewModel.width.collectAsState()
    val area by viewModel.areaM2.collectAsState()
    
    val displayArea = if (unitSystem == "imperial") area * 10.7639 else area
    val purchaseOrder by viewModel.purchaseOrder.collectAsState()
    val requirements by viewModel.requirements.collectAsState()
    val advisory by viewModel.advisoryText.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    
    val specialParts by viewModel.specialParts.collectAsState()
    val specialRate by viewModel.specialRate.collectAsState()
    val specialCurrency by viewModel.specialCurrency.collectAsState()
    
    val profile by pdfViewModel.companyProfile.collectAsState()
    val generatedPdf by pdfViewModel.pdfFile.collectAsState(initial = null)
    var lastGeneratedDocType by remember { mutableStateOf<PdfGenerator.DocType?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    var expandedProject by remember { mutableStateOf(false) }
    var expandedCurrency by remember { mutableStateOf(false) }
    
    var showAddPartDialog by remember { mutableStateOf(false) }
    var continueFromLast by remember { mutableStateOf(false) }
    var blockIndexToEdit by remember { mutableStateOf<Int?>(null) }
    var showDeletePartConfirmation by remember { mutableStateOf<ConstructionBlock?>(null) }
    var showLogoAlert by remember { mutableStateOf(false) }
    var materialToProcess by remember { mutableStateOf<CalculationRepository.MaterialRequirement?>(null) }
    var inputQuantity by remember { mutableStateOf("") }

    var showCreateMaterialDialog by remember { mutableStateOf<String?>(null) }
    var newMaterialUnit by remember { mutableStateOf("unidad") }
    
    var expandedMeasurements by remember { mutableStateOf(false) }

    var showCostDialog by remember { mutableStateOf(false) }
    var pendingDocType by remember { mutableStateOf<PdfGenerator.DocType?>(null) }
    var applyTaxes by remember { mutableStateOf(true) }
    
    var showPageSizeDialog by remember { mutableStateOf(false) }
    var selectedPageSize by remember { mutableStateOf(PdfGenerator.PageSize.A4) }
    var pendingPdfData by remember { mutableStateOf<PendingPdfData?>(null) }

    val isProcessing by viewModel.isProcessing.collectAsState()
    val pendingImportBlocks by viewModel.pendingImportBlocks.collectAsState()
    var quotationViewed by remember { mutableStateOf(false) }
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.importFromPdf(context, it) }
    }

    LaunchedEffect(Unit) {
        viewModel.uiMessage.collect { snackbarHostState.showSnackbar(it) }
    }

    val laborPrices by viewModel.laborPrices.collectAsState()
    val taxSetting by viewModel.taxSetting.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (requirements.isNotEmpty() || purchaseOrder.isNotEmpty() || (selectedType == "Especial (Multi-Partes)" && area > 0)) {
                Surface(tonalElevation = 8.dp, shadowElevation = 16.dp) {
                    Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DocumentButton(
                                label = "Cotización",
                                enabled = requirements.isNotEmpty() || area > 0,
                                onClick = {
                                    if (profile?.logoUri == null || profile?.businessName.isNullOrBlank()) {
                                        showLogoAlert = true
                                    } else {
                                        pendingDocType = PdfGenerator.DocType.QUOTATION
                                        showCostDialog = true
                                    }
                                },
                                showActions = lastGeneratedDocType == PdfGenerator.DocType.QUOTATION && generatedPdf != null,
                                generatedPdf = generatedPdf,
                                onActionConsumed = { lastGeneratedDocType = null },
                                onPdfViewed = { quotationViewed = true },
                                context = context,
                                modifier = Modifier.weight(1f)
                            )
                            DocumentButton(
                                label = "Factura Final",
                                enabled = quotationViewed && ((requirements.isNotEmpty() && purchaseOrder.isEmpty()) || (selectedType == "Especial (Multi-Partes)" && area > 0)),
                                onClick = {
                                    if (profile?.logoUri == null || profile?.businessName.isNullOrBlank()) {
                                        showLogoAlert = true
                                    } else {
                                        pendingDocType = PdfGenerator.DocType.FINAL_INVOICE
                                        showCostDialog = true
                                    }
                                },
                                showActions = lastGeneratedDocType == PdfGenerator.DocType.FINAL_INVOICE && generatedPdf != null,
                                generatedPdf = generatedPdf,
                                onActionConsumed = { lastGeneratedDocType = null },
                                context = context,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        // Handle back press with unsaved changes
        var showExitConfirmation by remember { mutableStateOf(false) }
        androidx.activity.compose.BackHandler(enabled = hasUnsavedChanges) {
            showExitConfirmation = true
        }

        if (showExitConfirmation) {
            AlertDialog(
                onDismissRequest = { showExitConfirmation = false },
                title = { Text("Cambios sin Guardar") },
                text = { Text("Tiene cambios pendientes en el desglose de áreas. ¿Desea guardarlos antes de salir?") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.saveAllChanges()
                        showExitConfirmation = false
                        navController.popBackStack()
                    }) { Text("Guardar y Salir") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showExitConfirmation = false
                        navController.popBackStack()
                    }) { Text("Descartar") }
                }
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Se elimina el Header Fijo redundante

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                
                item {
                    Text("1. Seleccionar Proyecto:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ExposedDropdownMenuBox(expanded = expandedProject, onExpandedChange = { expandedProject = !expandedProject }) {
                        OutlinedTextField(
                            value = selectedProject?.name ?: "Seleccione una obra", onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProject) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable), shape = MaterialTheme.shapes.medium
                        )
                        ExposedDropdownMenu(expanded = expandedProject, onDismissRequest = { expandedProject = false }) {
                            projects.forEach { project ->
                                DropdownMenuItem(text = { Text(project.name) }, onClick = { viewModel.setProject(project); expandedProject = false; quotationViewed = false })
                            }
                        }
                    }
                }

                item {
                    Text("2. Tipo de Construcción:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        constructionTypes.chunked(2).forEach { rowTypes ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowTypes.forEach { type ->
                                    FilterChip(
                                        selected = selectedType == type,
                                        onClick = { viewModel.setConstructionType(type) },
                                        label = { Text(type, modifier = Modifier.padding(vertical = 4.dp)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowTypes.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (selectedType == "Especial (Multi-Partes)") {
                    item {
                        Text("3. Desglose de Áreas y Bloques:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                specialParts.forEachIndexed { index, part ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            val blockLabel = if (part.name.isNotBlank()) "Bloque #${index + 1}: ${part.name}" else "Bloque #${index + 1}"
                                            Text(blockLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text("Área: ${com.drywall.calculator.utils.NumberFormatter.format(part.totalArea, precision)} $areaLabel (${part.segments.size} seg.)", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Row {
                                            IconButton(onClick = { blockIndexToEdit = index }) {
                                                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { showDeletePartConfirmation = part }) {
                                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp).alpha(0.2f))
                                }

                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            if (profile?.logoUri == null || profile?.businessName.isNullOrBlank()) {
                                                showLogoAlert = true
                                            } else {
                                                selectedProject?.let { p ->
                                                    pdfViewModel.generateTechnicalReport(
                                                        context = context,
                                                        projectName = p.name,
                                                        clientName = p.clientName,
                                                        totalM2 = area,
                                                        blocks = specialParts
                                                    )
                                                    lastGeneratedDocType = PdfGenerator.DocType.MEASUREMENTS_REPORT
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = specialParts.isNotEmpty()
                                    ) {
                                        Icon(Icons.Default.Output, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Exportar Reporte Técnico (IA)")
                                    }

                                    if (lastGeneratedDocType == PdfGenerator.DocType.MEASUREMENTS_REPORT && generatedPdf != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { 
                                                    PdfUtils.viewPdf(context, generatedPdf!!)
                                                    lastGeneratedDocType = null 
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                            ) {
                                                Icon(Icons.Default.Visibility, null)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Ver Reporte")
                                            }
                                            Button(
                                                onClick = { 
                                                    PdfUtils.sharePdf(context, generatedPdf!!)
                                                    lastGeneratedDocType = null
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                            ) {
                                                Icon(Icons.Default.Share, null)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Compartir")
                                            }
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { importLauncher.launch("application/pdf") },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    enabled = !isProcessing
                                ) {
                                    if (isProcessing) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.FileOpen, null)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text("Importar desde PDF")
                                }

                                Button(
                                    onClick = { 
                                        showAddPartDialog = true
                                        continueFromLast = specialParts.isNotEmpty() 
                                    }, 
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Icon(Icons.Default.Add, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (specialParts.isEmpty()) "Agregar Nuevo Bloque/Segmento" else "Agregar Otro Bloque")
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text("3. Dimensiones:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ValidatedTextField(
                                value = length,
                                onValueChange = { viewModel.setLength(it) },
                                label = "Largo $unitLabel",
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                trailingIcon = {
                                    if (length.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setLength("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Borrar Largo", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            )
                            ValidatedTextField(
                                value = width,
                                onValueChange = { viewModel.setWidth(it) },
                                label = "Ancho $unitLabel",
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                enabled = !selectedType.contains("Tabique", true),
                                trailingIcon = {
                                    if (width.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setWidth("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Borrar Ancho", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            )
                            ValidatedTextField(
                                value = height,
                                onValueChange = { viewModel.setHeight(it) },
                                label = "Altura $unitLabel",
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                trailingIcon = {
                                    if (height.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setHeight("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Borrar Altura", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    Text("4. Tarifa de Cotización:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValidatedTextField(
                            value = specialRate, onValueChange = { viewModel.setSpecialRate(it) },
                            label = "Precio por $areaLabel", modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        ExposedDropdownMenuBox(expanded = expandedCurrency, onExpandedChange = { expandedCurrency = !expandedCurrency }, modifier = Modifier.weight(0.6f)) {
                            OutlinedTextField(
                                value = specialCurrency, onValueChange = {}, readOnly = true, label = { Text("Moneda") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCurrency) },
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(expanded = expandedCurrency, onDismissRequest = { expandedCurrency = false }) {
                                listOf("USD", "EUR", "MLC", "CUP").forEach { curr ->
                                    DropdownMenuItem(text = { Text(curr) }, onClick = { viewModel.setSpecialCurrency(curr); expandedCurrency = false })
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.saveAllChanges() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = hasUnsavedChanges,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasUnsavedChanges) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Guardar Todo (Base de Datos)")
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                            Text("Área Total: ${com.drywall.calculator.utils.NumberFormatter.format(displayArea, precision)} $areaLabel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                            
                            val rate = specialRate.toDoubleOrNull() ?: 0.0
                            Text("Cotización: $specialCurrency ${com.drywall.calculator.utils.NumberFormatter.format(area * rate, precision)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.calculateMaterials() }, modifier = Modifier.weight(1f)) { Text("Calcular") }
                                Button(
                                    onClick = { viewModel.addMeasurementsToProject() }, 
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) { Text("Asignar Medidas") }
                            }
                        }
                    }
                }

                if (requirements.isNotEmpty()) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Asignación y Cotización:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { viewModel.assignMaterialsToProject() }) {
                                Icon(Icons.Default.Inventory, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Asignar Todo", fontWeight = FontWeight.Bold)
                            }
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                requirements.forEach { req ->
                                    val locale = LocalConfiguration.current.locales[0]
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(req.materialName, style = MaterialTheme.typography.bodyMedium)
                                            if (!req.isInCatalog) Text("No está en Catálogo", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                            else if (req.availableStock < req.totalQuantity) Text("Faltan: ${String.format(locale, "%.2f", req.totalQuantity - req.availableStock)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                            else Text("Stock OK", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                        }
                                        if (!req.isInCatalog) {
                                            TextButton(onClick = { 
                                                materialToProcess = req
                                                inputQuantity = String.format(locale, "%.2f", req.totalQuantity)
                                            }) { Text("Agregar") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                item { if (advisory.isNotBlank()) Card(modifier = Modifier.fillMaxWidth()) { Text(advisory, modifier = Modifier.padding(16.dp)) } }
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }

    if (showAddPartDialog || blockIndexToEdit != null) {
        val editingIndex = blockIndexToEdit
        
        // Temporarily hold blocks being edited in the dialog
        var tempBlocks by remember(editingIndex, showAddPartDialog) { 
            mutableStateOf<List<ConstructionBlock>>(
                if (editingIndex != null) specialParts
                else if (continueFromLast) specialParts + ConstructionBlock(name = "")
                else if (specialParts.isNotEmpty() && !continueFromLast) specialParts
                else listOf(ConstructionBlock(name = ""))
            )
        }
        var currentBlockIdx by remember(editingIndex, showAddPartDialog) { 
            mutableIntStateOf(
                if (editingIndex != null) editingIndex 
                else if (continueFromLast) specialParts.size 
                else 0
            ) 
        }
        var currentSegmentIdx by remember(editingIndex, showAddPartDialog) { mutableIntStateOf(0) }
        
        // Resolve current data pointers
        val block = tempBlocks.getOrNull(currentBlockIdx) ?: ConstructionBlock(name = "")
        val segments = if (block.segments.isEmpty()) listOf(ConstructionSegment(length = 0.0, width = 0.0, repetitions = 1)) else block.segments
        val segment = if (currentSegmentIdx < segments.size) segments[currentSegmentIdx] else ConstructionSegment(length = 0.0, width = 0.0, repetitions = 1)

        // Local input states
        var partName by remember(currentBlockIdx, currentSegmentIdx) { mutableStateOf(segment.name) }
        var pL by remember(currentBlockIdx, currentSegmentIdx) { mutableStateOf(if (segment.length > 0) segment.length.toString() else "") }
        var pW by remember(currentBlockIdx, currentSegmentIdx) { mutableStateOf(if (segment.width > 0) segment.width.toString() else "") }
        var pQ by remember(currentBlockIdx, currentSegmentIdx) { mutableStateOf(segment.repetitions.toString()) }

        var submitted by remember { mutableStateOf(false) }
        var showAlertDataMissing by remember { mutableStateOf(false) }

        val materialExists = remember(partName, allMaterials) {
            allMaterials.any { it.name.equals(partName, ignoreCase = true) }
        }

        val filteredMaterials = remember(partName) {
            if (partName.isBlank()) emptyList()
            else allMaterials.filter { it.name.contains(partName, ignoreCase = true) }
        }

        val currentSubtotal = remember(pL, pW, pQ) {
            (pL.toDoubleOrNull() ?: 0.0) * (pW.toDoubleOrNull() ?: 0.0) * (pQ.toIntOrNull() ?: 1)
        }

        val currentBlockArea = remember(block, currentSegmentIdx, pL, pW, pQ) {
            val otherSegmentsInBlock = block.segments.filterIndexed { idx, _ -> idx != currentSegmentIdx }.sumOf { it.subtotal }
            otherSegmentsInBlock + currentSubtotal
        }
        
        val totalGeneralArea = remember(tempBlocks, pL, pW, pQ) {
            // Sum all other segments + current input
            val otherSegmentsArea = tempBlocks.sumOf { b ->
                b.segments.filterIndexed { bIdx, s -> 
                    // Filter out the segment being edited in the current block
                    !(b.id == block.id && bIdx == currentSegmentIdx)
                }.sumOf { it.subtotal }
            }
            otherSegmentsArea + currentSubtotal
        }

        fun validateFields(): Boolean {
            return partName.isNotBlank() && 
                   materialExists &&
                   (pL.toDoubleOrNull() ?: 0.0) > 0 && 
                   (pW.toDoubleOrNull() ?: 0.0) > 0 && 
                   (pQ.toIntOrNull() ?: 0) > 0
        }

        fun saveCurrentToTemp(): List<ConstructionBlock>? {
            submitted = true
            if (!validateFields()) {
                showAlertDataMissing = true
                return null
            }
            
            val updatedSegment = ConstructionSegment(
                id = segment.id,
                name = partName,
                length = pL.toDoubleOrNull() ?: 0.0,
                width = pW.toDoubleOrNull() ?: 0.0,
                repetitions = pQ.toIntOrNull() ?: 1
            )
            val updatedSegments = segments.toMutableList()
            if (currentSegmentIdx < updatedSegments.size) {
                updatedSegments[currentSegmentIdx] = updatedSegment
            } else {
                updatedSegments.add(updatedSegment)
            }
            
            val updatedBlock = block.copy(name = "", segments = updatedSegments) // Block name optional, segments hold names
            val updatedBlocks = tempBlocks.toMutableList()
            updatedBlocks[currentBlockIdx] = updatedBlock
            tempBlocks = updatedBlocks
            
            // Auto-save: update the ViewModel state immediately
            viewModel.updateSpecialBlock(updatedBlock)

            submitted = false // Reset validation for next navigation
            return updatedBlocks
        }

        val confirmButtonLabel = remember(partName, editingIndex, currentBlockIdx, currentSegmentIdx) {
            // Si la descripción está vacía, estamos ante un registro nuevo dentro del flujo
            if (partName.isBlank()) "Guardar"
            // Si venimos de editar pero hemos navegado a un bloque/segmento que no existía originalmente
            else if (editingIndex != null) {
                val blockExists = specialParts.any { it.id == tempBlocks.getOrNull(currentBlockIdx)?.id }
                if (blockExists) "Actualizar" else "Guardar"
            }
            else "Guardar"
        }

        AlertDialog(
            onDismissRequest = { 
                showAddPartDialog = false
                continueFromLast = false
                blockIndexToEdit = null
            },
            title = { Text("Detalles del Bloque #${currentBlockIdx + 1}") },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column {
                        ValidatedTextField(
                            value = partName, 
                            onValueChange = { partName = it }, 
                            label = "Descripción",
                            isError = submitted && (partName.isBlank() || !materialExists),
                            errorMessage = if (partName.isBlank()) "Obligatorio" else "No existe",
                            trailingIcon = if (!materialExists && partName.isNotBlank()) {
                                {
                                    IconButton(onClick = { 
                                        newMaterialUnit = "unidad"
                                        showCreateMaterialDialog = partName 
                                    }) {
                                        Icon(Icons.Default.AddCircle, contentDescription = "Agregar", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } else null
                        )
                        if (filteredMaterials.isNotEmpty() && !filteredMaterials.any { it.name.equals(partName, true) }) {
                            Card(modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).padding(top = 2.dp)) {
                                val suggestions = filteredMaterials.sortedBy { it.name.lowercase() }
                                val locale = LocalConfiguration.current.locales[0]
                                LazyColumn {
                                    items(suggestions) { mat ->
                                        DropdownMenuItem(
                                            text = { Text("${mat.name} (${String.format(locale, "%.2f", mat.quantity)} ${mat.unitType})") },
                                            onClick = { partName = mat.name }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.alpha(0.3f))
                    Text("Segmento #${currentSegmentIdx + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ValidatedTextField(
                            value = pL, onValueChange = { pL = it }, label = "Largo", modifier = Modifier.weight(1f), 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = submitted && (pL.toDoubleOrNull() ?: 0.0) <= 0
                        )
                        ValidatedTextField(
                            value = pW, onValueChange = { pW = it }, label = "Ancho", modifier = Modifier.weight(1f), 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = submitted && (pW.toDoubleOrNull() ?: 0.0) <= 0
                        )
                        ValidatedTextField(
                            value = pQ, onValueChange = { pQ = it }, label = "Rep.", modifier = Modifier.weight(0.8f), 
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = submitted && (pQ.toIntOrNull() ?: 0) <= 0
                        )
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SUBTOTAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("${com.drywall.calculator.utils.NumberFormatter.format(currentSubtotal, precision)} $areaLabel", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("BLOQUE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("${com.drywall.calculator.utils.NumberFormatter.format(currentBlockArea, precision)} $areaLabel", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TOTAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("${com.drywall.calculator.utils.NumberFormatter.format(totalGeneralArea, precision)} $areaLabel", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    if (showAlertDataMissing) {
                        Text(
                            text = if (!materialExists && partName.isNotBlank()) "⚠️ Debe agregar el material al catálogo primero" 
                                   else "⚠️ Faltan datos por llenar o valores son 0",
                            color = MaterialTheme.colorScheme.error, 
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // --- TRES ESTRUCTURAS EN COLUMNAS ---
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        
                        // 1. Estructura de Segmento
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 2.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Segmento", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Button(
                                    onClick = { 
                                        val updated = saveCurrentToTemp()
                                        if (updated != null) {
                                            if (currentSegmentIdx < updated[currentBlockIdx].segments.size - 1) {
                                                currentSegmentIdx++
                                            } else {
                                                val updatedBlocks = updated.toMutableList()
                                                val currentB = updatedBlocks[currentBlockIdx]
                                                val newSegments = currentB.segments + ConstructionSegment(length = 0.0, width = 0.0, repetitions = 1)
                                                updatedBlocks[currentBlockIdx] = currentB.copy(segments = newSegments)
                                                tempBlocks = updatedBlocks
                                                currentSegmentIdx++
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("Siguiente", fontSize = 12.sp) }
                                OutlinedButton(
                                    onClick = { saveCurrentToTemp()?.let { currentSegmentIdx-- } },
                                    enabled = currentSegmentIdx > 0,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("Anterior", fontSize = 12.sp) }
                            }
                        }

                        // 2. Estructura de Bloque
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 2.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Bloque", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Button(
                                    onClick = { 
                                        val updated = saveCurrentToTemp()
                                        if (updated != null) {
                                            if (currentBlockIdx < updated.size - 1) {
                                                currentBlockIdx++
                                                currentSegmentIdx = 0
                                            } else {
                                                tempBlocks = updated + ConstructionBlock(name = "")
                                                currentBlockIdx++
                                                currentSegmentIdx = 0
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("Siguiente", fontSize = 12.sp) }
                                OutlinedButton(
                                    onClick = { saveCurrentToTemp()?.let { currentBlockIdx--; currentSegmentIdx = 0 } },
                                    enabled = currentBlockIdx > 0,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("Anterior", fontSize = 12.sp) }
                            }
                        }

                        // 3. Estructura de Acciones
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 2.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Acciones", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Button(
                                    onClick = {
                                        val latestBlocks = saveCurrentToTemp()
                                        if (latestBlocks != null) {
                                            val validBlocks = latestBlocks.filter { it.segments.any { s -> s.name.isNotBlank() && s.subtotal > 0 } }
                                            
                                            validBlocks.forEach { b ->
                                                if (editingIndex != null && currentBlockIdx == editingIndex) {
                                                    viewModel.updateSpecialBlock(b)
                                                } else {
                                                    val exists = viewModel.specialParts.value.any { it.id == b.id }
                                                    if (exists) viewModel.updateSpecialBlock(b)
                                                    else viewModel.addSpecialBlock(b)
                                                }
                                            }
                                            showAddPartDialog = false
                                            continueFromLast = false
                                            blockIndexToEdit = null
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(0.dp)
                                ) { 
                                    Text(confirmButtonLabel, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        showAddPartDialog = false
                                        continueFromLast = false
                                        blockIndexToEdit = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("Cancelar", fontSize = 12.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    showDeletePartConfirmation?.let { part ->
        val blockIndex = specialParts.indexOfFirst { it.id == part.id }
        val blockDisplayName = if (part.name.isNotBlank()) "Bloque #${blockIndex + 1} ('${part.name}')" else "Bloque #${blockIndex + 1}"
        AlertDialog(
            onDismissRequest = { showDeletePartConfirmation = null },
            title = { Text("Eliminar Bloque") },
            text = { Text("¿Desea eliminar el $blockDisplayName definitivamente?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeSpecialBlock(part.id)
                        showDeletePartConfirmation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePartConfirmation = null }) { Text("Cancelar") }
            }
        )
    }

    if (showLogoAlert) {
        AlertDialog(
            onDismissRequest = { showLogoAlert = false },
            title = { Text("Datos Faltantes") },
            text = { Text("Se debe configurar el Logotipo y Datos de Empresa para generar documentos.") },
            confirmButton = { Button(onClick = { showLogoAlert = false; navController.navigate("company") }) { Text("Configurar") } }
        )
    }

    showCreateMaterialDialog?.let { mName ->
        AlertDialog(
            onDismissRequest = { showCreateMaterialDialog = null },
            title = { Text("Nuevo Material") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Defina la unidad para '$mName':")
                    OutlinedTextField(
                        value = mName,
                        onValueChange = { showCreateMaterialDialog = it },
                        label = { Text("Nombre del Material") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    var expanded by remember { mutableStateOf(false) }
                    val units = listOf("unidad", "kg", "m²", "m", "litro", "rollo", "caja", "paquete")
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = newMaterialUnit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unidad de Medida") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            units.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = {
                                        newMaterialUnit = u
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addMaterialToCatalog(mName, 0.0, newMaterialUnit)
                    showCreateMaterialDialog = null
                }) { Text("Crear Material") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateMaterialDialog = null }) { Text("Cancelar") }
            }
        )
    }

    materialToProcess?.let { req ->
        AlertDialog(
            onDismissRequest = { materialToProcess = null },
            title = { Text("Agregar al Catálogo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Desea agregar '${req.materialName}' al catálogo de materiales?")
                    ValidatedTextField(
                        value = inputQuantity,
                        onValueChange = { inputQuantity = it },
                        label = "Stock Inicial",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val qty = inputQuantity.toDoubleOrNull() ?: 0.0
                    viewModel.addMaterialToCatalog(req.materialName, qty)
                    materialToProcess = null
                }) { Text("Añadir al Inventario") }
            },
            dismissButton = {
                TextButton(onClick = { materialToProcess = null }) { Text("Cancelar") }
            }
        )
    }

    if (pendingImportBlocks.isNotEmpty()) {
        ImportComparisonDialog(
            currentBlocks = specialParts,
            newBlocks = pendingImportBlocks,
            onConfirm = { blocks, mode -> 
                viewModel.applyImport(blocks, mode = mode)
            },
            onDismiss = { viewModel.cancelImport() }
        )
    }

    if (showCostDialog && pendingDocType != null) {
        // Track which types the user wants to apply
        var applyInterior by remember { mutableStateOf(true) }
        var applyExterior by remember { mutableStateOf(true) }
        var applyDropCeiling by remember { mutableStateOf(true) }
        var applyTileCeiling by remember { mutableStateOf(true) }
        
        // Calculate m² for each construction type based on project type and area
        val m2Result = remember(selectedType, area, specialParts) {
            when (selectedType) {
                "Tabique Interior" -> Triple(area, 0.0, 0.0)
                "Tabique Exterior" -> Triple(0.0, area, 0.0)
                "Cielo Raso Descolgado", "Cielo Raso de Baldosa" -> Triple(0.0, 0.0, area)
                "Especial (Multi-Partes)" -> {
                    var intM2 = 0.0
                    var extM2 = 0.0
                    var ceilM2 = 0.0
                    specialParts.forEach { block ->
                        block.segments.forEach { seg ->
                            val segArea = seg.length * seg.width * seg.repetitions
                            when {
                                block.name.contains("Interior", ignoreCase = true) || seg.name.contains("Interior", ignoreCase = true) -> intM2 += segArea
                                block.name.contains("Exterior", ignoreCase = true) || seg.name.contains("Exterior", ignoreCase = true) -> extM2 += segArea
                                block.name.contains("Cielo", ignoreCase = true) || seg.name.contains("Cielo", ignoreCase = true) -> ceilM2 += segArea
                                else -> intM2 += segArea // Default to interior
                            }
                        }
                    }
                    Triple(intM2, extM2, ceilM2)
                }
                else -> Triple(0.0, 0.0, 0.0)
            }
        }
        val interiorM2 = m2Result.first
        val exteriorM2 = m2Result.second
        val ceilingM2 = m2Result.third
        
        val totalM2 = interiorM2 + exteriorM2 + ceilingM2
        val lp = laborPrices
        
        // Calculate labor values for each aspect
        val interiorPrice = if (lp != null) lp.salePriceInterior() else 0.0
        val exteriorPrice = if (lp != null) lp.salePriceExterior() else 0.0
        val dropCeilingPrice = if (lp != null) lp.salePriceDropCeiling() else 0.0
        val tileCeilingPrice = if (lp != null) lp.salePriceTileCeiling() else 0.0
        
        val interiorSubtotal = interiorM2 * interiorPrice
        val exteriorSubtotal = exteriorM2 * exteriorPrice
        val dropCeilingSubtotal = ceilingM2 * dropCeilingPrice
        val tileCeilingSubtotal = ceilingM2 * tileCeilingPrice
        
        val laborTypes = listOf(
            LaborTypeInfo("Tabique Interior", "Interior", interiorM2, interiorPrice, interiorSubtotal),
            LaborTypeInfo("Tabique Exterior", "Exterior", exteriorM2, exteriorPrice, exteriorSubtotal),
            LaborTypeInfo("Cielo Raso Descolgado", "Descolgado", ceilingM2, dropCeilingPrice, dropCeilingSubtotal),
            LaborTypeInfo("Cielo Raso de Baldosa", "Baldosa", ceilingM2, tileCeilingPrice, tileCeilingSubtotal)
        )
        
        AlertDialog(
            onDismissRequest = { showCostDialog = false; pendingDocType = null },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Aplicar Costos al PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
            },
            text = {
                val scrollState = rememberScrollState()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                    Text("1. Seleccione los costos a incluir:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    // Total m² Summary Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("METROS CUADRADOS TOTAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text("${NumberFormatter.format(totalM2, precision)} m²", 
                                        fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                if (selectedType == "Especial (Multi-Partes)") {
                                    Text("Multi-Partes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                M2TypeCard(
                                    label = "Interior",
                                    m2 = interiorM2,
                                    price = interiorPrice,
                                    subtotal = interiorSubtotal,
                                    isEnabled = applyInterior,
                                    color = MaterialTheme.colorScheme.primary,
                                    specialCurrency = specialCurrency,
                                    precision = precision,
                                    onToggle = { applyInterior = !applyInterior }
                                )
                                M2TypeCard(
                                    label = "Exterior",
                                    m2 = exteriorM2,
                                    price = exteriorPrice,
                                    subtotal = exteriorSubtotal,
                                    isEnabled = applyExterior,
                                    color = MaterialTheme.colorScheme.secondary,
                                    specialCurrency = specialCurrency,
                                    precision = precision,
                                    onToggle = { applyExterior = !applyExterior }
                                )
                                M2TypeCard(
                                    label = "Cielo Raso Descolgado",
                                    m2 = ceilingM2,
                                    price = dropCeilingPrice,
                                    subtotal = dropCeilingSubtotal,
                                    isEnabled = applyDropCeiling,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    specialCurrency = specialCurrency,
                                    precision = precision,
                                    onToggle = { applyDropCeiling = !applyDropCeiling }
                                )
                                M2TypeCard(
                                    label = "Cielo Raso Baldosa",
                                    m2 = ceilingM2,
                                    price = tileCeilingPrice,
                                    subtotal = tileCeilingSubtotal,
                                    isEnabled = applyTileCeiling,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    specialCurrency = specialCurrency,
                                    precision = precision,
                                    onToggle = { applyTileCeiling = !applyTileCeiling }
                                )
                            }
                            // Labor subtotals summary
                            if ((applyInterior && interiorSubtotal > 0) || (applyExterior && exteriorSubtotal > 0) || (applyDropCeiling && dropCeilingSubtotal > 0) || (applyTileCeiling && tileCeilingSubtotal > 0)) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("SUBTOTALES MANO DE OBRA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    if (applyInterior && interiorSubtotal > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Tabique Interior", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$specialCurrency ${NumberFormatter.format(interiorSubtotal, precision)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    if (applyExterior && exteriorSubtotal > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Tabique Exterior", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$specialCurrency ${NumberFormatter.format(exteriorSubtotal, precision)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                    if (applyDropCeiling && dropCeilingSubtotal > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Cielo Raso Descolgado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$specialCurrency ${NumberFormatter.format(dropCeilingSubtotal, precision)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                        }
                                    }
                                    if (applyTileCeiling && tileCeilingSubtotal > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Cielo Raso Baldosa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$specialCurrency ${NumberFormatter.format(tileCeilingSubtotal, precision)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                    val totalLaborSubtotal = (if (applyInterior) interiorSubtotal else 0.0) + 
                                        (if (applyExterior) exteriorSubtotal else 0.0) + 
                                        (if (applyDropCeiling) dropCeilingSubtotal else 0.0) + 
                                        (if (applyTileCeiling) tileCeilingSubtotal else 0.0)
                                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Total Mano de Obra", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("$specialCurrency ${NumberFormatter.format(totalLaborSubtotal, precision)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    // Mano de Obra header
                    Text("MANO DE OBRA - DETALLE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
                
                    laborTypes.forEachIndexed { index, laborInfo ->
                        val isChecked = when (index) {
                            0 -> applyInterior
                            1 -> applyExterior
                            2 -> applyDropCeiling
                            3 -> applyTileCeiling
                            else -> true
                        }
                        val label = laborInfo.label
                        val price = laborInfo.price
                        val m2ForType = laborInfo.m2
                        val subtotal = laborInfo.subtotal
                        val onToggle: (Boolean) -> Unit = when (index) {
                            0 -> { v -> applyInterior = v }
                            1 -> { v -> applyExterior = v }
                            2 -> { v -> applyDropCeiling = v }
                            3 -> { v -> applyTileCeiling = v }
                            else -> { _ -> }
                        }
                        val icon = when (index) {
                            0 -> Icons.Default.Home
                            1 -> Icons.Default.Landscape
                            2 -> Icons.Default.Apartment
                            3 -> Icons.Default.GridOn
                            else -> Icons.Default.Architecture
                        }
                        val cardColor = when (index) {
                            0 -> MaterialTheme.colorScheme.primaryContainer
                            1 -> MaterialTheme.colorScheme.secondaryContainer
                            2 -> MaterialTheme.colorScheme.tertiaryContainer
                            3 -> MaterialTheme.colorScheme.outlineVariant
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .animateContentSize(),
                            colors = CardDefaults.cardColors(containerColor = if (isChecked) cardColor.copy(alpha = 0.5f) else cardColor.copy(alpha = 0.15f)),
                            border = BorderStroke(1.5.dp, if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Checkbox(checked = isChecked, onCheckedChange = onToggle,
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            if (price > 0) {
                                                Text("Precio: $specialCurrency ${NumberFormatter.format(price, precision)}/m²", 
                                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        if (price > 0 && m2ForType > 0) {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("$specialCurrency ${NumberFormatter.format(subtotal, precision)}", 
                                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Text("${NumberFormatter.format(m2ForType, precision)} m²", 
                                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    if (price > 0 && m2ForType > 0) {
                                        Text("${NumberFormatter.format(m2ForType, precision)} m² × $specialCurrency ${NumberFormatter.format(price, precision)}/m² = $specialCurrency ${NumberFormatter.format(subtotal, precision)}",
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else if (price > 0) {
                                        Text("$specialCurrency ${NumberFormatter.format(price, precision)}/m² (${NumberFormatter.format(m2ForType, precision)} m² disponibles)", 
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Text("Sin precio configurado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showCostDialog = false
                    selectedProject?.let { p ->
                        // Build items with only selected labor types
                        val baseItems = if (selectedType == "Especial (Multi-Partes)") {
                            val rate = specialRate.toDoubleOrNull() ?: 0.0
                            specialParts.flatMapIndexed { bIdx, block ->
                                val bName = if (block.name.isNotBlank()) block.name else "Bloque #${bIdx + 1}"
                                block.segments.map { seg ->
                                    PdfGenerator.InvoiceItem(
                                        name = seg.name, qtyPerM2 = seg.length * seg.width,
                                        totalQty = seg.subtotal, unitPrice = rate,
                                        totalPrice = seg.subtotal * rate, unitType = areaLabel, groupName = bName
                                    )
                                }
                            }
                        } else {
                            requirements.map { req -> PdfGenerator.InvoiceItem(req.materialName, req.quantityPerM2, req.totalQuantity, req.unitPrice, req.totalPrice) }
                        }
                        val mutableItems = baseItems.toMutableList()
                        var laborSubtotal = 0.0
                        if (laborPrices != null) {
                            val lp = laborPrices!!
                            val selectedTypes = mutableListOf<String>()
                            if (applyInterior) selectedTypes.add("Tabique Interior")
                            if (applyExterior) selectedTypes.add("Tabique Exterior")
                            if (applyDropCeiling) selectedTypes.add("Cielo Raso Descolgado")
                            if (applyTileCeiling) selectedTypes.add("Cielo Raso de Baldosa")
                            // Add labor items only for selected types with prices > 0
                            for (t in selectedTypes) {
                                val result = when (t) {
                                    "Tabique Interior" -> interiorM2 to lp.salePriceInterior()
                                    "Tabique Exterior" -> exteriorM2 to lp.salePriceExterior()
                                    "Cielo Raso Descolgado" -> ceilingM2 to lp.salePriceDropCeiling()
                                    "Cielo Raso de Baldosa" -> ceilingM2 to lp.salePriceTileCeiling()
                                    else -> 0.0 to 0.0
                                }
                                val m2ForType = result.first
                                val unitPrice = result.second
                                
                                if (unitPrice > 0 && m2ForType > 0) {
                                    val itemSubtotal = m2ForType * unitPrice
                                    laborSubtotal += itemSubtotal
                                    mutableItems.add(PdfGenerator.InvoiceItem(
                                        name = "Mano de Obra ($t)", qtyPerM2 = 1.0, totalQty = m2ForType,
                                        unitPrice = unitPrice, totalPrice = itemSubtotal, unitType = "m²"
                                    ))
                                }
                            }
                        }
                        val materialSubtotal = baseItems.sumOf { it.totalPrice }
                        val subtotal = materialSubtotal + laborSubtotal
                        val taxAmount = if (applyTaxes && taxSetting != null) subtotal * (taxSetting!!.percentage / 100.0) else 0.0
                        val grandTotal = subtotal + taxAmount
                        val specialData = if (selectedType == "Especial (Multi-Partes)") gson.toJson(specialParts) else null

                        pendingPdfData = PendingPdfData(mutableItems, grandTotal, specialData)
                    }
                    showPageSizeDialog = true
                }) { Text("Configurar Hoja y Generar") }
            },
            dismissButton = {
                TextButton(onClick = { showCostDialog = false; pendingDocType = null }) { Text("Cancelar") }
            }
        )
    }

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
                TextButton(onClick = {
                    showPageSizeDialog = false
                    selectedProject?.let { p ->
                        pendingPdfData?.let { data ->
                            when (pendingDocType) {
                                PdfGenerator.DocType.QUOTATION -> {
                                    pdfViewModel.generateQuotation(context, p.name, p.clientName, area, data.invoiceItems, data.grandTotal, constructionType = selectedType, specialData = data.specialData, pageSize = selectedPageSize)
                                    lastGeneratedDocType = PdfGenerator.DocType.QUOTATION
                                }
                                PdfGenerator.DocType.FINAL_INVOICE -> {
                                    pdfViewModel.generateFinalInvoice(context, p.name, p.clientName, area, data.invoiceItems, data.grandTotal, constructionType = selectedType, specialData = data.specialData, pageSize = selectedPageSize)
                                    lastGeneratedDocType = PdfGenerator.DocType.FINAL_INVOICE
                                }
                                else -> {}
                            }
                        }
                    }
                    pendingDocType = null
                    pendingPdfData = null
                }) { Text("Generar") }
            },
            dismissButton = {
                TextButton(onClick = { showPageSizeDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun ImportComparisonDialog(
    currentBlocks: List<ConstructionBlock>,
    newBlocks: List<ConstructionBlock>,
    onConfirm: (List<ConstructionBlock>, ImportMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.90f),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    @Suppress("DEPRECATION") Icon(Icons.Filled.CompareArrows, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Comparación de Importación", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "Compare los datos del PDF con los actuales antes de aplicar.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(newBlocks.size) { index ->
                    val newBlock = newBlocks[index]
                    val existingBlock = currentBlocks.find { it.name.equals(newBlock.name, true) }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (existingBlock != null) 
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, if (existingBlock != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (existingBlock != null) Icons.Default.SyncAlt else Icons.Default.AddCircle,
                                    null,
                                    tint = if (existingBlock != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Bloque: ${newBlock.name.ifBlank { "Sin Nombre" }}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (existingBlock != null) {
                                    Spacer(Modifier.weight(1f))
                                    Badge(containerColor = MaterialTheme.colorScheme.secondary) { Text("EXISTENTE") }
                                }
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            Text("Segmentos a Importar (${newBlock.segments.size}):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            
                            newBlock.segments.forEach { seg ->
                                val match = existingBlock?.segments?.find { it.name.equals(seg.name, true) }
                                val isDifferent = match != null && (match.length != seg.length || match.width != seg.width || match.repetitions != seg.repetitions)
                                
                                Row(
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isDifferent) Icons.Default.Warning else Icons.Default.Check,
                                        null,
                                        tint = if (isDifferent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Column {
                                        Text("${seg.name}: ${seg.length}x${seg.width} (x${seg.repetitions})", style = MaterialTheme.typography.labelSmall)
                                        if (isDifferent) {
                                            Text(
                                                "Actual: ${match.length}x${match.width} (x${match.repetitions})", 
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onConfirm(newBlocks, ImportMode.ADD_NEW) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar por Separado")
                }
                Button(
                    onClick = { onConfirm(newBlocks, ImportMode.MERGE) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Merge, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Combinar Existentes")
                }
                Button(
                    onClick = { onConfirm(newBlocks, ImportMode.OVERWRITE) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.History, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sobreescribir Todo")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar Importación")
            }
        }
    )
}

@Composable
fun M2TypeCard(
    label: String,
    m2: Double,
    price: Double,
    subtotal: Double,
    isEnabled: Boolean,
    onToggle: ((Boolean) -> Unit)? = null,
    color: androidx.compose.ui.graphics.Color,
    specialCurrency: String,
    precision: Int,
    modifier: Modifier = Modifier
) {
    val hasPrice = price > 0 && m2 > 0
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isEnabled) color.copy(alpha = 0.18f) else color.copy(alpha = 0.08f)),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, if (isEnabled) color.copy(alpha = 0.6f) else color.copy(alpha = 0.3f)),
        elevation = if (isEnabled) CardDefaults.cardElevation(defaultElevation = 4.dp) else CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = when (label) {
                    "Interior", "Tabique Interior" -> Icons.Default.Home
                    "Exterior", "Tabique Exterior" -> Icons.Default.Landscape
                    "Cielo Raso Descolgado", "Descolgado" -> Icons.Default.Apartment
                    "Cielo Raso Baldosa", "Baldosa" -> Icons.Default.GridOn
                    else -> Icons.Default.Architecture
                },
                contentDescription = null,
                tint = color.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.9f), fontWeight = FontWeight.Bold)
                Text(
                    "${com.drywall.calculator.utils.NumberFormatter.format(m2, precision)} m²",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isEnabled) color else color.copy(alpha = 0.5f)
                )
            }

            if (hasPrice) {
                Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                    Text(
                        "$specialCurrency ${com.drywall.calculator.utils.NumberFormatter.format(subtotal, precision)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "@ $specialCurrency ${com.drywall.calculator.utils.NumberFormatter.format(price, precision)}/m²",
                        style = MaterialTheme.typography.labelSmall,
                        color = color.copy(alpha = 0.7f),
                        textAlign = TextAlign.End
                    )
                }
            } else if (m2 > 0) {
                 Text(
                    "Sin precio",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
            
            if (onToggle != null) {
                Checkbox(
                    checked = isEnabled, 
                    onCheckedChange = { onToggle(!isEnabled) },
                    colors = CheckboxDefaults.colors(checkedColor = color)
                )
            }
        }
    }
}

@Composable
private fun DocumentButton(
    label: String, 
    enabled: Boolean, 
    onClick: () -> Unit, 
    showActions: Boolean, 
    generatedPdf: java.io.File?, 
    onActionConsumed: () -> Unit,
    onPdfViewed: (() -> Unit)? = null,
    context: android.content.Context, 
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onClick, modifier = Modifier.weight(1.2f), enabled = enabled, shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Default.Print, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(label, maxLines = 1)
            }
            if (showActions && generatedPdf != null) {
                IconButton(onClick = { PdfUtils.viewPdf(context, generatedPdf); onActionConsumed(); onPdfViewed?.invoke() }, modifier = Modifier.weight(0.4f), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Icon(Icons.Default.Visibility, null) }
                IconButton(onClick = { PdfUtils.sharePdf(context, generatedPdf); onActionConsumed() }, modifier = Modifier.weight(0.4f), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Icon(Icons.Default.Share, null) }
            } else {
                Spacer(modifier = Modifier.weight(0.8f))
            }
        }
    }
}
