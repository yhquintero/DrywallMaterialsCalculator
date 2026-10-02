package com.drywall.calculator.presentation.ui.labor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.LaborPrice
import com.drywall.calculator.data.repository.LaborPriceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LaborPriceViewModel @Inject constructor(
    private val repository: LaborPriceRepository
) : ViewModel() {
    val prices = repository.getLaborPrices().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun savePrices(prices: LaborPrice) {
        viewModelScope.launch { repository.saveLaborPrices(prices) }
    }
}
