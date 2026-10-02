package com.drywall.calculator.presentation.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.Material
import com.drywall.calculator.data.repository.InventoryRepository
import com.drywall.calculator.data.repository.MaterialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val materialRepository: MaterialRepository,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val materials = materialRepository.getAllMaterials()
        .combine(_searchQuery) { list, query ->
            if (query.isBlank()) list
            else list.filter { it.name.contains(query, ignoreCase = true) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _lowStockAlerts = MutableStateFlow<List<String>>(emptyList())
    val lowStockAlerts: StateFlow<List<String>> = _lowStockAlerts.asStateFlow()

    init {
        viewModelScope.launch {
            materialRepository.getAllMaterials().collect { list ->
                val alerts = list.filter { it.quantity <= 1.0 }.map { "⚠️ ${it.name}: stock = ${it.quantity}" }
                _lowStockAlerts.value = alerts
            }
        }
    }

    fun addStock(materialId: String, quantity: Double, reason: String, date: Date = Date()) {
        viewModelScope.launch {
            inventoryRepository.addStock(materialId, quantity, reason, date)
        }
    }

    private val _consumeResult = MutableStateFlow<InventoryRepository.ConsumeResult?>(null)
    val consumeResult: StateFlow<InventoryRepository.ConsumeResult?> = _consumeResult.asStateFlow()

    fun consumeStock(materialId: String, quantity: Double, projectName: String, date: Date = Date()) {
        viewModelScope.launch {
            val result = inventoryRepository.consumeStock(materialId, quantity, projectName, date)
            _consumeResult.value = result
        }
    }

    fun clearConsumeResult() {
        _consumeResult.value = null
    }

    private val _deleteResult = MutableStateFlow<InventoryRepository.DeleteResult?>(null)
    val deleteResult: StateFlow<InventoryRepository.DeleteResult?> = _deleteResult.asStateFlow()

    fun deleteTransaction(transactionId: String, materialId: String) {
        viewModelScope.launch {
            val result = inventoryRepository.deleteTransaction(transactionId, materialId)
            _deleteResult.value = result
        }
    }

    fun clearDeleteResult() {
        _deleteResult.value = null
    }

    fun updateTransactionReason(transactionId: String, newReason: String) {
        viewModelScope.launch {
            inventoryRepository.updateTransactionReason(transactionId, newReason)
        }
    }

    private val _updateQuantityResult = MutableStateFlow<InventoryRepository.UpdateQuantityResult?>(null)
    val updateQuantityResult: StateFlow<InventoryRepository.UpdateQuantityResult?> = _updateQuantityResult.asStateFlow()

    fun updateTransactionQuantity(transactionId: String, materialId: String, newQuantity: Double) {
        viewModelScope.launch {
            val result = inventoryRepository.updateTransactionQuantity(transactionId, materialId, newQuantity)
            _updateQuantityResult.value = result
            // No manual refresh needed - Room Flow automatically emits new values
        }
    }

    fun clearUpdateQuantityResult() {
        _updateQuantityResult.value = null
    }

    // Stock Card (Tarjeta de Estiba) data - using flatMapLatest for automatic updates
    private val _selectedMaterialForCard = MutableStateFlow<Material?>(null)
    val selectedMaterialForCard: StateFlow<Material?> = _selectedMaterialForCard.asStateFlow()

    val stockCardTransactions: StateFlow<List<com.drywall.calculator.data.local.entity.InventoryTransaction>> =
        _selectedMaterialForCard.flatMapLatest { material ->
            if (material != null) {
                inventoryRepository.getAllTransactionsForMaterial(material.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _showStockCard = MutableStateFlow(false)
    val showStockCard: StateFlow<Boolean> = _showStockCard.asStateFlow()

    fun openStockCard(material: Material) {
        _selectedMaterialForCard.value = material
        _showStockCard.value = true
    }

    fun closeStockCard() {
        _showStockCard.value = false
        _selectedMaterialForCard.value = null
    }
}
