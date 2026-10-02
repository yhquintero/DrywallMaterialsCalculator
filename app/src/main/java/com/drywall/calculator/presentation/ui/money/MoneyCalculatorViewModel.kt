package com.drywall.calculator.presentation.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.repository.AppConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.drywall.calculator.data.local.entity.AppConfig

@HiltViewModel
class MoneyCalculatorViewModel @Inject constructor(
    private val repository: AppConfigRepository
) : ViewModel() {

    val config = repository.getConfig().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppConfig()
    )

    fun updateDecimalPrecision(precision: Int) {
        viewModelScope.launch {
            val current = config.value ?: AppConfig()
            repository.saveConfig(current.copy(decimalPrecision = precision))
        }
    }
}
