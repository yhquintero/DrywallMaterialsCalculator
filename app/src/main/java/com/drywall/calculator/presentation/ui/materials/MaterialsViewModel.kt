package com.drywall.calculator.presentation.ui.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.Material
import com.drywall.calculator.data.local.entity.UnitType
import com.drywall.calculator.data.local.entity.PriceHistory
import com.drywall.calculator.data.repository.MaterialRepository
import com.drywall.calculator.data.repository.PriceHistoryRepository
import com.drywall.calculator.data.repository.ProviderRepository
import com.drywall.calculator.data.repository.UnitTypeRepository
import java.util.Date
import com.drywall.calculator.domain.calculator.ProfitValidator
import com.drywall.calculator.utils.MaterialSeeder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaterialsViewModel @Inject constructor(
    private val repository: MaterialRepository,
    private val providerRepository: ProviderRepository,
    private val unitTypeRepository: UnitTypeRepository,
    private val priceHistoryRepository: PriceHistoryRepository
) : ViewModel() {

    fun getPriceHistory(materialId: String): StateFlow<List<PriceHistory>> =
        priceHistoryRepository.getPriceHistoryForMaterial(materialId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addPriceHistory(materialId: String, price: Double, date: Date) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                priceHistoryRepository.addPrice(materialId, price, date)
            } catch (e: IllegalArgumentException) {
                _errorMessage.emit(e.message ?: "Error de validación")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updatePriceHistory(entry: PriceHistory, newPrice: Double, newDate: Date) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                priceHistoryRepository.updatePrice(entry, newPrice, newDate)
            } catch (e: IllegalArgumentException) {
                _errorMessage.emit(e.message ?: "Error de validación")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePriceHistory(entry: PriceHistory) {
        viewModelScope.launch {
            priceHistoryRepository.deletePrice(entry)
        }
    }

    val materials = repository.getAllMaterials().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val catalogMeasures = com.drywall.calculator.data.provider.MaterialDataProvider.allMeasures

    val providers = providerRepository.getAllProviders().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ... (keep search logic)
    
    fun importFromCatalog(selected: List<com.drywall.calculator.domain.measure.MaterialMeasure>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val existingNames = repository.getAllMaterials().first().map { it.name.lowercase() }.toMutableSet()
                selected.forEach { m ->
                    val nameLower = m.name.lowercase()
                    if (nameLower !in existingNames) {
                        val unitType = when (m.category) {
                            com.drywall.calculator.domain.measure.MaterialCategory.PLASTERBOARD -> if (m.standard.code == "US") "ft²" else "m²"
                            com.drywall.calculator.domain.measure.MaterialCategory.PROFILES -> "unidad"
                            com.drywall.calculator.domain.measure.MaterialCategory.COMPOUNDS -> "cubo"
                            com.drywall.calculator.domain.measure.MaterialCategory.TAPES -> "rollo"
                            else -> "unidad"
                        }
                        
                        val material = Material(
                            name = m.name,
                            quantity = 0.0,
                            purchasePrice = 1.0,
                            salePrice = 1.275,
                            profitPercentage = 27.5,
                            unitType = unitType
                        )
                        repository.insertMaterial(material)
                        existingNames.add(nameLower)
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    val customUnitTypes = unitTypeRepository.getAllUnitTypes().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredMaterials: StateFlow<List<Material>> = combine(materials, _searchQuery) { list, query ->
        val filtered = if (query.isBlank()) list
        else list.filter { it.name.contains(query, ignoreCase = true) }
        filtered.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addMaterial(name: String, purchasePrice: Double, profitPercent: Double, unitType: String, providerId: Int? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val salePrice = ProfitValidator.enforceProfit(purchasePrice, profitPercent)
                val material = Material(
                    name = name,
                    quantity = 0.0,
                    purchasePrice = purchasePrice,
                    salePrice = salePrice,
                    profitPercentage = profitPercent.coerceIn(com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MIN_PROFIT, com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MAX_PROFIT),
                    unitType = unitType,
                    providerId = providerId
                )
                repository.insertMaterial(material)
            } catch (e: IllegalArgumentException) {
                _errorMessage.emit(e.message ?: "Error de validación")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateMaterial(material: Material) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.updateMaterial(material)
            } catch (e: IllegalArgumentException) {
                _errorMessage.emit(e.message ?: "Error de validación")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteMaterial(material: Material) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteMaterial(material)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteAllMaterials() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteAllMaterials()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getSeedMaterials(): List<Material> = MaterialSeeder.getDefaultMaterials()

    fun importMaterials(materials: List<Material>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val existingNames = repository.getAllMaterials().first().map { it.name.lowercase() }.toMutableSet()
                materials.forEach { material ->
                    val nameLower = material.name.lowercase()
                    if (nameLower !in existingNames) {
                        repository.insertMaterial(material)
                        existingNames.add(nameLower)
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addCustomUnit(name: String) {
        viewModelScope.launch {
            unitTypeRepository.insertUnitType(UnitType(name))
        }
    }

    fun deleteCustomUnit(unit: UnitType) {
        viewModelScope.launch {
            unitTypeRepository.deleteUnitType(unit)
        }
    }
}
