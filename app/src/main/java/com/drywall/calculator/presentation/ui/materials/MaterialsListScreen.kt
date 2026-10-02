package com.drywall.calculator.presentation.ui.materials

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Store
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
import com.drywall.calculator.data.local.entity.Material
import com.drywall.calculator.data.local.entity.PriceHistory
import com.drywall.calculator.presentation.ui.components.ValidatedTextField
import com.drywall.calculator.utils.NumberFormatter
import com.drywall.calculator.utils.PdfUtils
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

internal val ButtonBlue = Color(0xFF1976D2)
internal val ButtonRed = Color(0xFFD32F2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsListScreen(navController: NavController, viewModel: MaterialsViewModel = hiltViewModel()) {
    val materials by viewModel.filteredMaterials.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val customUnitTypes by viewModel.customUnitTypes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var showDialog by remember { mutableStateOf(false) }
    var editingMaterial by remember { mutableStateOf<Material?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Material?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showImportDefaultsDialog by remember { mutableStateOf(false) }
    var showImportMeasurementsDialog by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var profitPercent by remember { mutableStateOf("27.5") }
    var unitType by remember { mutableStateOf("unidad") }
    var providerId by remember { mutableStateOf<Int?>(null) }
    
    var showAddUnitDialog by remember { mutableStateOf(false) }
    var newUnitName by remember { mutableStateOf("") }
    var isQuadratic by remember { mutableStateOf(false) }
    
    var expandedUnit by remember { mutableStateOf(false) }
    var expandedProfit by remember { mutableStateOf(false) }
    var expandedProvider by remember { mutableStateOf(false) }
    var showAlert by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    var showAddPriceDialog by remember { mutableStateOf(false) }
    var newPriceDate by remember { mutableStateOf("") }
    var newPriceValue by remember { mutableStateOf("") }
    var priceDialogError by remember { mutableStateOf<String?>(null) }
    var editingPriceEntry by remember { mutableStateOf<PriceHistory?>(null) }
    var deletingPriceEntry by remember { mutableStateOf<PriceHistory?>(null) }

    val priceHistoryForEditing by remember(editingMaterial?.id) {
        editingMaterial?.let { viewModel.getPriceHistory(it.id) }
            ?: MutableStateFlow(emptyList())
    }.collectAsState(initial = emptyList())
    
    val unitTypes = listOf("caja", "ft²", "kg", "litro", "m²", "paquete", "rollo", "unidad")

    fun clearFields() {
        name = ""
        purchasePrice = ""
        profitPercent = "27.5"
        unitType = "unidad"
        providerId = null
        nameError = null
        editingMaterial = null
    }

    LaunchedEffect(name) {
        if (name.isNotBlank()) {
            val exists = materials.any { it.name.trim().lowercase() == name.trim().lowercase() && it.id != editingMaterial?.id }
            nameError = if (exists) "Ya existe un material con este nombre" else null
        } else {
            nameError = null
        }
    }

    LaunchedEffect(editingMaterial) {
        editingMaterial?.let {
            name = it.name
            purchasePrice = it.purchasePrice.toString()
            profitPercent = it.profitPercentage.toString()
            unitType = it.unitType
            providerId = it.providerId
        }
    }

    // El Precio Compra (deshabilitado) siempre es igual al último Id. de la lista
    // (última creación/actualización/modificación del precio).
    LaunchedEffect(editingMaterial?.id, priceHistoryForEditing) {
        if (editingMaterial != null) {
            val latest = priceHistoryForEditing.lastOrNull()
            if (latest != null) {
                purchasePrice = NumberFormatter.money(latest.price)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            // Header Fijo
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Botones con etiquetas encima
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top
                    ) {
                        HeaderActionButton(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Predet.",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { showImportDefaultsDialog = true }
                        )

                        HeaderActionButton(
                            icon = Icons.Default.Category,
                            label = "Catálogo",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { showImportMeasurementsDialog = true }
                        )

                        HeaderActionButton(
                            icon = Icons.Default.DeleteSweep,
                            label = "Limpiar",
                            containerColor = if (materials.isEmpty()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
                            contentColor = if (materials.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onErrorContainer,
                            onClick = { if (materials.isNotEmpty()) showDeleteAllConfirm = true },
                            enabled = materials.isNotEmpty()
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
                    // Barra de búsqueda debajo
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it.replace("\n", "").replace("\r", "")) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar material...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )}
                }
            }

            // Lista Desplazable ajustable
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                if (materials.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No hay materiales registrados.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(materials) { material ->
                        MaterialCard(
                            material = material,
                            providerName = providers.find { it.id == material.providerId }?.name,
                            onEdit = {
                                editingMaterial = material
                                showDialog = true
                            },
                            onDelete = { showDeleteConfirm = material }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Eliminar Material") },
            text = { Text("¿Desea eliminar el material ${showDeleteConfirm!!.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMaterial(showDeleteConfirm!!)
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

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("¿Eliminar TODO el Inventario?") },
            text = { Text("Esta acción borrará todos los materiales registrados permanentemente. ¿Desea continuar?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllMaterials()
                        showDeleteAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar Todo") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showImportDefaultsDialog) {
        ImportDefaultsDialog(
            seedMaterials = viewModel.getSeedMaterials(),
            onDismiss = { showImportDefaultsDialog = false },
            onImport = { selected ->
                viewModel.importMaterials(selected)
                showImportDefaultsDialog = false
            }
        )
    }

    if (showImportMeasurementsDialog) {
        ImportMeasurementsDialog(
            measurements = viewModel.catalogMeasures,
            onDismiss = { showImportMeasurementsDialog = false },
            onImport = { selected ->
                viewModel.importFromCatalog(selected)
                showImportMeasurementsDialog = false
            }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                clearFields()
            },
            title = { Text(if (editingMaterial == null) "Nuevo Material" else "Editar Material") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        ValidatedTextField(
                            value = name, 
                            onValueChange = { name = it }, 
                            label = "Nombre", 
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ValidatedTextField(
                            value = purchasePrice,
                            onValueChange = {
                                if (editingMaterial != null) return@ValidatedTextField
                                val clean = it.replace("\n", "").replace("\r", "")
                                if (clean.isEmpty() || clean.toDoubleOrNull() != null) purchasePrice = clean
                            },
                            label = "Precio Compra",
                            modifier = Modifier.weight(1f),
                            enabled = editingMaterial == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        if (editingMaterial != null) {
                            // Botón azul con icono de editar para gestionar precios de compra
                            FilledIconButton(
                                onClick = {
                                    priceDialogError = null
                                    editingPriceEntry = null
                                    newPriceValue = ""
                                    newPriceDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                                    showAddPriceDialog = true
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = ButtonBlue,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar precios de compra")
                            }
                            // Botón para exportar el historial de precios a PDF
                            FilledIconButton(
                                onClick = {
                                    val history = priceHistoryForEditing
                                    val file = generatePriceHistoryPdf(context, editingMaterial!!, history)
                                    PdfUtils.viewPdf(context, file)
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = ButtonBlue,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Exportar historial de precios")
                            }
                        }
                    }
                    
                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(
                        expanded = expandedProfit,
                        onExpandedChange = { expandedProfit = !expandedProfit }
                    ) {
                        OutlinedTextField(
                            value = profitPercent,
                            onValueChange = { newValue ->
                                val clean = newValue.replace(Regex("[^0-9.]"), "")
                                if (clean.isEmpty()) {
                                    profitPercent = ""
                                    return@OutlinedTextField
                                }
                                val num = clean.toDoubleOrNull()
                                if (num != null) {
                                    if (num > 30) return@OutlinedTextField
                                    profitPercent = clean
                                }
                            },
                            label = { Text("Margen % (25-30)") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProfit) }
                        )
                        ExposedDropdownMenu(
                            expanded = expandedProfit,
                            onDismissRequest = { expandedProfit = false }
                        ) {
                            listOf("25", "26", "27", "27.5", "28", "29", "30").forEach { selection ->
                                DropdownMenuItem(
                                    text = { Text(selection) },
                                    onClick = {
                                        profitPercent = selection
                                        expandedProfit = false
                                    }
                                )
                            }
                        }
                    }
                    
                    if (showAlert) {
                        AlertDialog(
                            onDismissRequest = { showAlert = false },
                            title = { Text("Valor Incompleto") },
                            text = { Text("Falta un dígito por seleccionar entre 25 o 30.") },
                            confirmButton = {
                                TextButton(onClick = { showAlert = false }) { Text("Aceptar") }
                            }
                        )
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = expandedUnit,
                            onExpandedChange = { expandedUnit = !expandedUnit },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = unitType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Unidad") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnit) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedUnit,
                                onDismissRequest = { expandedUnit = false }
                            ) {
                                val allUnits = (unitTypes + customUnitTypes.map { it.name }).distinct().sortedBy { it.lowercase() }
                                allUnits.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type) },
                                        onClick = {
                                            unitType = type
                                            expandedUnit = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        IconButton(
                            onClick = { showAddUnitDialog = true },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Añadir Unidad", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Selector de Proveedor
                    ExposedDropdownMenuBox(
                        expanded = expandedProvider,
                        onExpandedChange = { expandedProvider = !expandedProvider }
                    ) {
                        OutlinedTextField(
                            value = providers.find { it.id == providerId }?.name ?: "Seleccionar Proveedor",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Proveedor (Contrato)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expandedProvider,
                            onDismissRequest = { expandedProvider = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ninguno / General") },
                                onClick = {
                                    providerId = null
                                    expandedProvider = false
                                }
                            )
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.name) },
                                    onClick = {
                                        providerId = provider.id
                                        expandedProvider = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue, contentColor = Color.White),
                    onClick = {
                        val p = profitPercent.toDoubleOrNull() ?: 0.0
                        val minProfit = com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MIN_PROFIT
                        val maxProfit = com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MAX_PROFIT
                        if (profitPercent.length == 1 || (p > 0 && p < minProfit)) {
                            showAlert = true
                            return@Button
                        }
                        
                        val profit = p.coerceIn(minProfit, maxProfit)
                        if (editingMaterial == null) {
                            val price = purchasePrice.toDoubleOrNull() ?: 0.0
                            if (name.isNotBlank() && price > 0 && nameError == null) {
                                viewModel.addMaterial(name, price, profit, unitType, providerId)
                                showDialog = false
                                clearFields()
                            }
                        } else {
                            // Al editar solo se guardan los datos del material; el Precio de
                            // Compra se gestiona únicamente en "Precios de Compra".
                            if (name.isNotBlank() && nameError == null) {
                                val currentPrice = editingMaterial!!.purchasePrice
                                viewModel.updateMaterial(editingMaterial!!.copy(
                                    name = name,
                                    profitPercentage = profit,
                                    salePrice = currentPrice * (1 + profit / 100),
                                    unitType = unitType,
                                    providerId = providerId
                                ))
                                showDialog = false
                                clearFields()
                            }
                        }
                    },
                    enabled = name.isNotBlank() && nameError == null
                ) { Text("Guardar") }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showDialog = false
                        clearFields()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonRed, contentColor = Color.White)
                ) { Text("Cancelar") }
            }
        )
    }

    if (showAddUnitDialog) {
        AlertDialog(
            onDismissRequest = { showAddUnitDialog = false },
            title = { Text("Nueva Unidad de Medida") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isQuadratic = !isQuadratic }) {
                        Checkbox(checked = isQuadratic, onCheckedChange = { isQuadratic = it })
                        Text("Unidad cuadrática (ej. m²)", style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedTextField(
                        value = newUnitName,
                        onValueChange = { newUnitName = it },
                        label = { Text("Nombre de la Unidad") },
                        placeholder = { Text(if (isQuadratic) "m2, ft2..." else "galón, saco...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUnitName.isNotBlank()) {
                            val finalUnit = if (isQuadratic && !newUnitName.contains("²")) {
                                newUnitName + "²"
                            } else newUnitName
                            
                            viewModel.addCustomUnit(finalUnit)

                            unitType = finalUnit
                            showAddUnitDialog = false
                            newUnitName = ""
                            isQuadratic = false
                        }
                    }
                ) { Text("Agregar") }
            },
            dismissButton = {
                TextButton(onClick = { showAddUnitDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showAddPriceDialog && editingMaterial != null) {
        AlertDialog(
            onDismissRequest = {
                showAddPriceDialog = false
                editingPriceEntry = null
                newPriceValue = ""
                newPriceDate = ""
                priceDialogError = null
            },
            title = { Text(if (editingPriceEntry != null) "Editar Precio de Compra" else "Precios de Compra") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.drywall.calculator.presentation.ui.components.DateField(
                        value = newPriceDate,
                        onValueChange = {
                            newPriceDate = it
                            priceDialogError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isError = priceDialogError != null && parseDate(newPriceDate) == null,
                        allowFuture = false,
                        highlightedDates = priceHistoryForEditing.map { it.date }
                    )
                    OutlinedTextField(
                        value = newPriceValue,
                        onValueChange = {
                            val clean = it.replace("\n", "").replace("\r", "")
                            if (clean.isEmpty() || clean.toDoubleOrNull() != null) newPriceValue = clean
                            priceDialogError = null
                        },
                        label = { Text(if (editingPriceEntry != null) "Editar Precio" else "Nuevo Precio") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    if (priceDialogError != null) {
                        Text(priceDialogError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    if (priceHistoryForEditing.isNotEmpty()) {
                        Text(
                            "Precios anteriores",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        // El Flow ya llega ordenado por fecha desc y última actualización.
                        val sortedHistory = priceHistoryForEditing
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, borderColor)
                        ) {
                            // Encabezado
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PriceCell("Id.", Modifier.width(34.dp), borderColor, bold = true)
                                PriceCell("Fecha", Modifier.width(92.dp), borderColor, bold = true)
                                PriceCell("Precio", Modifier.weight(1f), borderColor, bold = true)
                                PriceCell("Acciones", Modifier.width(92.dp), borderColor, bold = true, lastColumn = true)
                            }
                            Divider(color = borderColor)
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                            ) {
                                itemsIndexed(sortedHistory, key = { _, e -> e.id }) { index, entry ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PriceCell("${index + 1}", Modifier.width(34.dp), borderColor)
                                        PriceCell(
                                            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(entry.date),
                                            Modifier.width(92.dp), borderColor
                                        )
                                        PriceCell(NumberFormatter.money(entry.price), Modifier.weight(1f), borderColor)
                                        Row(
                                            modifier = Modifier
                                                .width(92.dp)
                                                .height(IntrinsicSize.Min),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    editingPriceEntry = entry
                                                    newPriceValue = entry.price.toString()
                                                    newPriceDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(entry.date)
                                                    priceDialogError = null
                                                },
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = ButtonBlue, modifier = Modifier.size(18.dp))
                                            }
                                            val canDelete = sortedHistory.size > 1
                                            IconButton(
                                                onClick = { if (canDelete) deletingPriceEntry = entry },
                                                enabled = canDelete,
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Eliminar",
                                                    tint = if (canDelete) ButtonRed else ButtonRed.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                    Divider(color = borderColor)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val price = newPriceValue.toDoubleOrNull()
                        val date = parseDate(newPriceDate)
                        when {
                            date == null -> priceDialogError = "Fecha inválida. Use dd/mm/aaaa"
                            date.after(endOfToday()) -> priceDialogError = "No se permiten fechas mayores a la fecha actual"
                            price == null || price <= 0 -> priceDialogError = "El precio debe ser mayor a 0"
                            editingPriceEntry != null -> {
                                viewModel.updatePriceHistory(editingPriceEntry!!, price, date)
                                editingPriceEntry = null
                                newPriceValue = ""
                                newPriceDate = ""
                            }
                            else -> {
                                viewModel.addPriceHistory(editingMaterial!!.id, price, date)
                                newPriceValue = ""
                                newPriceDate = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue, contentColor = Color.White)
                ) { Text(if (editingPriceEntry != null) "Actualizar" else "Guardar") }
            },
            dismissButton = {
                Button(
                    onClick = {
                        if (editingPriceEntry != null) {
                            editingPriceEntry = null
                            newPriceValue = ""
                            newPriceDate = ""
                            priceDialogError = null
                        } else {
                            showAddPriceDialog = false
                            newPriceValue = ""
                            newPriceDate = ""
                            priceDialogError = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonRed, contentColor = Color.White)
                ) { Text(if (editingPriceEntry != null) "Cancelar edición" else "Cerrar") }
            }
        )
    }

    deletingPriceEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingPriceEntry = null },
            title = { Text("Eliminar precio") },
            text = {
                Text(
                    "¿Eliminar el precio ${NumberFormatter.money(entry.price)} del " +
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(entry.date) + "?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePriceHistory(entry)
                        if (editingPriceEntry?.id == entry.id) {
                            editingPriceEntry = null
                            newPriceValue = ""
                            newPriceDate = ""
                        }
                        deletingPriceEntry = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonRed, contentColor = Color.White)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                Button(
                    onClick = { deletingPriceEntry = null },
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue, contentColor = Color.White)
                ) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PriceCell(
    text: String,
    modifier: Modifier,
    borderColor: Color,
    bold: Boolean = false,
    lastColumn: Boolean = false
) {
    Box(
        modifier = modifier
            .then(
                if (!lastColumn) Modifier.drawBehind {
                    drawLine(
                        color = borderColor,
                        start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = 1f
                    )
                } else Modifier
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip
        )
    }
}

private fun formatDateInput(input: String): String {
    val digits = input.filter { it.isDigit() }.take(8)
    val sb = StringBuilder()
    for (i in digits.indices) {
        if (i == 2 || i == 4) sb.append('/')
        sb.append(digits[i])
    }
    return sb.toString()
}

private fun endOfToday(): Date {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
    cal.set(java.util.Calendar.MINUTE, 59)
    cal.set(java.util.Calendar.SECOND, 59)
    cal.set(java.util.Calendar.MILLISECOND, 999)
    return cal.time
}

private fun parseDate(text: String): Date? {
    return try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }
        sdf.parse(text)
    } catch (e: Exception) {
        null
    }
}

private fun generatePriceHistoryPdf(
    context: android.content.Context,
    material: Material,
    history: List<PriceHistory>
): java.io.File {
    val pdfDocument = android.graphics.pdf.PdfDocument()
    val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 10f; color = android.graphics.Color.BLACK }
    val headerPaint = android.graphics.Paint().apply {
        isAntiAlias = true; textSize = 10f; color = android.graphics.Color.WHITE
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val titlePaint = android.graphics.Paint().apply {
        isAntiAlias = true; textSize = 15f; color = android.graphics.Color.BLACK
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    val pageWidth = 595
    val pageHeight = 842
    val margin = 40f
    val contentWidth = pageWidth - 2 * margin
    val rowHeight = 20f

    val sorted = history.sortedByDescending { it.date.time }
    val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    var pageNumber = 1
    var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas
    var y = margin

    canvas.drawText("HISTORIAL DE PRECIOS DE COMPRA", margin, y, titlePaint)
    y += 20f
    canvas.drawText("Material: ${material.name}", margin, y, paint)
    y += 14f
    canvas.drawText("Fecha de impresión: ${dateFmt.format(Date())}", margin, y, paint)
    y += 20f

    val colNo = margin
    val colName = margin + contentWidth * 0.10f
    val colDate = margin + contentWidth * 0.55f
    val colPrice = margin + contentWidth * 0.78f

    fun drawHeader() {
        val bg = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#1976D2") }
        canvas.drawRect(margin, y - 12f, margin + contentWidth, y + 4f, bg)
        canvas.drawText("No.", colNo + 2f, y, headerPaint)
        canvas.drawText("Nombre del Material", colName, y, headerPaint)
        canvas.drawText("Fecha", colDate, y, headerPaint)
        canvas.drawText("Precio", colPrice, y, headerPaint)
        y += rowHeight
    }

    drawHeader()

    sorted.forEachIndexed { index, entry ->
        if (y + rowHeight > pageHeight - 50f) {
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = margin
            drawHeader()
        }
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("${index + 1}", colNo + 2f, y, paint)
        canvas.drawText(material.name.take(35), colName, y, paint)
        canvas.drawText(dateFmt.format(entry.date), colDate, y, paint)
        canvas.drawText(NumberFormatter.money(entry.price), colPrice, y, paint)
        y += rowHeight
    }

    if (sorted.isEmpty()) {
        canvas.drawText("No hay precios registrados.", margin, y, paint)
    }

    pdfDocument.finishPage(page)

    val fileName = "HISTORIAL_PRECIOS_${material.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
    val file = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), fileName)
    file.outputStream().use { pdfDocument.writeTo(it) }
    pdfDocument.close()
    return file.canonicalFile
}

@Composable
fun ImportMeasurementsDialog(
    measurements: List<com.drywall.calculator.domain.measure.MaterialMeasure>,
    onDismiss: () -> Unit,
    onImport: (List<com.drywall.calculator.domain.measure.MaterialMeasure>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredMeasurements = remember(searchQuery) {
        if (searchQuery.isBlank()) measurements
        else measurements.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.tags.any { tag -> tag.contains(searchQuery, ignoreCase = true) } 
        }
    }
    
    val selectedItems = remember { mutableStateListOf<com.drywall.calculator.domain.measure.MaterialMeasure>() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar desde Catálogo Medidas") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            if (selectedItems.size == filteredMeasurements.size && filteredMeasurements.isNotEmpty()) selectedItems.clear()
                            else {
                                selectedItems.clear()
                                selectedItems.addAll(filteredMeasurements)
                            }
                        }
                    ) {
                        Text(if (selectedItems.size == filteredMeasurements.size && filteredMeasurements.isNotEmpty()) "Quitar Todos" else "Todos")
                    }
                    Text("${selectedItems.size} seleccionados", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    placeholder = { Text("Buscar en catálogo...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                if (filteredMeasurements.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No se encontraron resultados.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text("Seleccione las medidas a incorporar como materiales:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredMeasurements) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedItems.contains(item)) selectedItems.remove(item)
                                        else selectedItems.add(item)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = selectedItems.contains(item), onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${item.category.displayName} • ${item.standard.displayName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    // Medidas organizadas verticalmente
                                    item.values.forEach { v ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${v.dimension}:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                v.formatted(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
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
            Button(
                onClick = { onImport(selectedItems.toList()) },
                enabled = selectedItems.isNotEmpty()
            ) {
                Text("Incorporar (${selectedItems.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun ImportDefaultsDialog(
    seedMaterials: List<Material>,
    onDismiss: () -> Unit,
    onImport: (List<Material>) -> Unit
) {
    val selectedItems = remember { mutableStateListOf<Material>().apply { addAll(seedMaterials) } }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Materiales Predeterminados") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Seleccione los materiales a incorporar:", style = MaterialTheme.typography.labelMedium)
                    TextButton(
                        onClick = {
                            if (selectedItems.size == seedMaterials.size) selectedItems.clear()
                            else {
                                selectedItems.clear()
                                selectedItems.addAll(seedMaterials)
                            }
                        },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(if (selectedItems.size == seedMaterials.size) "Quitar Todos" else "Todos")
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(seedMaterials) { material ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedItems.contains(material)) selectedItems.remove(material)
                                    else selectedItems.add(material)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedItems.contains(material),
                                onCheckedChange = null // Click handled by Row
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(material.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(selectedItems.toList()) },
                enabled = selectedItems.isNotEmpty()
            ) {
                Text("Incorporar (${selectedItems.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun HeaderActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    isFilled: Boolean = false,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
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
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
fun MaterialCard(material: Material, providerName: String?, onEdit: () -> Unit, onDelete: () -> Unit) {
    val precision = com.drywall.calculator.presentation.theme.LocalDecimalPrecision.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = material.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                
                if (providerName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Default.Store, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(text = providerName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                val locale = LocalConfiguration.current.locales[0]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuggestionChip(
                        onClick = { },
                        label = { Text("${String.format(locale, "%,.${precision}f", material.quantity)} ${material.unitType}") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (material.quantity < 10) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Venta: $${String.format(locale, "%,.${precision}f", material.salePrice)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
    }
}
