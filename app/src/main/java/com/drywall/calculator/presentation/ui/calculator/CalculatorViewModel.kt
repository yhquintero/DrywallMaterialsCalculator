package com.drywall.calculator.presentation.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.Keep
import com.drywall.calculator.data.local.entity.Material
import com.drywall.calculator.data.local.entity.Project
import com.drywall.calculator.data.repository.CalculationRepository
import com.drywall.calculator.data.repository.MaterialRepository
import com.drywall.calculator.data.repository.MaterialMeasurementRepository
import com.drywall.calculator.data.repository.InventoryRepository
import com.drywall.calculator.data.repository.ProjectRepository
import com.drywall.calculator.data.repository.PurchaseOrderRepository
import com.drywall.calculator.data.repository.LaborPriceRepository
import com.drywall.calculator.data.repository.TaxRepository
import com.drywall.calculator.data.local.entity.PurchaseOrder
import com.drywall.calculator.data.local.entity.PurchaseOrderItem
import com.drywall.calculator.domain.calculator.MaterialAdvisoryEngine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@Keep
data class ConstructionSegment(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val length: Double,
    val width: Double,
    val repetitions: Int
) {
    val subtotal: Double get() = length * width * repetitions
}

@Keep
data class ConstructionBlock(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val segments: List<ConstructionSegment> = emptyList()
) {
    val totalArea: Double get() = segments.sumOf { it.subtotal }
}

enum class ImportMode { OVERWRITE, MERGE, ADD_NEW }

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val materialRepository: MaterialRepository,
    private val calculationRepository: CalculationRepository,
    private val projectRepository: ProjectRepository,
    private val purchaseOrderRepository: PurchaseOrderRepository,
    private val measurementRepository: MaterialMeasurementRepository,
    private val inventoryRepository: InventoryRepository,
    private val appConfigRepository: com.drywall.calculator.data.repository.AppConfigRepository,
    private val laborPriceRepository: LaborPriceRepository,
    private val taxRepository: TaxRepository
) : ViewModel() {
    private val gson = Gson()
    private val TAG = "CalculatorViewModel"

    val allMaterials = materialRepository.getAllMaterials()
        .map { list -> list.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects = projectRepository.getAllProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val measurements = measurementRepository.getMeasurements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    private val _selectedMeasurement = MutableStateFlow<com.drywall.calculator.data.local.entity.MaterialMeasurement?>(null)
    val selectedMeasurement = _selectedMeasurement.asStateFlow()

    val config = appConfigRepository.getConfig().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val laborPrices = laborPriceRepository.getLaborPrices().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val taxSetting = taxRepository.getTaxSetting().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    
    private val _selectedProject = MutableStateFlow<Project?>(null)
    val selectedProject: StateFlow<Project?> = _selectedProject.asStateFlow()

    private val _constructionType = MutableStateFlow("Techo")
    val constructionType: StateFlow<String> = _constructionType.asStateFlow()

    private val _height = MutableStateFlow("0")
    val height: StateFlow<String> = _height.asStateFlow()

    private val _length = MutableStateFlow("0")
    val length: StateFlow<String> = _length.asStateFlow()

    private val _width = MutableStateFlow("0")
    val width: StateFlow<String> = _width.asStateFlow()

    private val _areaM2 = MutableStateFlow(0.0)
    val areaM2: StateFlow<Double> = _areaM2.asStateFlow()

    private val _requirements = MutableStateFlow<List<CalculationRepository.MaterialRequirement>>(emptyList())
    val requirements: StateFlow<List<CalculationRepository.MaterialRequirement>> = _requirements.asStateFlow()

    private val _purchaseOrder = MutableStateFlow<List<CalculationRepository.MaterialRequirement>>(emptyList())
    val purchaseOrder: StateFlow<List<CalculationRepository.MaterialRequirement>> = _purchaseOrder.asStateFlow()

    private val _advisoryText = MutableStateFlow("")
    val advisoryText: StateFlow<String> = _advisoryText.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage = _uiMessage.asSharedFlow()

    // Multi-part calculation state
    private val _specialBlocks = MutableStateFlow<List<ConstructionBlock>>(emptyList())
    val specialParts = _specialBlocks.asStateFlow()

    private val _specialRate = MutableStateFlow("0.0")
    val specialRate = _specialRate.asStateFlow()

    private val _specialCurrency = MutableStateFlow("USD")
    val specialCurrency = _specialCurrency.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(value = false)
    val hasUnsavedChanges = _hasUnsavedChanges.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _pendingImportBlocks = MutableStateFlow<List<ConstructionBlock>>(emptyList())
    val pendingImportBlocks = _pendingImportBlocks.asStateFlow()

    fun importFromPdf(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val blocks = com.drywall.calculator.utils.PdfImporter.extractBlocksFromPdf(context, uri)
                if (blocks.isNotEmpty()) {
                    if (_specialBlocks.value.isEmpty()) {
                        applyImport(blocks, ImportMode.OVERWRITE)
                    } else {
                        _pendingImportBlocks.value = blocks
                    }
                } else {
                    _uiMessage.emit("No se encontró bloques en el PDF. Para importar datos de bloques, use un Reporte Técnico o una Cotización/Factura generada desde la App.")
                }
            } catch (e: Exception) {
                _uiMessage.emit("Error al importar: ${e.message}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun applyImport(blocks: List<ConstructionBlock>, mode: ImportMode) {
        val merged = when (mode) {
            ImportMode.OVERWRITE -> mutableListOf<ConstructionBlock>()
            ImportMode.MERGE, ImportMode.ADD_NEW -> _specialBlocks.value.toMutableList()
        }
        
        blocks.forEach { newBlock ->
            if (mode == ImportMode.MERGE) {
                val existingBlockIndex = merged.indexOfFirst { it.name.isNotBlank() && it.name.equals(newBlock.name, ignoreCase = true) }
                if (existingBlockIndex != -1) {
                    val existingBlock = merged[existingBlockIndex]
                    val updatedSegments = existingBlock.segments.toMutableList()
                    
                    newBlock.segments.forEach { newSeg ->
                        val existingSegIndex = updatedSegments.indexOfFirst { it.name.equals(newSeg.name, ignoreCase = true) }
                        if (existingSegIndex != -1) {
                            // Update existing segment within the block
                            updatedSegments[existingSegIndex] = newSeg.copy(id = updatedSegments[existingSegIndex].id)
                        } else {
                            // Add as new segment to existing block
                            updatedSegments.add(newSeg.copy(id = UUID.randomUUID().toString()))
                        }
                    }
                    merged[existingBlockIndex] = existingBlock.copy(segments = updatedSegments)
                } else {
                    // Completely new block
                    merged.add(newBlock.copy(id = UUID.randomUUID().toString()))
                }
            } else {
                // For OVERWRITE or ADD_NEW
                merged.add(newBlock.copy(id = UUID.randomUUID().toString()))
            }
        }

        _specialBlocks.value = merged
        _pendingImportBlocks.value = emptyList()
        _hasUnsavedChanges.value = true
        updateSpecialTotal()
        viewModelScope.launch { 
            try {
                val msg = when(mode) {
                    ImportMode.OVERWRITE -> "Datos reemplazados correctamente"
                    ImportMode.MERGE -> "Datos combinados con éxito"
                    ImportMode.ADD_NEW -> "Bloques añadidos por separado"
                }
                _uiMessage.emit(msg)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error emitting message", e)
            }
        }
    }

    fun cancelImport() {
        _pendingImportBlocks.value = emptyList()
    }

    fun addSpecialBlock(block: ConstructionBlock) {
        val current = _specialBlocks.value
        if (current.none { it.id == block.id }) {
            _specialBlocks.value = current + block
            _hasUnsavedChanges.value = true
            updateSpecialTotal()
        }
    }

    fun updateSpecialBlock(block: ConstructionBlock) {
        _specialBlocks.value = _specialBlocks.value.map {
            if (it.id == block.id) block else it
        }
        _hasUnsavedChanges.value = true
        updateSpecialTotal()
    }

    fun removeSpecialBlock(id: String) {
        _specialBlocks.value = _specialBlocks.value.filter { it.id != id }
        _hasUnsavedChanges.value = true
        updateSpecialTotal()
    }

    fun setSpecialRate(r: String) { _specialRate.value = r; _hasUnsavedChanges.value = true }
    fun setSpecialCurrency(c: String) { _specialCurrency.value = c; _hasUnsavedChanges.value = true }

    private fun updateSpecialTotal() {
        if (_constructionType.value == "Especial (Multi-Partes)") {
            _areaM2.value = _specialBlocks.value.sumOf { it.totalArea }
        }
    }

    fun saveAllChanges() {
        val project = _selectedProject.value ?: return
        val json = gson.toJson(_specialBlocks.value)
        val rate = _specialRate.value.toDoubleOrNull() ?: 0.0
        viewModelScope.launch {
            try {
                projectRepository.update(project.copy(
                    specialPartsJson = json,
                    specialRate = rate,
                    specialCurrency = _specialCurrency.value,
                    totalAreaM2 = _areaM2.value
                ))
                _hasUnsavedChanges.value = false
                _uiMessage.emit("Cambios guardados correctamente")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error saving changes", e)
                _uiMessage.emit("Error al guardar: ${e.message}")
            }
        }
    }

    fun setProject(project: Project) {
        _selectedProject.value = project
        _height.value = if (project.height > 0) project.height.toString() else "0"
        _length.value = if (project.length > 0) project.length.toString() else "0"
        _width.value = if (project.width > 0) project.width.toString() else "0"
        _areaM2.value = project.totalAreaM2
        if (project.constructionType.isNotBlank()) {
            _constructionType.value = project.constructionType
        }
        
        _specialRate.value = if (project.specialRate > 0) project.specialRate.toString() else "0.0"
        _specialCurrency.value = project.specialCurrency.ifBlank { "USD" }
        
        if (project.specialPartsJson.isNotBlank()) {
            try {
                val type = object : TypeToken<List<ConstructionBlock>>() {}.type
                val blocks: List<ConstructionBlock> = gson.fromJson(project.specialPartsJson, type)
                // Asegurar que los bloques importados tengan IDs únicos para evitar colisiones si faltan
                _specialBlocks.value = blocks.map { b ->
                    if (b.id.isBlank()) b.copy(id = UUID.randomUUID().toString()) else b
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error parsing special parts", e)
                _specialBlocks.value = emptyList()
            }
        } else {
            _specialBlocks.value = emptyList()
        }
        _hasUnsavedChanges.value = false
    }

    fun setConstructionType(type: String) {
        _constructionType.value = type
    }

    fun setSelectedMeasurement(m: com.drywall.calculator.data.local.entity.MaterialMeasurement?) {
        _selectedMeasurement.value = m
    }

    fun setHeight(v: String) { _height.value = v }
    fun setLength(v: String) { _length.value = v }
    fun setWidth(v: String) { _width.value = v }

    fun addMeasurementsToProject() {
        val project = _selectedProject.value
        if (project == null) {
            viewModelScope.launch { _uiMessage.emit("Seleccione una obra primero") }
            return
        }

        val h = _height.value.toDoubleOrNull()
        val l = _length.value.toDoubleOrNull()
        val w = _width.value.toDoubleOrNull() ?: 0.0

        if ((h == null) || (l == null)) {
            viewModelScope.launch { _uiMessage.emit("Debe introducir Altura y Largo") }
            return
        }

        val calculatedArea = if (_constructionType.value.contains("Tabique", true)) {
            l * h
        } else {
            l * (if (w > 0) w else 1.0)
        }

        _areaM2.value = calculatedArea

        viewModelScope.launch {
            try {
                projectRepository.update(project.copy(
                    height = h,
                    length = l,
                    width = w,
                    totalAreaM2 = calculatedArea,
                    constructionType = _constructionType.value
                ))
                _uiMessage.emit("Medidas guardadas en la obra")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error adding measurements", e)
                _uiMessage.emit("Error al guardar medidas")
            }
        }
    }

    fun calculateMaterials() {
        if (_areaM2.value <= 0) {
            viewModelScope.launch { _uiMessage.emit("El área debe ser mayor a 0") }
            return
        }

        viewModelScope.launch {
            try {
                val catalog = materialRepository.getAllMaterials().first()
                val currentMeasurement = _selectedMeasurement.value ?: measurements.value.firstOrNull()
                val reqs = calculationRepository.calculateRequirements(
                    _constructionType.value, 
                    _areaM2.value, 
                    catalog,
                    currentMeasurement
                )
                _requirements.value = reqs
                
                val toBuy = reqs.asSequence().filter { req ->
                    !req.isInCatalog || req.availableStock < req.totalQuantity
                }.map { req ->
                    val needed = if (!req.isInCatalog) req.totalQuantity else req.totalQuantity - req.availableStock
                    req.copy(totalQuantity = maxOf(0.0, needed))
                }.toList()
                _purchaseOrder.value = toBuy

                _advisoryText.value = MaterialAdvisoryEngine.getAdvisoryText(_constructionType.value, _areaM2.value)
                
                // --- AUTO-SAVE PURCHASE ORDER ---
                if (selectedProject.value != null) {
                    savePurchaseOrder()
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error calculating materials", e)
                _uiMessage.emit("Error al calcular materiales")
            }
        }
    }

    fun assignMaterialsToProject() {
        val project = _selectedProject.value
        if (project == null) {
            viewModelScope.launch { _uiMessage.emit("Seleccione una obra primero") }
            return
        }
        val reqs = _requirements.value
        if (reqs.isEmpty()) {
            viewModelScope.launch { _uiMessage.emit("Primero calcule los materiales") }
            return
        }
        
        viewModelScope.launch {
            try {
                val catalog = materialRepository.getAllMaterials().first()
                val requests = reqs.mapNotNull { req ->
                    val catalogItem = catalog.find { it.name.equals(req.materialName, ignoreCase = true) }
                    if (catalogItem != null && catalogItem.quantity >= req.totalQuantity) {
                        InventoryRepository.StockConsumeRequest(
                            materialId = catalogItem.id,
                            quantity = req.totalQuantity,
                            projectName = project.name
                        )
                    } else null
                }
                val count = inventoryRepository.consumeMultipleStock(requests)
                if (count > 0) {
                    _uiMessage.emit("Se han asignado $count materiales a la obra y actualizado el stock")
                    calculateMaterials()
                } else {
                    _uiMessage.emit("No hay materiales suficientes en stock para asignar")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error assigning materials", e)
                _uiMessage.emit("Error al asignar materiales")
            }
        }
    }

    fun addMaterialToCatalog(materialName: String, initialQuantity: Double = 0.0, unitType: String = "unidad") {
        viewModelScope.launch {
            try {
                val materialId = UUID.randomUUID().toString()
                val newMaterial = Material(
                    id = materialId,
                    name = materialName,
                    quantity = initialQuantity,
                    purchasePrice = 1.0,
                    profitPercentage = 27.5,
                    salePrice = 1.275,
                    unitType = unitType
                )
                materialRepository.insertMaterial(newMaterial)
                
                if (initialQuantity > 0) {
                    inventoryRepository.addStock(materialId, initialQuantity, "Carga inicial desde calculadora")
                }
                
                _uiMessage.emit("$materialName agregado al catálogo")
                calculateMaterials()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error adding material to catalog", e)
                _uiMessage.emit("Error al agregar material")
            }
        }
    }

    fun addStockToMaterial(materialName: String, quantity: Double) {
        viewModelScope.launch {
            try {
                val materials = materialRepository.getAllMaterials().first()
                val material = materials.find { it.name.equals(materialName, ignoreCase = true) }
                if (material != null) {
                    materialRepository.updateStock(material.id, quantity)
                    _uiMessage.emit("Stock de $materialName actualizado")
                    calculateMaterials()
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error adding stock", e)
                _uiMessage.emit("Error al actualizar stock")
            }
        }
    }

    private suspend fun savePurchaseOrder() {
        val project = _selectedProject.value ?: return
        val itemsToBuy = _purchaseOrder.value
        
        try {
            // 1. Buscar si ya existe una orden pendiente para este proyecto
            val existingOrder = purchaseOrderRepository.getPendingOrderForProject(project.id)
            
            // Si el cálculo actual no requiere comprar nada, y existía una orden, la eliminamos
            if (itemsToBuy.isEmpty()) {
                existingOrder?.let { purchaseOrderRepository.deleteOrder(it) }
                _uiMessage.emit("No hay materiales faltantes para ordenar")
                return
            }

            // 2. Usar ID existente o generar uno nuevo
            val orderId = existingOrder?.id ?: UUID.randomUUID().toString()
            
            val rate = _specialRate.value.toDoubleOrNull() ?: 0.0
            val currency = _specialCurrency.value
            val cotizacionData = mapOf(
                "rate" to rate,
                "currency" to currency,
                "constructionType" to _constructionType.value,
                "totalArea" to _areaM2.value
            )
            
            val order = PurchaseOrder(
                id = orderId,
                projectId = project.id,
                projectName = project.name,
                date = System.currentTimeMillis(),
                status = "Pendiente",
                notes = com.google.gson.Gson().toJson(cotizacionData)
            )
            
            val items = itemsToBuy.map {
                PurchaseOrderItem(
                    id = UUID.randomUUID().toString(),
                    purchaseOrderId = orderId,
                    materialName = it.materialName,
                    quantityRequired = it.totalQuantity
                )
            }

            // 3. Si existía, limpiar sus items anteriores primero
            if (existingOrder != null) {
                purchaseOrderRepository.deleteOrder(existingOrder)
            }
            
            purchaseOrderRepository.createOrder(order, items)
            _uiMessage.emit("Orden de compra actualizada: ${items.size} materiales faltantes")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error saving purchase order", e)
            _uiMessage.emit("Error al guardar orden de compra: ${e.message}")
        }
    }
}
