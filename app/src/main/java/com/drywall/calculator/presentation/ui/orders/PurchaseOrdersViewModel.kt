package com.drywall.calculator.presentation.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.PurchaseOrder
import com.drywall.calculator.data.local.entity.PurchaseOrderItem
import com.drywall.calculator.data.repository.PurchaseOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PurchaseOrdersViewModel @Inject constructor(
    private val repository: PurchaseOrderRepository
) : ViewModel() {
    val orders = repository.getAllOrders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getItemsForOrder(orderId: String): Flow<List<PurchaseOrderItem>> = repository.getItemsForOrder(orderId)

    fun deleteOrder(order: PurchaseOrder) {
        viewModelScope.launch {
            repository.deleteOrder(order)
        }
    }
}
